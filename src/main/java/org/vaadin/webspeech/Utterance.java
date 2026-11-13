package org.vaadin.webspeech;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Text to be spoken with {@link SpeechSynthesis#speak(Utterance)}, with
 * optional language, voice and prosody settings. Unset ({@code null}) values
 * use browser defaults. Immutable, created with {@link #of(String)} or
 * {@link #builder(String)}.
 */
public final class Utterance implements Serializable {

    private final String text;
    private final Locale lang;
    private final Voice voice;
    private final Double rate;
    private final Double pitch;
    private final Double volume;

    private Utterance(Builder b) {
        text = Objects.requireNonNull(b.text, "text must not be null");
        lang = b.lang;
        voice = b.voice;
        rate = b.rate;
        pitch = b.pitch;
        volume = b.volume;
    }

    /**
     * @param text the text to speak
     * @return an utterance with browser defaults
     */
    public static Utterance of(String text) {
        return new Builder(text).build();
    }

    /**
     * @param text the text to speak
     * @return a builder for an utterance
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * @return a builder starting from this utterance
     */
    public Builder toBuilder() {
        return new Builder(text).lang(lang).voice(voice).rate(rate).pitch(pitch).volume(volume);
    }

    /** @return the text to speak */
    public String text() {
        return text;
    }

    /** @return the language, or {@code null} for the voice's or the page's language */
    public Locale lang() {
        return lang;
    }

    /** @return the voice, or {@code null} for the browser's choice for the language */
    public Voice voice() {
        return voice;
    }

    /** @return speaking rate, 0.1 - 10, 1 is normal, or {@code null} */
    public Double rate() {
        return rate;
    }

    /** @return pitch, 0 - 2, 1 is normal, or {@code null} */
    public Double pitch() {
        return pitch;
    }

    /** @return volume, 0 - 1, or {@code null} */
    public Double volume() {
        return volume;
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

    @Override
    public boolean equals(Object o) {
        return o instanceof Utterance other && text.equals(other.text) && Objects.equals(lang, other.lang)
                && Objects.equals(voice, other.voice) && Objects.equals(rate, other.rate)
                && Objects.equals(pitch, other.pitch) && Objects.equals(volume, other.volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, lang, voice, rate, pitch, volume);
    }

    @Override
    public String toString() {
        return "Utterance[text=" + text + ", lang=" + lang + ", voice=" + voice + ", rate=" + rate + ", pitch="
                + pitch + ", volume=" + volume + "]";
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
            this.text = Objects.requireNonNull(text, "text must not be null");
        }

        /**
         * @param lang the language, or {@code null} for the voice's or the
         *             page's language
         * @return this builder
         */
        public Builder lang(Locale lang) {
            this.lang = lang;
            return this;
        }

        /**
         * @param voice the voice, or {@code null} for the browser's choice
         * @return this builder
         */
        public Builder voice(Voice voice) {
            this.voice = voice;
            return this;
        }

        /**
         * @param rate speaking rate, 0.1 - 10, 1 is normal, or {@code null}
         * @return this builder
         */
        public Builder rate(Double rate) {
            this.rate = rate;
            return this;
        }

        /**
         * @param pitch pitch, 0 - 2, 1 is normal, or {@code null}
         * @return this builder
         */
        public Builder pitch(Double pitch) {
            this.pitch = pitch;
            return this;
        }

        /**
         * @param volume volume, 0 - 1, or {@code null}
         * @return this builder
         */
        public Builder volume(Double volume) {
            this.volume = volume;
            return this;
        }

        /**
         * @return the utterance
         */
        public Utterance build() {
            return new Utterance(this);
        }
    }
}
