package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Text to be spoken with {@link SpeechSynthesis#speak(Utterance)}, with
 * optional language, voice and prosody settings. Unset ({@code null}) values
 * use browser defaults.
 *
 * @param text   the text to speak
 * @param lang   the language, or {@code null} for the voice's or the page's
 *               language
 * @param voice  the voice, or {@code null} for the browser's choice for the
 *               language
 * @param rate   speaking rate, 0.1 - 10, 1 is normal, or {@code null}
 * @param pitch  pitch, 0 - 2, 1 is normal, or {@code null}
 * @param volume volume, 0 - 1, or {@code null}
 */
public record Utterance(String text, Locale lang, Voice voice, Double rate, Double pitch, Double volume)
        implements Serializable {

    public Utterance {
        Objects.requireNonNull(text, "text must not be null");
    }

    /**
     * @param text the text to speak
     * @return an utterance with browser defaults
     */
    public static Utterance of(String text) {
        return new Utterance(text, null, null, null, null, null);
    }

    /**
     * @param text the text to speak
     * @return a builder for an utterance
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    ObjectNode toJson() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        if (lang != null) {
            json.put("lang", lang.toLanguageTag());
        }
        if (voice != null) {
            json.put("voiceUri", voice.voiceUri());
            json.put("voiceName", voice.name());
        }
        if (rate != null) {
            json.put("rate", rate);
        }
        if (pitch != null) {
            json.put("pitch", pitch);
        }
        if (volume != null) {
            json.put("volume", volume);
        }
        return json;
    }

    /**
     * Builder for {@link Utterance}.
     */
    public static final class Builder implements Serializable {
        private final String text;
        private Locale lang;
        private Voice voice;
        private Double rate;
        private Double pitch;
        private Double volume;

        private Builder(String text) {
            this.text = text;
        }

        public Builder lang(Locale lang) {
            this.lang = lang;
            return this;
        }

        public Builder voice(Voice voice) {
            this.voice = voice;
            return this;
        }

        public Builder rate(double rate) {
            this.rate = rate;
            return this;
        }

        public Builder pitch(double pitch) {
            this.pitch = pitch;
            return this;
        }

        public Builder volume(double volume) {
            this.volume = volume;
            return this;
        }

        public Utterance build() {
            return new Utterance(text, lang, voice, rate, pitch, volume);
        }
    }
}
