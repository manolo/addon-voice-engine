package org.vaadin.voiceengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.vaadin.flow.component.UI;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Simulates the browser's side of speech recognition and synthesis, for
 * testing views without a browser, e.g. with Vaadin's browserless testing or
 * plain unit tests that attach components to a {@link UI}.
 * <p>
 * The simulated events go through the same handling as real ones from the
 * browser, so listeners, {@link SpeechRecognition#isActive()}, transcripts,
 * live targets and synthesis states behave as in a browser.
 *
 * <pre>{@code
 * SimulatedSpeech.setSupport(ui, SpeechSupport.SUPPORTED, SpeechSupport.SUPPORTED);
 * SpeechRecognition recognition = dictationButton.getRecognition();
 * SimulatedSpeech.start(recognition);           // as if the user pressed the button
 * SimulatedSpeech.interimResult(recognition, "hello wor");
 * SimulatedSpeech.finalResult(recognition, "hello world");
 * SimulatedSpeech.end(recognition);
 * assertEquals("hello world", textArea.getValue());
 *
 * view.readSummaryAloud();
 * assertEquals("Done.", SimulatedSpeech.getQueuedUtterances(ui).get(0).text());
 * SimulatedSpeech.finishUtterance(ui);
 * }</pre>
 * <p>
 * Not for production code.
 */
public final class SimulatedSpeech {

    private SimulatedSpeech() {
    }

    /**
     * Sets what the browser reports as supported, as if the browser had
     * answered. Runs pending {@code whenSupportKnown} callbacks.
     *
     * @param ui          the UI
     * @param synthesis   synthesis support
     * @param recognition recognition support
     */
    public static void setSupport(UI ui, SpeechSupport synthesis, SpeechSupport recognition) {
        SpeechClient.get(ui).setSupport(synthesis, recognition);
    }

    /**
     * The session starts in the browser, as if the user pressed a
     * {@link SpeechRecognition#bindTrigger bound trigger}. Not needed after
     * {@link SpeechRecognition#start()}.
     *
     * @param recognition the session
     */
    public static void start(SpeechRecognition recognition) {
        recognition.handle(event("starting"));
    }

    /**
     * The browser reports text that may still change.
     *
     * @param recognition the session
     * @param transcript  the text
     */
    public static void interimResult(SpeechRecognition recognition, String transcript) {
        result(recognition, transcript, false);
    }

    /**
     * The browser reports a final phrase. The next result starts a new
     * phrase.
     *
     * @param recognition the session
     * @param transcript  the text
     */
    public static void finalResult(SpeechRecognition recognition, String transcript) {
        result(recognition, transcript, true);
    }

    private static void result(SpeechRecognition recognition, String transcript, boolean isFinal) {
        Objects.requireNonNull(transcript, "transcript must not be null");
        ObjectNode event = event("result");
        ObjectNode result = event.putArray("results").addObject();
        result.put("index", nextIndex(recognition));
        result.put("isFinal", isFinal);
        result.putArray("alternatives").addObject().put("transcript", transcript).put("confidence", 0.9);
        recognition.handle(event);
    }

    private static int nextIndex(SpeechRecognition recognition) {
        // Phrases are numbered from 0, like in the browser
        return recognition.nextResultIndex();
    }

    /**
     * The browser reports progress.
     *
     * @param recognition the session
     * @param phase       the phase
     */
    public static void phase(SpeechRecognition recognition, RecognitionPhase phase) {
        recognition.handle(event("phase").put("phase", phase.jsName()));
    }

    /**
     * The browser reports an error. Call {@link #end(SpeechRecognition)}
     * after it, like the browser does.
     *
     * @param recognition the session
     * @param code        the error
     */
    public static void error(SpeechRecognition recognition, RecognitionErrorCode code) {
        recognition.handle(event("error").put("error", code.jsCode()));
    }

    /**
     * The session ends.
     *
     * @param recognition the session
     */
    public static void end(SpeechRecognition recognition) {
        recognition.handle(event("end"));
    }

    /**
     * @param ui the UI
     * @return the utterances queued for speaking and not finished yet, oldest
     *         first
     */
    public static List<Utterance> getQueuedUtterances(UI ui) {
        return new ArrayList<>(SpeechClient.get(ui).synthesis.queued().values());
    }

    /**
     * The oldest queued utterance is spoken to the end.
     *
     * @param ui the UI
     * @throws IllegalStateException if nothing is queued
     */
    public static void finishUtterance(UI ui) {
        String id = oldest(ui);
        SpeechClient.get(ui).synthesis.handle(id, event("start"));
        SpeechClient.get(ui).synthesis.handle(id, event("end"));
    }

    /**
     * The oldest queued utterance fails.
     *
     * @param ui   the UI
     * @param code the error
     * @throws IllegalStateException if nothing is queued
     */
    public static void failUtterance(UI ui, SynthesisErrorCode code) {
        SpeechClient.get(ui).synthesis.handle(oldest(ui),
                event("error").put("error", code.jsCode()));
    }

    private static String oldest(UI ui) {
        Map<String, Utterance> queued = SpeechClient.get(ui).synthesis.queued();
        if (queued.isEmpty()) {
            throw new IllegalStateException("No utterance is queued");
        }
        return queued.keySet().iterator().next();
    }

    private static ObjectNode event(String type) {
        return JsonNodeFactory.instance.objectNode().put("type", type);
    }
}
