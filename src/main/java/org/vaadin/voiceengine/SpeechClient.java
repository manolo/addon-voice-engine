package org.vaadin.voiceengine;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.server.streams.DownloadEvent;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;

/**
 * Per-UI bridge to the client-side module {@code speech.js}.
 * <p>
 * The module is a classpath resource next to this class. It is served through
 * a {@link DownloadHandler} attached to the UI element, and loaded on demand
 * with a dynamic {@code import()} from
 * {@link com.vaadin.flow.dom.Element#executeJs}. No {@code @JsModule} or static
 * resource serving is involved, so applications using the add-on need no
 * custom frontend bundle and it works in any deployment. Calls and events go through the UI element: the
 * module dispatches {@code voice-engine-*} events on it, and this class routes
 * them by id to the registered handlers.
 */
final class SpeechClient implements Serializable {

    static final String MODULE_ATTRIBUTE = "data-voice-engine-module";
    static final String SYNTHESIS_EVENT = "voice-engine-synthesis";
    static final String RECOGNITION_EVENT = "voice-engine-recognition";

    private final UI ui;
    private final Map<String, SerializableConsumer<JsonNode>> synthesisHandlers = new HashMap<>();
    private final Map<String, SerializableConsumer<JsonNode>> recognitionHandlers = new HashMap<>();
    private Registration synthesisListener;
    private Registration recognitionListener;

    private SpeechSupport synthesisSupport = SpeechSupport.UNKNOWN;
    private SpeechSupport recognitionSupport = SpeechSupport.UNKNOWN;

    /** Synthesis state shared by all callers in this UI, see {@link SpeechSynthesis}. */
    final SynthesisTracker synthesis = new SynthesisTracker(this);

    private SpeechClient(UI ui) {
        this.ui = ui;
        ui.getElement().setAttribute(MODULE_ATTRIBUTE, new ModuleHandler());
        call("m.support()").then(JsonNode.class, json -> {
            synthesisSupport = SpeechSupport.of(json.path("synthesis").asBoolean(false));
            recognitionSupport = SpeechSupport.of(json.path("recognition").asBoolean(false));
        }, error -> {
            synthesisSupport = SpeechSupport.UNSUPPORTED;
            recognitionSupport = SpeechSupport.UNSUPPORTED;
        });
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
