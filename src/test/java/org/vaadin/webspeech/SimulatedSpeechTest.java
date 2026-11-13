package org.vaadin.webspeech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vaadin.webspeech.ui.DictationButton;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextArea;

class SimulatedSpeechTest {

    private UI ui;

    @BeforeEach
    void setUp() {
        ui = new UI();
        // Like browserless test frameworks do
        UI.setCurrent(ui);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void dictationButtonWritesFinalTextToField() {
        TextArea field = new TextArea();
        field.setValue("Before");
        DictationButton button = new DictationButton(field);
        ui.add(field, button);
        SpeechRecognition recognition = button.getRecognition();

        SimulatedSpeech.start(recognition);
        assertTrue(recognition.isActive());
        assertEquals("Stop dictation", button.getText());

        SimulatedSpeech.finalResult(recognition, "hello");
        SimulatedSpeech.finalResult(recognition, "world");
        SimulatedSpeech.end(recognition);

        assertFalse(recognition.isActive());
        assertEquals("Before hello world", field.getValue());
        assertEquals("Dictate", button.getText());
    }

    @Test
    void appendsToTheValueTheBrowserReportsAtStart() {
        TextArea field = new TextArea();
        field.setValue("old");
        DictationButton button = new DictationButton(field);
        ui.add(field, button);
        SpeechRecognition recognition = button.getRecognition();

        // The user edited the field, but no value change reached the server
        recognition.handle(tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("type", "starting").put("original", "edited"));
        SimulatedSpeech.finalResult(recognition, "hello");
        SimulatedSpeech.end(recognition);
        assertEquals("edited hello", field.getValue());
    }

    @Test
    void abortKeepsOriginalValue() {
        TextArea field = new TextArea();
        field.setValue("Before");
        DictationButton button = new DictationButton(field);
        ui.add(field, button);
        SpeechRecognition recognition = button.getRecognition();

        SimulatedSpeech.start(recognition);
        SimulatedSpeech.finalResult(recognition, "hello");
        SimulatedSpeech.error(recognition, RecognitionErrorCode.ABORTED);
        SimulatedSpeech.end(recognition);
        assertEquals("Before", field.getValue());
    }

    @Test
    void replacingTargetKeepsValueWhenNothingIsRecognized() {
        TextArea field = new TextArea();
        field.setValue("kept");
        DictationButton button = new DictationButton(field);
        button.setAppend(false);
        ui.add(field, button);
        SpeechRecognition recognition = button.getRecognition();

        SimulatedSpeech.start(recognition);
        SimulatedSpeech.end(recognition);
        assertEquals("kept", field.getValue());

        SimulatedSpeech.start(recognition);
        SimulatedSpeech.finalResult(recognition, "discarded");
        SimulatedSpeech.error(recognition, RecognitionErrorCode.ABORTED);
        SimulatedSpeech.end(recognition);
        assertEquals("kept", field.getValue());

        SimulatedSpeech.start(recognition);
        SimulatedSpeech.finalResult(recognition, "replaced");
        SimulatedSpeech.end(recognition);
        assertEquals("replaced", field.getValue());
    }

    @Test
    void phasesAndInterimResultsReachListeners() {
        TextArea field = new TextArea();
        ui.add(field);
        SpeechRecognition recognition = SpeechRecognition.create(field);
        List<String> events = new ArrayList<>();
        recognition.addPhaseListener(p -> events.add(p.name()));
        recognition.addResultListener(r -> events.add((r.isFinal() ? "final:" : "interim:") + r.transcript()));
        recognition.start();

        SimulatedSpeech.phase(recognition, RecognitionPhase.SPEECH_START);
        SimulatedSpeech.interimResult(recognition, "hel");
        assertEquals("hel", recognition.getInterimTranscript());
        SimulatedSpeech.finalResult(recognition, "hello");
        SimulatedSpeech.interimResult(recognition, "wor");
        assertEquals("hello", recognition.getTranscript());
        assertEquals("wor", recognition.getInterimTranscript());
        SimulatedSpeech.end(recognition);

        assertEquals(List.of("SPEECH_START", "interim:hel", "final:hello", "interim:wor"), events);
        assertEquals("", recognition.getInterimTranscript());
    }

    @Test
    void supportCallbacksRunWhenBrowserAnswers() {
        List<SpeechSupport> received = new ArrayList<>();
        SpeechRecognition.whenSupportKnown(ui, received::add);
        SpeechSynthesis.whenSupportKnown(ui, received::add);
        assertEquals(List.of(), received);

        SimulatedSpeech.setSupport(ui, SpeechSupport.SUPPORTED, SpeechSupport.UNSUPPORTED);
        assertEquals(List.of(SpeechSupport.UNSUPPORTED, SpeechSupport.SUPPORTED), received);

        SpeechRecognition.whenSupportKnown(ui, received::add);
        assertEquals(SpeechSupport.UNSUPPORTED, received.get(2));
    }

    @Test
    void dictationButtonDisablesItselfWithoutSupport() {
        TextArea field = new TextArea();
        DictationButton button = new DictationButton(field);
        ui.add(field, button);
        SimulatedSpeech.setSupport(ui, SpeechSupport.SUPPORTED, SpeechSupport.UNSUPPORTED);
        assertFalse(button.isEnabled());
    }

    @Test
    void synthesisQueueCanBeInspectedAndCompleted() {
        List<String> events = new ArrayList<>();
        SpeechSynthesis.addStateChangeListener(ui, s -> events.add(s.name()));
        SpeechSynthesis.speak(ui, Utterance.of("Done."), () -> events.add("ended"), e -> events.add("error"));
        SpeechSynthesis.speak(ui, Utterance.of("Second"), () -> events.add("ended"),
                e -> events.add(e.code().name()));

        assertEquals(List.of("Done.", "Second"),
                SimulatedSpeech.getQueuedUtterances(ui).stream().map(Utterance::text).toList());
        SimulatedSpeech.finishUtterance(ui);
        SimulatedSpeech.failUtterance(ui, SynthesisErrorCode.INTERRUPTED);

        assertEquals(List.of("SPEAKING", "ended", "IDLE", "INTERRUPTED"), events);
        assertTrue(SimulatedSpeech.getQueuedUtterances(ui).isEmpty());
    }

    @Test
    void joinMatchesBrowser() {
        assertEquals("a b", SpeechRecognition.joinTo("a", "b"));
        assertEquals("a\nb", SpeechRecognition.joinTo("a\n", "b"));
        assertEquals("b", SpeechRecognition.joinTo("", "b"));
        assertEquals("a", SpeechRecognition.joinTo("a", ""));
    }
}
