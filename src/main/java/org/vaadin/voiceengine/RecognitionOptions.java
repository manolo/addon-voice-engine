package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.Locale;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Options for {@link SpeechRecognition}.
 *
 * @param lang            the language to recognize, or {@code null} for the
 *                        browser's language
 * @param continuous      {@code true} to keep listening until stopped,
 *                        {@code false} to stop after the first phrase
 * @param interimResults  {@code true} to also report results that may still
 *                        change while the user speaks
 * @param maxAlternatives maximum number of alternatives per result, at least 1
 */
public record RecognitionOptions(Locale lang, boolean continuous, boolean interimResults, int maxAlternatives)
        implements Serializable {

    /** Browser language, single phrase, final results only. */
    public static final RecognitionOptions DEFAULT = new RecognitionOptions(null, false, false, 1);

    public RecognitionOptions {
        if (maxAlternatives < 1) {
            throw new IllegalArgumentException("maxAlternatives must be at least 1");
        }
    }

    /**
     * @return a builder starting from {@link #DEFAULT}
     */
    public static Builder builder() {
        return new Builder(DEFAULT);
    }

    /**
     * @return a builder starting from these options
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    ObjectNode toJson() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        if (lang != null) {
            json.put("lang", lang.toLanguageTag());
        }
        json.put("continuous", continuous);
        json.put("interimResults", interimResults);
        json.put("maxAlternatives", maxAlternatives);
        return json;
    }

    /**
     * Builder for {@link RecognitionOptions}.
     */
    public static final class Builder implements Serializable {
        private Locale lang;
        private boolean continuous;
        private boolean interimResults;
        private int maxAlternatives;

        private Builder(RecognitionOptions from) {
            lang = from.lang;
            continuous = from.continuous;
            interimResults = from.interimResults;
            maxAlternatives = from.maxAlternatives;
        }

        public Builder lang(Locale lang) {
            this.lang = lang;
            return this;
        }

        public Builder continuous(boolean continuous) {
            this.continuous = continuous;
            return this;
        }

        public Builder interimResults(boolean interimResults) {
            this.interimResults = interimResults;
            return this;
        }

        public Builder maxAlternatives(int maxAlternatives) {
            this.maxAlternatives = maxAlternatives;
            return this;
        }

        public RecognitionOptions build() {
            return new RecognitionOptions(lang, continuous, interimResults, maxAlternatives);
        }
    }
}
