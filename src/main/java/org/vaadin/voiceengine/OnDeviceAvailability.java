package org.vaadin.voiceengine;

/**
 * Whether a language can be recognized on the device, see
 * {@link SpeechRecognition#checkOnDeviceAvailability(java.util.Locale, com.vaadin.flow.function.SerializableConsumer)}.
 */
public enum OnDeviceAvailability {
    /** The language pack is installed. */
    AVAILABLE("available"),
    /** The language pack can be downloaded, see {@link SpeechRecognition#installOnDevice}. */
    DOWNLOADABLE("downloadable"),
    /** The language pack is being downloaded. */
    DOWNLOADING("downloading"),
    /**
     * On-device recognition is not possible for the language, or the browser
     * does not support it or did not answer in time.
     */
    UNAVAILABLE("unavailable");

    private final String jsName;

    OnDeviceAvailability(String jsName) {
        this.jsName = jsName;
    }

    static OnDeviceAvailability fromJs(String value) {
        for (OnDeviceAvailability a : values()) {
            if (a.jsName.equals(value)) {
                return a;
            }
        }
        return UNAVAILABLE;
    }
}
