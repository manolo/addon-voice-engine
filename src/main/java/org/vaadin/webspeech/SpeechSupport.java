package org.vaadin.webspeech;

/**
 * Whether the browser supports a part of the Web Speech API.
 * <p>
 * Support is detected asynchronously when the add-on is first used in a UI, so
 * the value is {@link #UNKNOWN} until the browser has responded.
 */
public enum SpeechSupport {
    /** The browser has not reported its capabilities yet. */
    UNKNOWN,
    /** The API is available in the browser. */
    SUPPORTED,
    /** The API is not available, e.g. SpeechRecognition in Firefox. */
    UNSUPPORTED;

    static SpeechSupport of(boolean supported) {
        return supported ? SUPPORTED : UNSUPPORTED;
    }
}
