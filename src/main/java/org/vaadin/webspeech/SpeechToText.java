package org.vaadin.webspeech;

import java.io.IOException;
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
 * // e.g. with Spring AI and OpenAI, which needs a file name with an extension
 * recognition.setServerFallback(recording -> transcriptionModel.transcribe(
 *         new InputStreamResource(recording.audio()) {
 *             public String getFilename() {
 *                 return recording.fileName();
 *             }
 *         }));
 * }</pre>
 */
@FunctionalInterface
public interface SpeechToText extends Serializable {

    /**
     * @param recording the recorded audio, its type and language
     * @return the recognized text, empty if nothing was recognized
     * @throws IOException if transcription fails, reported to the session as
     *                     {@link RecognitionErrorCode#TRANSCRIPTION_FAILED}
     */
    String transcribe(Recording recording) throws IOException;
}
