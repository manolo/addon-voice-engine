package org.vaadin.voiceengine;

/**
 * Progress events of a recognition session, see
 * {@link SpeechRecognition#addPhaseListener(com.vaadin.flow.function.SerializableConsumer)}.
 * Not all browsers report all of them.
 */
public enum RecognitionPhase {
    /** The browser started capturing audio. */
    AUDIO_START("audiostart"),
    /** Some sound was detected. */
    SOUND_START("soundstart"),
    /** Speech was detected. A good moment to show "listening". */
    SPEECH_START("speechstart"),
    /** Speech is no longer detected. */
    SPEECH_END("speechend"),
    /** Sound is no longer detected. */
    SOUND_END("soundend"),
    /** The browser stopped capturing audio. */
    AUDIO_END("audioend"),
    /** Speech was heard but not recognized well enough. */
    NO_MATCH("nomatch");

    private final String jsName;

    RecognitionPhase(String jsName) {
        this.jsName = jsName;
    }

    String jsName() {
        return jsName;
    }

    static RecognitionPhase fromJs(String value) {
        for (RecognitionPhase p : values()) {
            if (p.jsName.equals(value)) {
                return p;
            }
        }
        return null;
    }
}
