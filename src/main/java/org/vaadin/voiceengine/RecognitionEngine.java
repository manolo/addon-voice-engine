package org.vaadin.voiceengine;

/**
 * Where speech may be recognized, see
 * {@link RecognitionOptions.Builder#engine(RecognitionEngine)}.
 * <p>
 * By default, browsers decide themselves: Chrome sends the audio to a Google
 * service, for example. Newer Chrome and Edge can also recognize speech on the
 * device, which keeps the audio private and works offline, once the language
 * pack has been downloaded.
 * <p>
 * Browsers controlled by automation ({@code navigator.webdriver}, e.g.
 * Playwright or Selenium) are treated as having no on-device recognition, see
 * {@link SpeechRecognition#checkOnDeviceAvailability(java.util.Locale, com.vaadin.flow.function.SerializableConsumer)}.
 */
public enum RecognitionEngine {
    /** The browser decides. In Chrome and Edge, this means a cloud service. */
    ANY("any"),
    /**
     * On the device if the language pack is installed, otherwise the
     * browser's default. If the pack can be downloaded, the download is
     * started for the next time. The availability check delays the start
     * slightly (at most 1.5 seconds).
     */
    PREFER_ON_DEVICE("prefer-on-device"),
    /**
     * Only on the device: the audio never leaves it. Fails with
     * {@link RecognitionErrorCode#ON_DEVICE_UNAVAILABLE} if the browser cannot
     * recognize on the device at all, and with
     * {@link RecognitionErrorCode#SERVICE_NOT_ALLOWED} if the language pack is
     * not installed, see
     * {@link SpeechRecognition#checkOnDeviceAvailability(java.util.Locale, com.vaadin.flow.function.SerializableConsumer)}.
     */
    ON_DEVICE_ONLY("on-device-only");

    private final String jsName;

    RecognitionEngine(String jsName) {
        this.jsName = jsName;
    }

    String jsName() {
        return jsName;
    }
}
