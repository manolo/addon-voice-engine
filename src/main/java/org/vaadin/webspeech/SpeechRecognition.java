package org.vaadin.webspeech;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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
 * For the snappiest result, let the browser start and stop the session
 * directly from a button with {@link #bindTrigger(Component, TriggerMode)}
 * (no server round trip, push-to-talk, Escape to abort), and write the text
 * into a field in the browser with {@link #setLiveTarget(HasValue)}.
 * <p>
 * Speech recognition is not available in all browsers (e.g. Firefox for Android), see
 * {@link #getSupport()}. A {@link #setServerFallback(SpeechToText) server
 * fallback} can cover those. The browser asks the user for microphone
 * permission on first use.
 */
public class SpeechRecognition implements Serializable {

    private final Component owner;
    private final String id = SpeechClient.newId();
    private final Registration attachRegistration;
    private final Registration detachRegistration;
    private RecognitionOptions options;

    private final TreeMap<Integer, String> finalResults = new TreeMap<>();
    private final TreeMap<Integer, String> interimResults = new TreeMap<>();

    private final List<SerializableConsumer<RecognitionResult>> resultListeners = new ArrayList<>();
    private final List<SerializableConsumer<RecognitionError>> errorListeners = new ArrayList<>();
    private final List<SerializableConsumer<String>> endListeners = new ArrayList<>();
    private final List<SerializableConsumer<Boolean>> activeChangeListeners = new ArrayList<>();
    private final List<SerializableConsumer<RecognitionPhase>> phaseListeners = new ArrayList<>();

    private SpeechClient client;
    private Registration eventRegistration;
    private Registration transcriberRegistration;
    private boolean active;
    private RecognitionErrorCode sessionError;

    private Component trigger;
    private TriggerMode triggerMode;
    private Registration triggerBinding;
    private Registration keyBinding;

    private HasValue<?, String> liveTarget;
    private boolean liveAppend = true;
    private String liveBase = "";
    private boolean interimResultsToServer = true;
    private SpeechToText serverFallback;

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
            recognition.release();
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

    /**
     * Calls the callback once the browser has reported whether it supports
     * speech recognition, immediately if already known. Handy for hiding a
     * microphone button in browsers without recognition.
     *
     * @param ui       the UI
     * @param callback receives {@link SpeechSupport#SUPPORTED} or
     *                 {@link SpeechSupport#UNSUPPORTED}
     */
    public static void whenSupportKnown(UI ui, SerializableConsumer<SpeechSupport> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        SpeechClient client = SpeechClient.get(ui);
        client.whenSupportKnown(() -> callback.accept(client.recognitionSupport()));
    }

    /**
     * Checks whether the language can be recognized on the device in the
     * current UI's browser, see {@link RecognitionEngine}.
     * <p>
     * Call this only in response to a user action, not when the page loads:
     * in Chromium builds without the on-device service (e.g. the Chromium used
     * by Playwright, reportedly also some Electron and Brave builds), the
     * browser's check crashes the tab. To keep end-to-end tests alive, the
     * check is skipped in browsers controlled by automation
     * ({@code navigator.webdriver}), and the answer is
     * {@link OnDeviceAvailability#UNAVAILABLE}, as it is for a check that does
     * not answer within 1.5 seconds.
     *
     * @param lang     the language, or {@code null} for the browser's language
     * @param callback receives the availability
     */
    public static void checkOnDeviceAvailability(Locale lang, SerializableConsumer<OnDeviceAvailability> callback) {
        checkOnDeviceAvailability(SpeechClient.currentUi(), lang, callback);
    }

    /**
     * Checks whether the language can be recognized on the device in the
     * UI's browser, see {@link #checkOnDeviceAvailability(Locale, SerializableConsumer)}.
     *
     * @param ui       the UI
     * @param lang     the language, or {@code null} for the browser's language
     * @param callback receives the availability
     */
    public static void checkOnDeviceAvailability(UI ui, Locale lang,
            SerializableConsumer<OnDeviceAvailability> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        SpeechClient.get(ui).call("m.onDeviceAvailability($0)", lang == null ? null : lang.toLanguageTag())
                .then(String.class, value -> callback.accept(OnDeviceAvailability.fromJs(value)),
                        error -> callback.accept(OnDeviceAvailability.UNAVAILABLE));
    }

    /**
     * Downloads the language pack for on-device recognition in the current
     * UI's browser. Browsers only start a download shortly after a user
     * action, so call this from e.g. a click listener.
     *
     * @param lang     the language, or {@code null} for the browser's language
     * @param callback receives {@code true} when the language is installed,
     *                 {@code false} if installing failed or is not supported
     */
    public static void installOnDevice(Locale lang, SerializableConsumer<Boolean> callback) {
        installOnDevice(SpeechClient.currentUi(), lang, callback);
    }

    /**
     * Downloads the language pack for on-device recognition, see
     * {@link #installOnDevice(Locale, SerializableConsumer)}.
     *
     * @param ui       the UI
     * @param lang     the language, or {@code null} for the browser's language
     * @param callback receives {@code true} when the language is installed
     */
    public static void installOnDevice(UI ui, Locale lang, SerializableConsumer<Boolean> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        SpeechClient.get(ui).call("m.installOnDevice($0)", lang == null ? null : lang.toLanguageTag())
                .then(Boolean.class, callback, error -> callback.accept(false));
    }

    protected SpeechRecognition(Component owner, RecognitionOptions options) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        attachRegistration = owner.addAttachListener(e -> {
            if (triggerBinding != null && trigger != owner) {
                trigger.getUI().ifPresent(this::bindTriggerInBrowser);
            }
        });
        detachRegistration = owner.addDetachListener(e -> disconnect());
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
        connect(ui);
        begin();
        client.call("m.startRecognition(this, $0, $1, $2)", id, config(), liveTargetElement()).then(ok -> {
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
     * session ends. A {@link #setLiveTarget(HasValue) live target} gets its
     * original value back.
     */
    public void abort() {
        if (active) {
            client.call("m.abortRecognition($0)", id);
        }
    }

    /**
     * Lets the browser start and stop the session directly from the trigger
     * component's events, without a round trip to the server. Listening
     * starts within the user's gesture, so no words are lost and browsers that
     * require a user activation are satisfied. Escape aborts the session. The
     * server is informed as usual: {@link #isActive()} and the listeners work
     * the same as with {@link #start()}.
     * <p>
     * Only one trigger can be bound at a time; binding another one replaces it.
     * The trigger should not also call {@link #start()} or {@link #stop()}.
     *
     * @param trigger the component, typically a button
     * @param mode    toggle on click, or push-to-talk
     * @return a registration for unbinding the trigger
     */
    public Registration bindTrigger(Component trigger, TriggerMode mode) {
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        unbindTrigger();
        this.trigger = trigger;
        this.triggerMode = mode;
        triggerBinding = new OwnerBinding(trigger, ui -> {
            bindTriggerInBrowser(ui);
            return () -> {
                if (client != null) {
                    client.call("m.unbindTrigger($0)", id);
                }
            };
        });
        Registration binding = triggerBinding;
        return () -> {
            if (triggerBinding == binding) {
                unbindTrigger();
            }
        };
    }

    /**
     * Lets a key start and stop the session anywhere on the page, e.g. F8 for
     * push-to-talk. Like {@link #bindTrigger(Component, TriggerMode)}, the
     * browser starts listening directly in the key event, and Escape aborts.
     * Active while the session's owner is attached.
     * <p>
     * The key's default action is prevented, so use a key that is not needed
     * for typing, such as a function key. Only one key can be bound at a time;
     * binding another one replaces it. It can be used together with a bound
     * trigger component.
     *
     * <pre>{@code
     * dictationButton.getRecognition().bindKey(Key.F8, TriggerMode.HOLD);
     * }</pre>
     *
     * @param key  the key
     * @param mode {@link TriggerMode#TOGGLE} to start and stop with presses,
     *             {@link TriggerMode#HOLD} to listen while the key is held
     *             down
     * @return a registration for unbinding the key
     */
    public Registration bindKey(Key key, TriggerMode mode) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        unbindKey();
        ArrayNode keys = JsonNodeFactory.instance.arrayNode();
        key.getKeys().forEach(keys::add);
        keyBinding = new OwnerBinding(owner, ui -> {
            connect(ui);
            client.call("(m.configure(this, $0, $1, $2), m.bindKey(this, $0, $3, $4))", id, config(),
                    liveTargetElement(), keys, mode.jsName());
            return () -> {
                if (client != null) {
                    client.call("m.unbindKey($0)", id);
                }
            };
        });
        Registration binding = keyBinding;
        return () -> {
            if (keyBinding == binding) {
                unbindKey();
            }
        };
    }

    /**
     * Makes clicks on the component wait for the session to finish: while
     * the session is active, a click first stops it, and the click is
     * replayed in the browser once the session has ended. The click
     * listeners on the server then see the final text in the
     * {@link #setLiveTarget(HasValue) live target}, which otherwise reaches
     * the server only at the end of the session. Typical for a search or
     * submit button next to a field with a microphone.
     * <p>
     * Enter in a single-line live target works the same way by default.
     * Active while the component is attached.
     *
     * @param component the component whose clicks act on the recognized
     *                  text, typically a button
     * @return a registration for removing the behaviour
     */
    public Registration finishBefore(Component component) {
        Objects.requireNonNull(component, "component must not be null");
        String key = id + ":" + SpeechClient.newId();
        return new OwnerBinding(component, ui -> {
            connect(ui);
            client.call("m.finishBefore($0, $1, $2)", id, component.getElement(), key);
            return () -> {
                if (client != null) {
                    client.call("m.unfinishBefore($0)", key);
                }
            };
        });
    }

    private void unbindKey() {
        if (keyBinding != null) {
            keyBinding.remove();
            keyBinding = null;
        }
    }

    private void unbindTrigger() {
        if (triggerBinding != null) {
            triggerBinding.remove();
            triggerBinding = null;
            trigger = null;
            triggerMode = null;
        }
    }

    private void bindTriggerInBrowser(UI ui) {
        if (owner.getUI().isEmpty()) {
            // Bound again when the owner attaches
            return;
        }
        connect(ui);
        client.call("(m.configure(this, $0, $1, $2), m.bindTrigger(this, $3, $0, $4))", id, config(),
                liveTargetElement(), trigger.getElement(), triggerMode.jsName());
    }

    /**
     * Writes the recognized text into the field in the browser while the user
     * speaks, without a round trip per word. When the session ends, the final
     * text is set to the field on the server too. When the session is aborted
     * (e.g. with Escape) or recognizes nothing, the field gets its original
     * value back, also when the text replaces the value.
     * <p>
     * The server gets the text only when the session ends, so server code
     * reading the field during the session sees the old value. Enter in a
     * single-line field therefore finishes the session before the field's own
     * listeners get it; use {@link #finishBefore(Component)} for buttons that
     * act on the text.
     * <p>
     * Combine with {@link #setInterimResultsToServer(boolean)
     * setInterimResultsToServer(false)} to send only final results to the
     * server.
     *
     * @param field a field component with a string value, e.g. a
     *              {@code TextField} or {@code TextArea}, or {@code null} to
     *              remove the live target
     * @param append {@code true} to add the text after the field's value,
     *               {@code false} to replace the value
     * @throws IllegalArgumentException if the field is not a component
     */
    public void setLiveTarget(HasValue<?, String> field, boolean append) {
        if (field != null && !(field instanceof Component)) {
            throw new IllegalArgumentException("The live target must be a component");
        }
        liveTarget = field;
        liveAppend = append;
        pushConfig();
    }

    /**
     * Same as {@link #setLiveTarget(HasValue, boolean) setLiveTarget(field, true)}:
     * the text is added after the field's current value.
     *
     * @param field a field component with a string value, or {@code null}
     */
    public void setLiveTarget(HasValue<?, String> field) {
        setLiveTarget(field, true);
    }

    /**
     * @param interimResultsToServer {@code true} (default) to send every
     *                               result to the server, {@code false} to
     *                               send only final ones. Interim results
     *                               change with every word, so with a
     *                               {@link #setLiveTarget(HasValue) live
     *                               target} they are often not needed on the
     *                               server.
     */
    public void setInterimResultsToServer(boolean interimResultsToServer) {
        this.interimResultsToServer = interimResultsToServer;
        pushConfig();
    }

    /**
     * Records the audio and transcribes it on the server when the browser
     * cannot recognize speech: when it has no speech recognition (e.g. Firefox for Android), or
     * when its recognition service fails before any result (e.g. a Chromium
     * build without Google's service). The text arrives as a single final
     * result when the session is stopped; a recording is never stopped
     * automatically.
     *
     * @param transcriber transcribes the recording, or {@code null} to remove
     *                    the fallback; a session that is
     *                    already recording still uses it
     */
    public void setServerFallback(SpeechToText transcriber) {
        serverFallback = transcriber;
        if (transcriberRegistration != null) {
            transcriberRegistration.remove();
            transcriberRegistration = null;
        }
        if (client != null && transcriber != null && eventRegistration != null) {
            transcriberRegistration = client.registerTranscriber(id, transcriber);
        }
        pushConfig();
    }

    /**
     * @return the server fallback, or {@code null} if none is set
     */
    public SpeechToText getServerFallback() {
        return serverFallback;
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
     *         interim results are enabled and sent to the server
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
     * Sets the options. Changes take effect on the next start.
     *
     * @param options the recognition options
     */
    public void setOptions(RecognitionOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        pushConfig();
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
     * failed. A {@link #setLiveTarget(HasValue) live target} already has its
     * final value on the server when the listeners run, so they can read it
     * from the field.
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

    /**
     * Listens to the progress of the session: audio capture, sound and speech
     * detection, and phrases that could not be recognized. The browser only
     * sends these events while there is a listener.
     *
     * @param listener receives each phase
     * @return a registration for removing the listener
     */
    public Registration addPhaseListener(SerializableConsumer<RecognitionPhase> listener) {
        Registration registration = add(phaseListeners, listener);
        if (phaseListeners.size() == 1) {
            pushConfig();
        }
        return () -> {
            registration.remove();
            if (phaseListeners.isEmpty()) {
                pushConfig();
            }
        };
    }

    /* Connection to the browser */

    private void connect(UI ui) {
        SpeechClient uiClient = SpeechClient.get(ui);
        if (client != uiClient) {
            disconnect();
            client = uiClient;
        }
        if (eventRegistration == null) {
            eventRegistration = client.onRecognitionEvent(id, this::handle);
            if (serverFallback != null) {
                transcriberRegistration = client.registerTranscriber(id, serverFallback);
            }
        }
    }

    /** On detach: the browser side is gone or going, so end the session. */
    private void disconnect() {
        if (client != null && eventRegistration != null) {
            client.call("m.dispose($0)", id);
        }
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
        }
        if (transcriberRegistration != null) {
            transcriberRegistration.remove();
            transcriberRegistration = null;
        }
        if (active && sessionError == null) {
            // The browser aborts the session, restoring a live target
            sessionError = RecognitionErrorCode.ABORTED;
        }
        finish();
    }

    /** Releases everything, the session cannot be used after this. */
    void release() {
        unbindTrigger();
        unbindKey();
        attachRegistration.remove();
        detachRegistration.remove();
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
            client.call("m.dispose($0)", id);
        }
        if (transcriberRegistration != null) {
            transcriberRegistration.remove();
            transcriberRegistration = null;
        }
    }

    /** Sends the current configuration to the browser, for trigger-started sessions. */
    private void pushConfig() {
        if (client != null && eventRegistration != null && (triggerBinding != null || keyBinding != null)) {
            client.call("m.configure(this, $0, $1, $2)", id, config(), liveTargetElement());
        }
    }

    private ObjectNode config() {
        ObjectNode config = options.toJson();
        config.put("interimToServer", interimResultsToServer);
        config.put("phaseEvents", !phaseListeners.isEmpty());
        config.put("serverFallback", serverFallback != null);
        config.put("liveAppend", liveAppend);
        return config;
    }

    private Element liveTargetElement() {
        return liveTarget == null ? null : ((Component) liveTarget).getElement();
    }

    String id() {
        return id;
    }

    int nextResultIndex() {
        return finalResults.isEmpty() ? 0 : finalResults.lastKey() + 1;
    }

    void handle(JsonNode json) {
        switch (json.path("type").asString("")) {
            case "starting" -> {
                // Also sent for sessions started from the server, which are active already
                if (!active) {
                    begin();
                }
                // The browser's value is what the user saw, the server's may
                // be older if the field was edited without a blur
                if (liveAppend && json.has("original")) {
                    liveBase = json.path("original").asString("");
                }
            }
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
            case "phase" -> {
                RecognitionPhase phase = RecognitionPhase.fromJs(json.path("phase").asString(""));
                if (phase != null) {
                    new ArrayList<>(phaseListeners).forEach(l -> l.accept(phase));
                }
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

    private void begin() {
        finalResults.clear();
        interimResults.clear();
        sessionError = null;
        String original = liveTarget != null && liveTarget.getValue() != null ? liveTarget.getValue() : "";
        liveBase = liveAppend ? original : "";
        setActive(true);
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
        if (sessionError == null) {
            sessionError = error.code();
        }
        new ArrayList<>(errorListeners).forEach(l -> l.accept(error));
    }

    private void finish() {
        if (!active) {
            return;
        }
        interimResults.clear();
        setActive(false);
        String transcript = getTranscript();
        // Like in the browser: an aborted session or one that recognized
        // nothing leaves the field as it was
        if (liveTarget != null && sessionError != RecognitionErrorCode.ABORTED && !transcript.isEmpty()) {
            liveTarget.setValue(joinTo(liveBase, transcript));
        }
        new ArrayList<>(endListeners).forEach(l -> l.accept(transcript));
    }

    private void setActive(boolean active) {
        this.active = active;
        new ArrayList<>(activeChangeListeners).forEach(l -> l.accept(active));
    }

    private static String join(Collection<String> parts) {
        return parts.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
    }

    /** Same as join() in speech.js, so the server ends up with what the browser shows. */
    static String joinTo(String base, String text) {
        if (text.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return text;
        }
        return Character.isWhitespace(base.charAt(base.length() - 1)) ? base + text : base + " " + text;
    }

    private static <T> Registration add(List<T> list, T listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        list.add(listener);
        return () -> list.remove(listener);
    }
}
