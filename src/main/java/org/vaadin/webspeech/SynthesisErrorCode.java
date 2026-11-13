package org.vaadin.webspeech;

/**
 * Reason why an utterance was not spoken to the end. Mirrors the error codes of
 * the browser's {@code SpeechSynthesisErrorEvent}.
 */
public enum SynthesisErrorCode {
    /** Cancelled before speaking started, e.g. with {@link SpeechSynthesis#cancel()}. */
    CANCELED("canceled"),
    /** Interrupted while speaking, e.g. with {@link SpeechSynthesis#cancel()}. */
    INTERRUPTED("interrupted"),
    AUDIO_BUSY("audio-busy"),
    AUDIO_HARDWARE("audio-hardware"),
    NETWORK("network"),
    /** No synthesis engine available, or the browser does not support the API. */
    SYNTHESIS_UNAVAILABLE("synthesis-unavailable"),
    SYNTHESIS_FAILED("synthesis-failed"),
    LANGUAGE_UNAVAILABLE("language-unavailable"),
    VOICE_UNAVAILABLE("voice-unavailable"),
    TEXT_TOO_LONG("text-too-long"),
    INVALID_ARGUMENT("invalid-argument"),
    /**
     * Not allowed by the browser. Typically the page has not had any user
     * interaction yet (autoplay policy).
     */
    NOT_ALLOWED("not-allowed"),
    /** An error code not known by this version of the add-on. */
    UNKNOWN("");

    private final String jsCode;

    SynthesisErrorCode(String jsCode) {
        this.jsCode = jsCode;
    }

    String jsCode() {
        return jsCode;
    }

    static SynthesisErrorCode fromJs(String code) {
        for (SynthesisErrorCode c : values()) {
            if (c.jsCode.equals(code)) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
