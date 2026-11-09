package org.vaadin.voiceengine.ui;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.vaadin.voiceengine.RecognitionErrorCode;
import org.vaadin.voiceengine.RecognitionOptions;
import org.vaadin.voiceengine.SpeechRecognition;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * A button that dictates text into a field using speech recognition. Click to
 * start, click again to stop. The field is updated live while speaking.
 *
 * <pre>{@code
 * TextArea notes = new TextArea("Notes");
 * add(notes, new DictationButton(notes));
 * }</pre>
 * <p>
 * For more control, use {@link #getRecognition()} or {@link SpeechRecognition}
 * directly.
 */
public class DictationButton extends Button {

    private final HasValue<?, String> target;
    private final SpeechRecognition recognition;
    private boolean append = true;
    private String idleText = "Dictate";
    private String activeText = "Stop dictation";
    private String textBefore = "";

    /**
     * Creates a dictation button that keeps listening until stopped and shows
     * interim results in the field.
     *
     * @param target the field to write the recognized text to
     */
    public DictationButton(HasValue<?, String> target) {
        this(target, RecognitionOptions.builder().continuous(true).interimResults(true).build());
    }

    /**
     * Creates a dictation button with the given recognition options.
     *
     * @param target  the field to write the recognized text to
     * @param options the recognition options
     */
    public DictationButton(HasValue<?, String> target, RecognitionOptions options) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        recognition = SpeechRecognition.create(this, options);
        recognition.addResultListener(result -> updateTarget());
        recognition.addEndListener(transcript -> updateTarget());
        recognition.addActiveChangeListener(active -> updateLook());
        recognition.addErrorListener(error -> {
            if (error.code() == RecognitionErrorCode.RECOGNITION_UNAVAILABLE) {
                setEnabled(false);
                setTooltipText("Speech recognition is not supported by this browser");
            }
        });
        addClickListener(e -> toggle());
        updateLook();
    }

    private void toggle() {
        if (recognition.isActive()) {
            recognition.stop();
        } else {
            String current = target.getValue();
            textBefore = append && current != null ? current : "";
            recognition.start();
        }
    }

    private void updateTarget() {
        String text = Stream.of(textBefore, recognition.getTranscript(), recognition.getInterimTranscript())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        target.setValue(text);
    }

    private void updateLook() {
        boolean active = recognition.isActive();
        setText(active ? activeText : idleText);
        setIcon((active ? VaadinIcon.STOP : VaadinIcon.MICROPHONE).create());
        if (active) {
            addThemeVariants(ButtonVariant.ERROR);
        } else {
            removeThemeVariants(ButtonVariant.ERROR);
        }
        setTooltipText(active ? "Stop listening" : "Dictate text using your microphone");
    }

    /**
     * @param lang the language to recognize, or {@code null} for the browser's
     *             language. Takes effect on the next start.
     */
    public void setLang(Locale lang) {
        recognition.setOptions(recognition.getOptions().toBuilder().lang(lang).build());
    }

    /**
     * @param append {@code true} (default) to add the dictated text after the
     *               existing value, {@code false} to replace the value
     */
    public void setAppend(boolean append) {
        this.append = append;
    }

    /**
     * Sets the button texts.
     *
     * @param idleText   text when not listening, default "Dictate"
     * @param activeText text when listening, default "Stop dictation"
     */
    public void setTexts(String idleText, String activeText) {
        this.idleText = idleText;
        this.activeText = activeText;
        updateLook();
    }

    /**
     * @return the underlying recognition session, e.g. for adding listeners
     */
    public SpeechRecognition getRecognition() {
        return recognition;
    }
}
