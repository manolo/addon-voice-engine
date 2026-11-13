package org.vaadin.webspeech;

/**
 * Reason for a speech recognition failure. Mirrors the error codes of the
 * browser's {@code SpeechRecognitionErrorEvent}, plus a few add-on specific
 * ones.
 */
public enum RecognitionErrorCode {
    /** No speech was detected. */
    NO_SPEECH("no-speech"),
    /** Recognition was aborted, e.g. with {@link SpeechRecognition#abort()}. */
    ABORTED("aborted"),
    /** Audio capture failed, e.g. no microphone. */
    AUDIO_CAPTURE("audio-capture"),
    /** The recognition service could not be reached. */
    NETWORK("network"),
    /** The user or the browser denied microphone access. */
    NOT_ALLOWED("not-allowed"),
    /** The browser does not allow the recognition service to be used. */
    SERVICE_NOT_ALLOWED("service-not-allowed"),
    /** The requested language is not supported. */
    LANGUAGE_NOT_SUPPORTED("language-not-supported"),
    BAD_GRAMMAR("bad-grammar"),
    /**
     * The browser cannot apply {@link RecognitionOptions#phrases()}, e.g.
     * Chrome with cloud recognition.
     */
    PHRASES_NOT_SUPPORTED("phrases-not-supported"),
    /**
     * The browser does not support speech recognition, e.g. Firefox, and no
     * {@link SpeechRecognition#setServerFallback(SpeechToText) server fallback}
     * is set.
     */
    RECOGNITION_UNAVAILABLE("recognition-unavailable"),
    /**
     * {@link RecognitionEngine#ON_DEVICE_ONLY} was requested, but the browser
     * cannot recognize speech on the device.
     */
    ON_DEVICE_UNAVAILABLE("on-device-unavailable"),
    /** The {@link SpeechRecognition#setServerFallback(SpeechToText) server fallback} failed. */
    TRANSCRIPTION_FAILED("transcription-failed"),
    /**
     * Recognition could not be started. The message is the raw JavaScript
     * error from the browser or from Flow's {@code executeJs}.
     */
    START_FAILED("start-failed"),
    /** An error code not known by this version of the add-on. */
    UNKNOWN("");

    private final String jsCode;

    RecognitionErrorCode(String jsCode) {
        this.jsCode = jsCode;
    }

    String jsCode() {
        return jsCode;
    }

    static RecognitionErrorCode fromJs(String code) {
        for (RecognitionErrorCode c : values()) {
            if (c.jsCode.equals(code)) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
