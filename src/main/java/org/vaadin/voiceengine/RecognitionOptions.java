package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Options for {@link SpeechRecognition}, created with {@link #builder()}.
 * Immutable.
 *
 * <pre>{@code
 * RecognitionOptions options = RecognitionOptions.builder()
 *         .lang(Locale.of("fi", "FI"))
 *         .continuous(true)
 *         .interimResults(true)
 *         .engine(RecognitionEngine.PREFER_ON_DEVICE)
 *         .build();
 * }</pre>
 */
public final class RecognitionOptions implements Serializable {

    /** Browser language, single phrase, final results only, browser's engine. */
    public static final RecognitionOptions DEFAULT = new Builder().build();

    private final Locale lang;
    private final boolean continuous;
    private final boolean interimResults;
    private final int maxAlternatives;
    private final RecognitionEngine engine;
    private final List<String> phrases;
    private final boolean cancelSpeechOnStart;

    private RecognitionOptions(Builder b) {
        if (b.maxAlternatives < 1) {
            throw new IllegalArgumentException("maxAlternatives must be at least 1");
        }
        lang = b.lang;
        continuous = b.continuous;
        interimResults = b.interimResults;
        maxAlternatives = b.maxAlternatives;
        engine = Objects.requireNonNull(b.engine, "engine must not be null");
        phrases = List.copyOf(b.phrases);
        cancelSpeechOnStart = b.cancelSpeechOnStart;
    }

    /**
     * @return a builder starting from {@link #DEFAULT}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder starting from these options
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the language to recognize, or {@code null} for the browser's
     *         language
     */
    public Locale lang() {
        return lang;
    }

    /**
     * @return {@code true} to keep listening until stopped, {@code false} to
     *         stop after the first phrase
     */
    public boolean continuous() {
        return continuous;
    }

    /**
     * @return {@code true} to also report results that may still change while
     *         the user speaks
     */
    public boolean interimResults() {
        return interimResults;
    }

    /**
     * @return maximum number of alternatives per result, at least 1
     */
    public int maxAlternatives() {
        return maxAlternatives;
    }

    /**
     * @return where speech may be recognized
     */
    public RecognitionEngine engine() {
        return engine;
    }

    /**
     * @return words and phrases recognition should favour, e.g. names and
     *         product codes, empty by default
     */
    public List<String> phrases() {
        return phrases;
    }

    /**
     * @return {@code true} to stop speech synthesis when recognition starts,
     *         so the user can talk over what is being read aloud
     */
    public boolean cancelSpeechOnStart() {
        return cancelSpeechOnStart;
    }

    ObjectNode toJson() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        if (lang != null) {
            json.put("lang", lang.toLanguageTag());
        }
        json.put("continuous", continuous);
        json.put("interimResults", interimResults);
        json.put("maxAlternatives", maxAlternatives);
        json.put("engine", engine.jsName());
        ArrayNode phraseArray = json.putArray("phrases");
        phrases.forEach(phraseArray::add);
        json.put("cancelSpeech", cancelSpeechOnStart);
        return json;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RecognitionOptions other && Objects.equals(lang, other.lang)
                && continuous == other.continuous && interimResults == other.interimResults
                && maxAlternatives == other.maxAlternatives && engine == other.engine
                && phrases.equals(other.phrases) && cancelSpeechOnStart == other.cancelSpeechOnStart;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lang, continuous, interimResults, maxAlternatives, engine, phrases, cancelSpeechOnStart);
    }

    @Override
    public String toString() {
        return "RecognitionOptions[lang=" + lang + ", continuous=" + continuous + ", interimResults="
                + interimResults + ", maxAlternatives=" + maxAlternatives + ", engine=" + engine + ", phrases="
                + phrases + ", cancelSpeechOnStart=" + cancelSpeechOnStart + "]";
    }

    /**
     * Builder for {@link RecognitionOptions}.
     */
    public static final class Builder implements Serializable {
        private Locale lang;
        private boolean continuous;
        private boolean interimResults;
        private int maxAlternatives = 1;
        private RecognitionEngine engine = RecognitionEngine.ANY;
        private List<String> phrases = List.of();
        private boolean cancelSpeechOnStart;

        private Builder() {
        }

        private Builder(RecognitionOptions from) {
            lang = from.lang;
            continuous = from.continuous;
            interimResults = from.interimResults;
            maxAlternatives = from.maxAlternatives;
            engine = from.engine;
            phrases = from.phrases;
            cancelSpeechOnStart = from.cancelSpeechOnStart;
        }

        /**
         * @param lang the language to recognize, or {@code null} for the
         *             browser's language
         * @return this builder
         */
        public Builder lang(Locale lang) {
            this.lang = lang;
            return this;
        }

        /**
         * @param continuous {@code true} to keep listening until stopped,
         *                   {@code false} (default) to stop after the first
         *                   phrase
         * @return this builder
         */
        public Builder continuous(boolean continuous) {
            this.continuous = continuous;
            return this;
        }

        /**
         * @param interimResults {@code true} to also report results that may
         *                       still change while the user speaks
         * @return this builder
         */
        public Builder interimResults(boolean interimResults) {
            this.interimResults = interimResults;
            return this;
        }

        /**
         * @param maxAlternatives maximum number of alternatives per result, at
         *                        least 1 (default)
         * @return this builder
         */
        public Builder maxAlternatives(int maxAlternatives) {
            this.maxAlternatives = maxAlternatives;
            return this;
        }

        /**
         * @param engine where speech may be recognized, default
         *               {@link RecognitionEngine#ANY}
         * @return this builder
         */
        public Builder engine(RecognitionEngine engine) {
            this.engine = Objects.requireNonNull(engine, "engine must not be null");
            return this;
        }

        /**
         * Words and phrases that recognition should favour (contextual
         * biasing), e.g. customer names or product codes. A hint: browsers
         * that do not know the feature ignore it. Browsers that know it but
         * cannot apply it (e.g. Chrome with cloud recognition) fail with
         * {@link RecognitionErrorCode#PHRASES_NOT_SUPPORTED}, so combine it with
         * {@link RecognitionEngine#ON_DEVICE_ONLY} or handle that error.
         *
         * @param phrases the phrases, empty (default) for none
         * @return this builder
         */
        public Builder phrases(List<String> phrases) {
            this.phrases = List.copyOf(phrases);
            return this;
        }

        /**
         * @param cancelSpeechOnStart {@code true} to stop speech synthesis
         *                            when recognition starts (barge-in)
         * @return this builder
         */
        public Builder cancelSpeechOnStart(boolean cancelSpeechOnStart) {
            this.cancelSpeechOnStart = cancelSpeechOnStart;
            return this;
        }

        /**
         * @return the options
         */
        public RecognitionOptions build() {
            return new RecognitionOptions(this);
        }
    }
}
