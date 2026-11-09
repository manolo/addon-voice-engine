package org.vaadin.voiceengine;

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
    /** The browser does not support speech recognition, e.g. Firefox. */
    RECOGNITION_UNAVAILABLE("recognition-unavailable"),
    /** The browser refused to start recognition. */
    START_FAILED("start-failed"),
    /** An error code not known by this version of the add-on. */
    UNKNOWN("");

    private final String jsCode;

    RecognitionErrorCode(String jsCode) {
        this.jsCode = jsCode;
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
