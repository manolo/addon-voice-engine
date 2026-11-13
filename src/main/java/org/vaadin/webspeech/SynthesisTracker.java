package org.vaadin.webspeech;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;

/**
 * Tracks queued utterances and the synthesis state of one UI.
 */
final class SynthesisTracker implements Serializable {

    private record Pending(Utterance utterance, SerializableRunnable onEnd,
            SerializableConsumer<SynthesisError> onError, Registration registration) implements Serializable {
    }

    private final SpeechClient client;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final List<SerializableConsumer<SynthesisState>> listeners = new ArrayList<>();
    private boolean paused;
    private SynthesisState state = SynthesisState.IDLE;

    SynthesisTracker(SpeechClient client) {
        this.client = client;
    }

    SynthesisState state() {
        return state;
    }

    void speak(Utterance utterance, SerializableRunnable onEnd, SerializableConsumer<SynthesisError> onError) {
        String id = SpeechClient.newId();
        Registration registration = client.onSynthesisEvent(id, json -> handle(id, json));
        pending.put(id, new Pending(utterance, onEnd, onError, registration));
        client.call("m.speak(this, $0, $1, $2)", id, utterance.text(), utterance.toJson())
                .then(ok -> {
                }, failure -> finish(id, new SynthesisError(SynthesisErrorCode.SYNTHESIS_UNAVAILABLE, failure)));
        updateState();
    }

    void cancel() {
        client.call("m.cancel()");
        List<Pending> cancelled = new ArrayList<>(pending.values());
        pending.clear();
        paused = false;
        cancelled.forEach(p -> p.registration().remove());
        updateState();
        SynthesisError error = new SynthesisError(SynthesisErrorCode.CANCELED, "canceled");
        cancelled.forEach(p -> p.onError().accept(error));
    }

    void pause() {
        client.call("m.pause()");
        if (!pending.isEmpty()) {
            paused = true;
            updateState();
        }
    }

    void resume() {
        client.call("m.resume()");
        paused = false;
        updateState();
    }

    Registration addStateChangeListener(SerializableConsumer<SynthesisState> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** For {@link SimulatedSpeech}: the queued utterances by id, oldest first. */
    Map<String, Utterance> queued() {
        Map<String, Utterance> result = new LinkedHashMap<>();
        pending.forEach((id, p) -> result.put(id, p.utterance()));
        return result;
    }

    void handle(String id, JsonNode json) {
        switch (json.path("type").asString("")) {
            case "start", "resume" -> {
                paused = false;
                updateState();
            }
            case "pause" -> {
                paused = true;
                updateState();
            }
            case "end" -> finish(id, null);
            case "error" -> {
                String code = json.path("error").asString("");
                finish(id, new SynthesisError(SynthesisErrorCode.fromJs(code), code));
            }
            default -> {
                // ignore unknown events
            }
        }
    }

    private void finish(String id, SynthesisError error) {
        Pending p = pending.remove(id);
        if (p == null) {
            return;
        }
        p.registration().remove();
        if (pending.isEmpty()) {
            paused = false;
        }
        updateState();
        if (error == null) {
            p.onEnd().run();
        } else {
            p.onError().accept(error);
        }
    }

    private void updateState() {
        SynthesisState newState = pending.isEmpty() ? SynthesisState.IDLE
                : paused ? SynthesisState.PAUSED : SynthesisState.SPEAKING;
        if (newState != state) {
            state = newState;
            new ArrayList<>(listeners).forEach(l -> l.accept(newState));
        }
    }
}
