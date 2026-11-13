package org.vaadin.webspeech;

/**
 * How the browser records audio for the
 * {@link SpeechRecognition#setServerFallback(SpeechToText, RecordingFormat)
 * server fallback}.
 */
public enum RecordingFormat {
    /**
     * The browser's own compressed format, e.g. WebM/Opus in Chrome and
     * Firefox or MP4/AAC in Safari. Small uploads, for transcription services
     * that decode these, such as OpenAI's.
     */
    BROWSER("browser"),
    /**
     * WAV, 16 kHz mono 16-bit PCM, the same in every browser: what most
     * speech models (Whisper, Parakeet, ...) take as is, without a decoder
     * like ffmpeg on the server. About 32 kB per second of speech.
     */
    WAV("wav");

    private final String jsName;

    RecordingFormat(String jsName) {
        this.jsName = jsName;
    }

    String jsName() {
        return jsName;
    }
}
