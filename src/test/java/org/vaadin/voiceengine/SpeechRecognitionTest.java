package org.vaadin.voiceengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.html.Div;

class SpeechRecognitionTest {

    private static RecognitionResult result(int index, boolean isFinal, String text) {
        return new RecognitionResult(index, isFinal, List.of(new RecognitionAlternative(text, 0.9)));
    }

    @Test
    void transcriptJoinsFinalResultsInOrder() {
        SpeechRecognition recognition = SpeechRecognition.create(new Div());
        recognition.onResult(result(1, true, " world"));
        recognition.onResult(result(0, true, "Hello"));
        assertEquals("Hello world", recognition.getTranscript());
        assertEquals("", recognition.getInterimTranscript());
    }

    @Test
    void finalResultReplacesInterimForSameIndex() {
        SpeechRecognition recognition = SpeechRecognition.create(new Div());
        recognition.onResult(result(0, true, "One"));
        recognition.onResult(result(1, false, "tw"));
        assertEquals("One", recognition.getTranscript());
        assertEquals("tw", recognition.getInterimTranscript());

        recognition.onResult(result(1, true, "two"));
        assertEquals("One two", recognition.getTranscript());
        assertEquals("", recognition.getInterimTranscript());
    }

    @Test
    void resultListenersGetEveryResult() {
        SpeechRecognition recognition = SpeechRecognition.create(new Div());
        List<RecognitionResult> received = new ArrayList<>();
        recognition.addResultListener(received::add);
        recognition.onResult(result(0, false, "a"));
        recognition.onResult(result(0, true, "ab"));
        assertEquals(2, received.size());
    }

    @Test
    void startRequiresAttachedOwner() {
        SpeechRecognition recognition = SpeechRecognition.create(new Div());
        assertThrows(IllegalStateException.class, recognition::start);
        assertFalse(recognition.isActive());
    }

    @Test
    void optionsBuilder() {
        RecognitionOptions options = RecognitionOptions.builder().lang(Locale.of("fi", "FI"))
                .continuous(true).interimResults(true).build();
        assertEquals("fi-FI", options.toJson().get("lang").asString());
        assertEquals(1, options.maxAlternatives());
        assertThrows(IllegalArgumentException.class, () -> options.toBuilder().maxAlternatives(0).build());
    }

    @Test
    void errorCodesMapFromBrowserCodes() {
        assertEquals(RecognitionErrorCode.NOT_ALLOWED, RecognitionErrorCode.fromJs("not-allowed"));
        assertEquals(RecognitionErrorCode.UNKNOWN, RecognitionErrorCode.fromJs("something-new"));
        assertEquals(SynthesisErrorCode.INTERRUPTED, SynthesisErrorCode.fromJs("interrupted"));
        assertEquals(SynthesisErrorCode.UNKNOWN, SynthesisErrorCode.fromJs(null));
    }
}
