package org.vaadin.voiceengine;

/**
 * How a bound trigger element controls a recognition session, see
 * {@link SpeechRecognition#bindTrigger(com.vaadin.flow.component.Component, TriggerMode)}.
 * In both modes, Escape aborts the session.
 */
public enum TriggerMode {
    /** A click starts listening, the next click stops. */
    TOGGLE("toggle"),
    /**
     * Push to talk: listens while the pointer, or Space or Enter, is held
     * down on the element.
     */
    HOLD("hold");

    private final String jsName;

    TriggerMode(String jsName) {
        this.jsName = jsName;
    }

    String jsName() {
        return jsName;
    }
}
