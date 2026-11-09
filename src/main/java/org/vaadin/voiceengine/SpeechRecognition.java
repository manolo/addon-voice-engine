package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;

/**
 * Speech-to-text using the browser's
 * <a href="https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition">SpeechRecognition</a>
 * API.
 * <p>
 * A recognition session is bound to an owner component: it can be started
 * while the owner is attached, and it is aborted automatically when the owner
 * detaches. The same instance can be started again after it has ended.
 *
 * <pre>{@code
 * SpeechRecognition recognition = SpeechRecognition.create(this,
 *         RecognitionOptions.builder().lang(Locale.of("fi", "FI")).continuous(true).build());
 * recognition.addEndListener(transcript -> textArea.setValue(transcript));
 * recognition.addErrorListener(error -> Notification.show("Recognition failed: " + error.code()));
 * startButton.addClickListener(e -> recognition.start());
 * stopButton.addClickListener(e -> recognition.stop());
 *
 * // or for a single phrase:
 * SpeechRecognition.listenOnce(this, text -> textField.setValue(text), error -> {});
 * }</pre>
 * <p>
 * Speech recognition is not available in all browsers (e.g. Firefox), see
 * {@link #getSupport()}. The browser asks the user for microphone permission on
 * first use.
 */
public class SpeechRecognition implements Serializable {

    private final Component owner;
    private final String id = SpeechClient.newId();
    private final Registration detachRegistration;
    private RecognitionOptions options;

    private final TreeMap<Integer, String> finalResults = new TreeMap<>();
    private final TreeMap<Integer, String> interimResults = new TreeMap<>();

    private final List<SerializableConsumer<RecognitionResult>> resultListeners = new ArrayList<>();
    private final List<SerializableConsumer<RecognitionError>> errorListeners = new ArrayList<>();
    private final List<SerializableConsumer<String>> endListeners = new ArrayList<>();
    private final List<SerializableConsumer<Boolean>> activeChangeListeners = new ArrayList<>();

    private SpeechClient client;
    private Registration eventRegistration;
    private boolean active;

    /**
     * Creates a recognition session with {@link RecognitionOptions#DEFAULT}.
     *
     * @param owner the component whose lifecycle the session follows
     * @return a new, not yet started session
     */
    public static SpeechRecognition create(Component owner) {
        return create(owner, RecognitionOptions.DEFAULT);
    }

    /**
     * Creates a recognition session.
     *
     * @param owner   the component whose lifecycle the session follows
     * @param options the recognition options
     * @return a new, not yet started session
     */
    public static SpeechRecognition create(Component owner, RecognitionOptions options) {
        return new SpeechRecognition(owner, options);
    }

    /**
     * Recognizes a single phrase in the browser's language.
     *
     * @param owner   an attached component, the recognition is aborted if it
     *                detaches
     * @param onText  receives the recognized text, possibly empty if nothing
     *                was recognized
     * @param onError called instead of {@code onText} if recognition fails
     */
    public static void listenOnce(Component owner, SerializableConsumer<String> onText,
            SerializableConsumer<RecognitionError> onError) {
        listenOnce(owner, RecognitionOptions.DEFAULT, onText, onError);
    }

    /**
     * Recognizes a single phrase.
     *
     * @param owner   an attached component, the recognition is aborted if it
     *                detaches
     * @param options the recognition options, {@code continuous} is ignored
     * @param onText  receives the recognized text, possibly empty if nothing
     *                was recognized
     * @param onError called instead of {@code onText} if recognition fails
     */
    public static void listenOnce(Component owner, RecognitionOptions options, SerializableConsumer<String> onText,
            SerializableConsumer<RecognitionError> onError) {
        Objects.requireNonNull(onText, "onText must not be null");
        Objects.requireNonNull(onError, "onError must not be null");
        SpeechRecognition recognition = create(owner, options.toBuilder().continuous(false).build());
        RecognitionError[] failure = new RecognitionError[1];
        recognition.addErrorListener(error -> {
            if (failure[0] == null) {
                failure[0] = error;
            }
        });
        recognition.addEndListener(transcript -> {
            recognition.detachRegistration.remove();
            if (failure[0] != null && failure[0].code() != RecognitionErrorCode.NO_SPEECH) {
                onError.accept(failure[0]);
            } else {
                onText.accept(transcript);
            }
        });
        recognition.start();
    }

    /**
     * @return whether the current UI's browser supports speech recognition,
     *         {@link SpeechSupport#UNKNOWN} until the browser has responded
     */
    public static SpeechSupport getSupport() {
        return getSupport(SpeechClient.currentUi());
    }

    /**
     * @param ui the UI
     * @return whether the UI's browser supports speech recognition,
     *         {@link SpeechSupport#UNKNOWN} until the browser has responded
     */
    public static SpeechSupport getSupport(UI ui) {
        return SpeechClient.get(ui).recognitionSupport();
    }

    protected SpeechRecognition(Component owner, RecognitionOptions options) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        detachRegistration = owner.addDetachListener(e -> {
            if (active) {
                client.call("m.abortRecognition($0)", id);
                finish();
            }
        });
        // Start support detection early, so getSupport() is likely known
        // by the time the user interacts with the UI
        owner.getUI().ifPresent(SpeechClient::get);
    }

    /**
     * Starts listening. Does nothing if the session is already active.
     *
     * @throws IllegalStateException if the owner is not attached to a UI
     */
    public void start() {
        if (active) {
            return;
        }
        UI ui = owner.getUI().orElseThrow(() -> new IllegalStateException(
                "The owner component must be attached before starting speech recognition"));
        client = SpeechClient.get(ui);
        finalResults.clear();
        interimResults.clear();
        eventRegistration = client.onRecognitionEvent(id, this::handle);
        setActive(true);
        client.call("m.startRecognition(this, $0, $1)", id, options.toJson()).then(ok -> {
        }, failure -> {
            fireError(new RecognitionError(RecognitionErrorCode.START_FAILED, "start-failed", failure));
            finish();
        });
    }

    /**
     * Stops listening. The browser still delivers the results for audio
     * captured so far, then the session ends.
     */
    public void stop() {
        if (active) {
            client.call("m.stopRecognition($0)", id);
        }
    }

    /**
     * Stops listening and discards audio that has not been recognized yet. An
     * error with {@link RecognitionErrorCode#ABORTED} is reported before the
     * session ends.
     */
    public void abort() {
        if (active) {
            client.call("m.abortRecognition($0)", id);
        }
    }

    /**
     * @return {@code true} from {@link #start()} until the session has ended
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @return the final text recognized during the current or latest session
     */
    public String getTranscript() {
        return join(finalResults.values());
    }

    /**
     * @return text that has been recognized but may still change, only when
     *         interim results are enabled
     */
    public String getInterimTranscript() {
        return join(interimResults.values());
    }

    /**
     * @return the options
     */
    public RecognitionOptions getOptions() {
        return options;
    }

    /**
     * Sets the options. Changes take effect on the next {@link #start()}.
     *
     * @param options the recognition options
     */
    public void setOptions(RecognitionOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    /**
     * Listens to recognized phrases as they arrive.
     *
     * @param listener receives each result
     * @return a registration for removing the listener
     */
    public Registration addResultListener(SerializableConsumer<RecognitionResult> listener) {
        return add(resultListeners, listener);
    }

    /**
     * Listens to recognition errors. A session that fails always ends too.
     *
     * @param listener receives each error
     * @return a registration for removing the listener
     */
    public Registration addErrorListener(SerializableConsumer<RecognitionError> listener) {
        return add(errorListeners, listener);
    }

    /**
     * Listens to the end of a session, whether stopped, ended on its own or
     * failed.
     *
     * @param listener receives the final transcript, see
     *                 {@link #getTranscript()}
     * @return a registration for removing the listener
     */
    public Registration addEndListener(SerializableConsumer<String> listener) {
        return add(endListeners, listener);
    }

    /**
     * Listens to changes of {@link #isActive()}. Handy for updating buttons.
     *
     * @param listener receives the new value
     * @return a registration for removing the listener
     */
    public Registration addActiveChangeListener(SerializableConsumer<Boolean> listener) {
        return add(activeChangeListeners, listener);
    }

    private void handle(JsonNode json) {
        switch (json.path("type").asString("")) {
            case "result" -> {
                List<RecognitionResult> results = new ArrayList<>();
                for (JsonNode r : json.path("results")) {
                    List<RecognitionAlternative> alternatives = new ArrayList<>();
                    for (JsonNode a : r.path("alternatives")) {
                        alternatives.add(new RecognitionAlternative(a.path("transcript").asString(""),
                                a.path("confidence").asDouble(0)));
                    }
                    results.add(new RecognitionResult(r.path("index").asInt(), r.path("isFinal").asBoolean(),
                            List.copyOf(alternatives)));
                }
                results.forEach(this::onResult);
            }
            case "error" -> {
                String code = json.path("error").asString("");
                fireError(new RecognitionError(RecognitionErrorCode.fromJs(code), code,
                        json.path("message").asString("")));
            }
            case "end" -> finish();
            default -> {
                // "start" and unknown events need no handling
            }
        }
    }

    void onResult(RecognitionResult result) {
        if (result.isFinal()) {
            interimResults.remove(result.index());
            finalResults.put(result.index(), result.transcript());
        } else {
            interimResults.put(result.index(), result.transcript());
        }
        new ArrayList<>(resultListeners).forEach(l -> l.accept(result));
    }

    private void fireError(RecognitionError error) {
        new ArrayList<>(errorListeners).forEach(l -> l.accept(error));
    }

    private void finish() {
        if (!active) {
            return;
        }
        eventRegistration.remove();
        eventRegistration = null;
        interimResults.clear();
        setActive(false);
        String transcript = getTranscript();
        new ArrayList<>(endListeners).forEach(l -> l.accept(transcript));
    }

    private void setActive(boolean active) {
        this.active = active;
        new ArrayList<>(activeChangeListeners).forEach(l -> l.accept(active));
    }

    private static String join(Collection<String> parts) {
        return parts.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
    }

    private static <T> Registration add(List<T> list, T listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        list.add(listener);
        return () -> list.remove(listener);
    }
}
