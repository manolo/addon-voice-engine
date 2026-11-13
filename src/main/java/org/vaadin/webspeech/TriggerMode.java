package org.vaadin.webspeech;

/**
 * How a bound trigger element controls a recognition session, see
 * {@link SpeechRecognition#bindTrigger(com.vaadin.flow.component.Component, TriggerMode)}.
 * In all modes, Escape aborts the session.
 */
public enum TriggerMode {
    /** A click starts listening, the next click stops. */
    TOGGLE("toggle"),
    /**
     * Push to talk: listens while the pointer, or Space or Enter, is held
     * down on the element.
     */
    HOLD("hold"),
    /**
     * A press starts listening. After a short tap it keeps listening until the
     * next press, like {@link #TOGGLE}; after a longer press (over 400 ms) it
     * stops when released, like {@link #HOLD}. A click without a press, e.g.
     * from a screen reader, toggles. Suits a field's microphone, where both
     * habits are common.
     */
    TAP_OR_HOLD("tap-or-hold");

    private final String jsName;

    TriggerMode(String jsName) {
        this.jsName = jsName;
    }

    String jsName() {
        return jsName;
    }
}
