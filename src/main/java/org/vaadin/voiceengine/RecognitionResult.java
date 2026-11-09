package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.List;

/**
 * A recognized phrase. With interim results enabled, the same {@code index} is
 * reported several times until a final result replaces the interim ones.
 *
 * @param index        position of the phrase within the recognition session
 * @param isFinal      {@code true} if the browser will not change this phrase
 *                     any more
 * @param alternatives the interpretations, most likely first
 */
public record RecognitionResult(int index, boolean isFinal, List<RecognitionAlternative> alternatives)
        implements Serializable {

    /**
     * @return the transcript of the most likely interpretation
     */
    public String transcript() {
        return alternatives.isEmpty() ? "" : alternatives.get(0).transcript();
    }

    /**
     * @return the confidence of the most likely interpretation
     */
    public double confidence() {
        return alternatives.isEmpty() ? 0 : alternatives.get(0).confidence();
    }
}
