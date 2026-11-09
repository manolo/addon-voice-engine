package org.vaadin.voiceengine;

import java.io.Serializable;

/**
 * Error reported when an utterance could not be spoken to the end.
 *
 * @param code   the error code
 * @param jsCode the raw error code reported by the browser
 */
public record SynthesisError(SynthesisErrorCode code, String jsCode) implements Serializable {

    /**
     * @return {@code true} if speaking was cancelled or interrupted on purpose,
     *         e.g. with {@link SpeechSynthesis#cancel()}, rather than failing
     */
    public boolean isCancellation() {
        return code == SynthesisErrorCode.CANCELED || code == SynthesisErrorCode.INTERRUPTED;
    }
}
