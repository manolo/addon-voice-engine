package org.vaadin.webspeech;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadEvent;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.ElementRequestHandler;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.node.JsonNodeFactory;

import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

/**
 * Per-UI bridge to the client-side module {@code speech.js}.
 * <p>
 * The module is a classpath resource next to this class. It is served through
 * a {@link DownloadHandler} attached to the UI element, and loaded on demand
 * with a dynamic {@code import()} from
 * {@link com.vaadin.flow.dom.Element#executeJs}. No {@code @JsModule} or static
 * resource serving is involved, so applications using the add-on need no
 * custom frontend bundle and it works in any deployment. Calls and events go
 * through the UI element: the
 * module dispatches {@code web-speech-*} events on it, and this class routes
 * them by id to the registered handlers.
 */
final class SpeechClient implements Serializable {

    static final String MODULE_ATTRIBUTE = "data-web-speech-module";
    static final String UPLOAD_ATTRIBUTE = "data-web-speech-upload";
    /** Upper limit for an uploaded recording, about 20 minutes of Opus audio. */
    static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
    static final String SYNTHESIS_EVENT = "web-speech-synthesis";
    static final String RECOGNITION_EVENT = "web-speech-recognition";

    private final UI ui;
    private final Map<String, SerializableConsumer<JsonNode>> synthesisHandlers = new HashMap<>();
    private final Map<String, SerializableConsumer<JsonNode>> recognitionHandlers = new HashMap<>();
    private Registration synthesisListener;
    private Registration recognitionListener;

    private SpeechSupport synthesisSupport = SpeechSupport.UNKNOWN;
    private SpeechSupport recognitionSupport = SpeechSupport.UNKNOWN;
    private final List<Runnable> supportCallbacks = new ArrayList<>();

    /**
     * Server fallback transcribers by session id. Read by upload requests,
     * which run without the session lock.
     */
    private final Map<String, SpeechToText> transcribers = new ConcurrentHashMap<>();
    private boolean uploadRegistered;

    /** Synthesis state shared by all callers in this UI, see {@link SpeechSynthesis}. */
    final SynthesisTracker synthesis = new SynthesisTracker(this);

    private SpeechClient(UI ui) {
        this.ui = ui;
        // No session in plain unit tests (new UI()), where nothing is sent to a browser anyway
        if (ui.getSession() != null) {
            ui.getElement().setAttribute(MODULE_ATTRIBUTE, new ModuleHandler());
        }
        call("m.support()").then(JsonNode.class,
                json -> setSupport(SpeechSupport.of(json.path("synthesis").asBoolean(false)),
                        SpeechSupport.of(json.path("recognition").asBoolean(false))),
                error -> setSupport(SpeechSupport.UNSUPPORTED, SpeechSupport.UNSUPPORTED));
    }

    void setSupport(SpeechSupport synthesis, SpeechSupport recognition) {
        synthesisSupport = synthesis;
        recognitionSupport = recognition;
        List<Runnable> callbacks = new ArrayList<>(supportCallbacks);
        supportCallbacks.clear();
        callbacks.forEach(Runnable::run);
    }

    /** Runs the callback once support is known, immediately if it already is. */
    void whenSupportKnown(Runnable callback) {
        if (recognitionSupport != SpeechSupport.UNKNOWN) {
            callback.run();
        } else {
            supportCallbacks.add(callback);
        }
    }

    static SpeechClient get(UI ui) {
        Objects.requireNonNull(ui, "UI must not be null");
        SpeechClient client = ComponentUtil.getData(ui, SpeechClient.class);
        if (client == null) {
            client = new SpeechClient(ui);
            ComponentUtil.setData(ui, SpeechClient.class, client);
        }
        return client;
    }

    static UI currentUi() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            throw new IllegalStateException(
                    "No current UI available. Call from a UI thread or pass the UI explicitly.");
        }
        return ui;
    }

    UI ui() {
        return ui;
    }

    SpeechSupport synthesisSupport() {
        return synthesisSupport;
    }

    SpeechSupport recognitionSupport() {
        return recognitionSupport;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Calls the module. In {@code expression}, {@code m} is the imported module,
     * {@code this} is the UI element and {@code $0...} are the given arguments.
     */
    PendingJavaScriptResult call(String expression, Object... args) {
        return ui.getElement().executeJs("return import(new URL(this.getAttribute('" + MODULE_ATTRIBUTE
                + "'), document.baseURI).href).then(m => " + expression + ")", args);
    }

    /**
     * Serves speech.js. Allowed while a modal dialog makes the UI inert.
     */
    private static final class ModuleHandler implements DownloadHandler {
        @Override
        public void handleDownloadRequest(DownloadEvent event) throws IOException {
            event.setContentType("text/javascript");
            try (InputStream in = SpeechClient.class.getResourceAsStream("speech.js")) {
                if (in == null) {
                    throw new IOException("speech.js not found on the classpath");
                }
                in.transferTo(event.getOutputStream());
            }
        }

        @Override
        public String getUrlPostfix() {
            return "speech.js";
        }

        @Override
        public boolean isAllowInert() {
            return true;
        }
    }

    /**
     * Registers the transcriber of a session's server fallback.
     */
    Registration registerTranscriber(String id, SpeechToText transcriber) {
        if (!uploadRegistered && ui.getSession() != null) {
            uploadRegistered = true;
            ui.getElement().setAttribute(UPLOAD_ATTRIBUTE, new TranscriptionHandler(transcribers));
        }
        transcribers.put(id, transcriber);
        return () -> transcribers.remove(id, transcriber);
    }

    /**
     * Receives a recording from the browser, transcribes it and responds with
     * {@code {"transcript": "..."}}. Runs without the session lock, so a slow
     * transcription service does not block the UI.
     */
    private record TranscriptionHandler(Map<String, SpeechToText> transcribers) implements ElementRequestHandler {
        @Override
        public void handleRequest(VaadinRequest request, VaadinResponse response, VaadinSession session,
                Element owner) throws IOException {
            SpeechToText transcriber = transcribers.get(String.valueOf(request.getParameter("session")));
            if (!"POST".equals(request.getMethod()) || transcriber == null) {
                response.sendError(400, "No such recognition session");
                return;
            }
            if (request.getContentLengthLong() > MAX_UPLOAD_BYTES) {
                response.sendError(413, "Recording too large");
                return;
            }
            String contentType = request.getContentType() != null ? request.getContentType()
                    : "application/octet-stream";
            String transcript;
            try (InputStream audio = new LimitedInputStream(request.getInputStream(), MAX_UPLOAD_BYTES)) {
                transcript = transcriber.transcribe(audio, contentType);
            } catch (IOException | RuntimeException e) {
                LoggerFactory.getLogger(SpeechClient.class).warn("Transcription failed", e);
                response.sendError(500, "Transcription failed");
                return;
            }
            response.setContentType("application/json");
            response.getOutputStream().write(JsonNodeFactory.instance.objectNode()
                    .put("transcript", transcript == null ? "" : transcript).toString()
                    .getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getUrlPostfix() {
            return "transcribe";
        }

        @Override
        public boolean isAllowInert() {
            return true;
        }
    }

    /** Fails reading once more than the limit has been read. */
    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws IOException {
            remaining -= n;
            if (remaining < 0) {
                throw new IOException("Recording exceeds " + MAX_UPLOAD_BYTES + " bytes");
            }
        }
    }

    Registration onSynthesisEvent(String id, SerializableConsumer<JsonNode> handler) {
        if (synthesisListener == null) {
            synthesisListener = listen(SYNTHESIS_EVENT, synthesisHandlers);
        }
        synthesisHandlers.put(id, handler);
        return () -> synthesisHandlers.remove(id);
    }

    Registration onRecognitionEvent(String id, SerializableConsumer<JsonNode> handler) {
        if (recognitionListener == null) {
            recognitionListener = listen(RECOGNITION_EVENT, recognitionHandlers);
        }
        recognitionHandlers.put(id, handler);
        return () -> recognitionHandlers.remove(id);
    }

    private Registration listen(String eventName, Map<String, SerializableConsumer<JsonNode>> handlers) {
        return ui.getElement().addEventListener(eventName, e -> {
            JsonNode detail = e.getEventData().get("event.detail");
            if (detail == null) {
                return;
            }
            SerializableConsumer<JsonNode> handler = handlers.get(detail.path("id").asString(""));
            if (handler != null) {
                handler.accept(detail);
            }
        }).addEventData("event.detail")
                // Defensive: modals do not make the UI element inert today (see
                // DemoViewsIT), but speech events must get through regardless
                .allowInert();
    }
}
