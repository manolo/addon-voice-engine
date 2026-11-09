package org.vaadin.voiceengine.ui;

import java.util.Locale;
import java.util.Objects;

import org.vaadin.voiceengine.SpeechSynthesis;
import org.vaadin.voiceengine.SynthesisErrorCode;
import org.vaadin.voiceengine.Utterance;
import org.vaadin.voiceengine.Voice;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.function.SerializableSupplier;

/**
 * A button that reads text aloud using speech synthesis. Click again while
 * speaking to stop.
 *
 * <pre>{@code
 * add(new ReadAloudButton(() -> article.getText()));
 * add(new ReadAloudButton(textArea));
 * }</pre>
 */
public class ReadAloudButton extends Button {

    private final SerializableSupplier<String> text;
    private Locale lang;
    private Voice voice;
    private Double rate;
    private boolean speaking;
    private String idleText = "Read aloud";
    private String activeText = "Stop reading";

    /**
     * @param text supplies the text to read when the button is clicked
     */
    public ReadAloudButton(SerializableSupplier<String> text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        addClickListener(e -> toggle());
        updateLook();
    }

    /**
     * @param source the field whose value is read
     */
    public ReadAloudButton(HasValue<?, String> source) {
        this(source::getValue);
    }

    private void toggle() {
        if (speaking) {
            SpeechSynthesis.cancel();
            return;
        }
        String value = text.get();
        if (value == null || value.isBlank()) {
            return;
        }
        Utterance.Builder utterance = Utterance.builder(value).lang(lang).voice(voice);
        if (rate != null) {
            utterance.rate(rate);
        }
        setSpeaking(true);
        SpeechSynthesis.speak(utterance.build(), () -> setSpeaking(false), error -> {
            setSpeaking(false);
            if (error.code() == SynthesisErrorCode.SYNTHESIS_UNAVAILABLE) {
                setEnabled(false);
                setTooltipText("Speech synthesis is not supported by this browser");
            }
        });
    }

    private void setSpeaking(boolean speaking) {
        this.speaking = speaking;
        updateLook();
    }

    private void updateLook() {
        setText(speaking ? activeText : idleText);
        setIcon((speaking ? VaadinIcon.STOP : VaadinIcon.PLAY).create());
        setTooltipText(speaking ? "Stop reading" : "Read the text aloud");
    }

    /**
     * @param lang the language, or {@code null} for the voice's or the page's
     *             language
     */
    public void setLang(Locale lang) {
        this.lang = lang;
    }

    /**
     * @param voice the voice, or {@code null} for the browser's choice
     */
    public void setVoice(Voice voice) {
        this.voice = voice;
    }

    /**
     * @param rate speaking rate, 0.1 - 10, 1 is normal, or {@code null} for
     *             default
     */
    public void setRate(Double rate) {
        this.rate = rate;
    }

    /**
     * Sets the button texts.
     *
     * @param idleText   text when not speaking, default "Read aloud"
     * @param activeText text when speaking, default "Stop reading"
     */
    public void setTexts(String idleText, String activeText) {
        this.idleText = idleText;
        this.activeText = activeText;
        updateLook();
    }
}
