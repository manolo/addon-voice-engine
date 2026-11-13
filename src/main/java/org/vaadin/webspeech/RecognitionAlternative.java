package org.vaadin.webspeech;

import java.io.Serializable;

/**
 * One possible interpretation of a recognized phrase.
 *
 * @param transcript the recognized text
 * @param confidence the confidence, 0 - 1 (some browsers always report 0)
 */
public record RecognitionAlternative(String transcript, double confidence) implements Serializable {
}
