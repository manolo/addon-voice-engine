package org.vaadin.webspeech;

import java.io.Serializable;

/**
 * Error reported by speech recognition.
 *
 * @param code    the error code
 * @param jsCode  the raw error code reported by the browser
 * @param message additional information from the browser, possibly empty
 */
public record RecognitionError(RecognitionErrorCode code, String jsCode, String message) implements Serializable {
}
