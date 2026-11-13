package org.vaadin.webspeech;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Speech recorded in the browser, to be transcribed by a
 * {@link SpeechToText server fallback}.
 */
public final class Recording {

    private final InputStream audio;
    private final String contentType;
    private final Locale lang;
    private final List<String> phrases;

    /**
     * @param audio       the recorded audio
     * @param contentType the audio's media type, e.g.
     *                    {@code audio/webm;codecs=opus}
     * @param lang        the language spoken, or {@code null} if not known
     * @param phrases     words and phrases to favour, see
     *                    {@link RecognitionOptions#phrases()}
     */
    public Recording(InputStream audio, String contentType, Locale lang, List<String> phrases) {
        this.audio = Objects.requireNonNull(audio, "audio must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.lang = lang;
        this.phrases = List.copyOf(phrases);
    }

    /**
     * @return the audio, as recorded by the browser's {@code MediaRecorder};
     *         read it once, no need to close it
     */
    public InputStream audio() {
        return audio;
    }

    /**
     * @return the audio's media type, e.g. {@code audio/webm;codecs=opus}
     *         (Chrome, Firefox) or {@code audio/mp4} (Safari)
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @return the language spoken: the session's
     *         {@link RecognitionOptions#lang() lang}, or the browser's
     *         language if that is not set; {@code null} if not known
     */
    public Locale lang() {
        return lang;
    }

    /**
     * @return words and phrases recognition should favour, e.g. for a
     *         transcription service's prompt, empty if none
     */
    public List<String> phrases() {
        return phrases;
    }

    /**
     * A file name with an extension matching the content type, for services
     * that detect the format from the file name (e.g. OpenAI's transcription
     * API).
     *
     * @return e.g. {@code recording.webm}, {@code recording.mp4}
     */
    public String fileName() {
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String subtype = type.contains("/") ? type.substring(type.indexOf('/') + 1) : "";
        String extension = switch (subtype) {
            case "mpeg", "mp3" -> "mp3";
            case "wav", "x-wav", "wave", "vnd.wave" -> "wav";
            case "x-m4a", "m4a" -> "m4a";
            case "ogg", "webm", "mp4", "flac", "aac" -> subtype;
            default -> "webm";
        };
        return "recording." + extension;
    }

    @Override
    public String toString() {
        return "Recording[contentType=" + contentType + ", lang=" + lang + ", phrases=" + phrases + "]";
    }
}
