package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.shared.Registration;

import tools.jackson.core.type.TypeReference;

/**
 * Text-to-speech using the browser's
 * <a href="https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis">SpeechSynthesis</a>
 * API.
 * <p>
 * The browser has a single speech queue per page, so this is a static facade.
 * Methods without a {@link UI} parameter use {@link UI#getCurrent()}.
 *
 * <pre>{@code
 * SpeechSynthesis.speak("Hello world");
 *
 * SpeechSynthesis.speak(Utterance.builder("Hei maailma").lang(Locale.of("fi", "FI")).rate(1.2).build(),
 *         () -> Notification.show("Done"),
 *         error -> Notification.show("Failed: " + error.code()));
 * }</pre>
 * <p>
 * Browsers only allow speaking after the user has interacted with the page
 * (e.g. clicked something). Before that, utterances fail with
 * {@link SynthesisErrorCode#NOT_ALLOWED}.
 */
public final class SpeechSynthesis implements Serializable {

    private SpeechSynthesis() {
    }

    /**
     * Speaks the text with default settings in the current UI.
     *
     * @param text the text to speak
     */
    public static void speak(String text) {
        speak(Utterance.of(text));
    }

    /**
     * Queues the utterance in the current UI.
     *
     * @param utterance the utterance to speak
     */
    public static void speak(Utterance utterance) {
        speak(utterance, () -> {
        }, error -> {
        });
    }

    /**
     * Queues the utterance in the current UI.
     *
     * @param utterance the utterance to speak
     * @param onEnd     called when the utterance has been spoken to the end
     * @param onError   called if the utterance fails or is cancelled, see
     *                  {@link SynthesisError#isCancellation()}
     */
    public static void speak(Utterance utterance, SerializableRunnable onEnd,
            SerializableConsumer<SynthesisError> onError) {
        speak(SpeechClient.currentUi(), utterance, onEnd, onError);
    }

    /**
     * Queues the utterance in the given UI.
     *
     * @param ui        the UI to speak in
     * @param utterance the utterance to speak
     * @param onEnd     called when the utterance has been spoken to the end
     * @param onError   called if the utterance fails or is cancelled, see
     *                  {@link SynthesisError#isCancellation()}
     */
    public static void speak(UI ui, Utterance utterance, SerializableRunnable onEnd,
            SerializableConsumer<SynthesisError> onError) {
        Objects.requireNonNull(utterance, "utterance must not be null");
        Objects.requireNonNull(onEnd, "onEnd must not be null");
        Objects.requireNonNull(onError, "onError must not be null");
        tracker(ui).speak(utterance, onEnd, onError);
    }

    /**
     * Stops speaking and clears the queue in the current UI. Pending utterances
     * get their error callback called with {@link SynthesisErrorCode#CANCELED}.
     */
    public static void cancel() {
        cancel(SpeechClient.currentUi());
    }

    /**
     * Stops speaking and clears the queue in the given UI.
     *
     * @param ui the UI
     */
    public static void cancel(UI ui) {
        tracker(ui).cancel();
    }

    /**
     * Pauses speaking in the current UI.
     */
    public static void pause() {
        pause(SpeechClient.currentUi());
    }

    /**
     * Pauses speaking in the given UI.
     *
     * @param ui the UI
     */
    public static void pause(UI ui) {
        tracker(ui).pause();
    }

    /**
     * Resumes paused speaking in the current UI.
     */
    public static void resume() {
        resume(SpeechClient.currentUi());
    }

    /**
     * Resumes paused speaking in the given UI.
     *
     * @param ui the UI
     */
    public static void resume(UI ui) {
        tracker(ui).resume();
    }

    /**
     * @return the synthesis state of the current UI
     */
    public static SynthesisState getState() {
        return getState(SpeechClient.currentUi());
    }

    /**
     * @param ui the UI
     * @return the synthesis state of the given UI
     */
    public static SynthesisState getState(UI ui) {
        return tracker(ui).state();
    }

    /**
     * Listens to synthesis state changes in the UI of the owner component. The
     * listener is active while the owner is attached, so it is removed
     * automatically with the owner.
     *
     * @param owner    the component whose lifecycle the listener follows
     * @param listener called with the new state
     * @return a registration for removing the listener
     */
    public static Registration addStateChangeListener(Component owner,
            SerializableConsumer<SynthesisState> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        return new OwnerBinding(owner, ui -> tracker(ui).addStateChangeListener(listener));
    }

    /**
     * Fetches the voices available in the current UI's browser.
     *
     * @param callback receives the voices, possibly an empty list
     */
    public static void getVoices(SerializableConsumer<List<Voice>> callback) {
        getVoices(SpeechClient.currentUi(), callback);
    }

    /**
     * Fetches the voices available in the given UI's browser.
     *
     * @param ui       the UI
     * @param callback receives the voices, possibly an empty list
     */
    public static void getVoices(UI ui, SerializableConsumer<List<Voice>> callback) {
        SpeechClient.get(ui).call("m.voices()").then(new TypeReference<List<Voice>>() {
        }, callback, error -> callback.accept(List.of()));
    }

    /**
     * @return whether the current UI's browser supports speech synthesis,
     *         {@link SpeechSupport#UNKNOWN} until the browser has responded
     */
    public static SpeechSupport getSupport() {
        return getSupport(SpeechClient.currentUi());
    }

    /**
     * @param ui the UI
     * @return whether the UI's browser supports speech synthesis,
     *         {@link SpeechSupport#UNKNOWN} until the browser has responded
     */
    public static SpeechSupport getSupport(UI ui) {
        return SpeechClient.get(ui).synthesisSupport();
    }

    /**
     * Calls the callback once the browser has reported whether it supports
     * speech synthesis, immediately if already known.
     *
     * @param ui       the UI
     * @param callback receives {@link SpeechSupport#SUPPORTED} or
     *                 {@link SpeechSupport#UNSUPPORTED}
     */
    public static void whenSupportKnown(UI ui, SerializableConsumer<SpeechSupport> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        SpeechClient client = SpeechClient.get(ui);
        client.whenSupportKnown(() -> callback.accept(client.synthesisSupport()));
    }

    /**
     * Allows speaking later, outside user interaction, in browsers that
     * require speech to be started from a user gesture first (iOS Safari).
     * The first click, tap or key press on the component speaks a silent,
     * empty utterance in the browser, without a server round trip.
     * <p>
     * Use it on the button that starts something that is read aloud when it
     * completes, e.g. a question to an AI agent.
     *
     * @param component the component whose first activation unlocks speech
     * @return a registration for removing the priming before it happens
     */
    public static Registration prime(Component component) {
        Objects.requireNonNull(component, "component must not be null");
        return new OwnerBinding(component, ui -> {
            SpeechClient client = SpeechClient.get(ui);
            client.call("m.prime($0)", component.getElement());
            return () -> client.call("m.unprime($0)", component.getElement());
        });
    }

    private static SynthesisTracker tracker(UI ui) {
        return SpeechClient.get(ui).synthesis;
    }
}
