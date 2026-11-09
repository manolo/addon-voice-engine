package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.Locale;

/**
 * A speech synthesis voice available in the browser, see
 * {@link SpeechSynthesis#getVoices(com.vaadin.flow.function.SerializableConsumer)}.
 *
 * @param name         human readable name, e.g. "Google suomi"
 * @param lang         BCP 47 language tag, e.g. "fi-FI"
 * @param localService {@code true} if the voice is provided by the device
 *                     (works offline), {@code false} for a remote service
 * @param defaultVoice {@code true} if this is the browser's default voice
 * @param voiceUri     identifier of the voice, used to select it
 */
public record Voice(String name, String lang, boolean localService, boolean defaultVoice, String voiceUri)
        implements Serializable {

    /**
     * @return the language of this voice as a {@link Locale}
     */
    public Locale locale() {
        return Locale.forLanguageTag(lang);
    }
}
