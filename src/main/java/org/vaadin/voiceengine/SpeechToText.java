package org.vaadin.voiceengine;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Transcribes recorded speech on the server, for browsers that cannot
 * recognize speech themselves. See
 * {@link SpeechRecognition#setServerFallback(SpeechToText)}.
 * <p>
 * Called in the upload request's thread without the Vaadin session lock, so
 * it may take its time, but must not touch UI components.
 *
 * <pre>{@code
 * // e.g. with Spring AI
 * recognition.setServerFallback((audio, contentType) ->
 *         transcriptionModel.transcribe(new InputStreamResource(audio)));
 * }</pre>
 */
@FunctionalInterface
public interface SpeechToText extends Serializable {

    /**
     * @param audio       the recording, as recorded by the browser's
     *                    {@code MediaRecorder}
     * @param contentType the recording's media type, e.g.
     *                    {@code audio/webm;codecs=opus} or {@code audio/mp4}
     * @return the recognized text, empty if nothing was recognized
     * @throws IOException if transcription fails, reported to the session as
     *                     {@link RecognitionErrorCode#TRANSCRIPTION_FAILED}
     */
    String transcribe(InputStream audio, String contentType) throws IOException;
}
