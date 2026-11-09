package org.vaadin.voiceengine;

/**
 * State of speech synthesis in a UI, see {@link SpeechSynthesis#getState()}.
 */
public enum SynthesisState {
    /** Nothing is being spoken or queued. */
    IDLE,
    /** An utterance is being spoken or is queued to be spoken. */
    SPEAKING,
    /** Speaking is paused with {@link SpeechSynthesis#pause()}. */
    PAUSED
}
