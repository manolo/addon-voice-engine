package org.vaadin.webspeech.ui;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.vaadin.webspeech.RecognitionErrorCode;
import org.vaadin.webspeech.RecognitionOptions;
import org.vaadin.webspeech.SpeechRecognition;
import org.vaadin.webspeech.SpeechSupport;
import org.vaadin.webspeech.TriggerMode;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * A button that dictates text into a field using speech recognition. Click to
 * start, click again to stop, Escape to cancel. The field is updated in the
 * browser while the user speaks, and the final text is set to it on the
 * server when dictation ends.
 *
 * <pre>{@code
 * TextArea notes = new TextArea("Notes");
 * add(notes, new DictationButton(notes));
 * }</pre>
 * <p>
 * The button keeps listening until clicked again; for a single request (a
 * search box, a command), use {@link #setContinuous(boolean) setContinuous(false)}.
 * <p>
 * The browser starts listening directly when the button is pressed, without a
 * server round trip. With {@link #setTriggerMode(TriggerMode) TriggerMode.HOLD},
 * it works as push-to-talk. For more control, use {@link #getRecognition()},
 * e.g. for a {@link SpeechRecognition#setServerFallback server fallback}.
 */
public class DictationButton extends Button {

    private final HasValue<?, String> target;
    private final SpeechRecognition recognition;
    private TriggerMode triggerMode = TriggerMode.TOGGLE;
    private boolean append = true;
    private boolean iconOnly;
    private List<ButtonVariant> activeVariants = List.of(ButtonVariant.ERROR);
    private String idleText = "Dictate";
    private String activeText = "Stop dictation";
    private String textBefore = "";

    /**
     * Creates a dictation button that keeps listening until stopped, shows
     * the text in the field while the user speaks, and stops speech synthesis
     * when dictation starts.
     *
     * @param target the field to write the recognized text to
     */
    public DictationButton(HasValue<?, String> target) {
        this(target, RecognitionOptions.builder().continuous(true).interimResults(true).cancelSpeechOnStart(true)
                .build());
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
        if (target instanceof Component) {
            // Updated in the browser, only final results go to the server
            recognition.setLiveTarget(target, append);
            recognition.setInterimResultsToServer(false);
        } else {
            recognition.addActiveChangeListener(active -> {
                if (active) {
                    String current = target.getValue();
                    textBefore = append && current != null ? current : "";
                }
            });
            recognition.addResultListener(result -> updateTarget());
            recognition.addEndListener(transcript -> updateTarget());
        }
        recognition.addActiveChangeListener(active -> updateLook());
        recognition.addErrorListener(error -> {
            if (error.code() == RecognitionErrorCode.RECOGNITION_UNAVAILABLE) {
                markUnsupported();
            }
        });
        recognition.bindTrigger(this, triggerMode);
        updateLook();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        SpeechRecognition.whenSupportKnown(attachEvent.getUI(), support -> {
            if (support == SpeechSupport.UNSUPPORTED && recognition.getServerFallback() == null) {
                markUnsupported();
            }
        });
    }

    private void markUnsupported() {
        setEnabled(false);
        setTooltipText("Speech recognition is not supported by this browser");
    }

    private void updateTarget() {
        String text = Stream.of(textBefore, recognition.getTranscript(), recognition.getInterimTranscript())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        target.setValue(text);
    }

    private void updateLook() {
        boolean active = recognition.isActive();
        String text = active ? activeText : idleText;
        if (iconOnly) {
            setText(null);
            setAriaLabel(text);
        } else {
            setText(text);
            setAriaLabel(null);
        }
        setIcon((active ? VaadinIcon.STOP : VaadinIcon.MICROPHONE).create());
        activeVariants.forEach(variant -> {
            if (active) {
                addThemeVariants(variant);
            } else {
                removeThemeVariants(variant);
            }
        });
        if (isEnabled()) {
            setTooltipText(active ? "Stop listening, Escape to cancel"
                    : triggerMode == TriggerMode.HOLD ? "Hold down and speak" : "Dictate text using your microphone");
        }
    }

    /**
     * @param lang the language to recognize, or {@code null} for the browser's
     *             language. Takes effect on the next start.
     */
    public void setLang(Locale lang) {
        recognition.setOptions(recognition.getOptions().toBuilder().lang(lang).build());
    }

    /**
     * By default the button keeps listening until it is clicked again, for
     * dictating longer text. For a single request, e.g. in a search box or a
     * command field, use {@code false}: the session ends when the user pauses,
     * and an {@link SpeechRecognition#addEndListener end listener} can act on
     * the text.
     *
     * <pre>{@code
     * DictationButton mic = new DictationButton(search);
     * mic.setContinuous(false);
     * mic.getRecognition().addEndListener(text -> runSearch(search.getValue()));
     * }</pre>
     *
     * @param continuous {@code true} (default) to listen until stopped,
     *                   {@code false} to stop after the first phrase. Takes
     *                   effect on the next start.
     */
    public void setContinuous(boolean continuous) {
        recognition.setOptions(recognition.getOptions().toBuilder().continuous(continuous).build());
    }

    /**
     * @param append {@code true} (default) to add the dictated text after the
     *               existing value, {@code false} to replace the value
     */
    public void setAppend(boolean append) {
        this.append = append;
        if (target instanceof Component) {
            recognition.setLiveTarget(target, append);
        }
    }

    /**
     * @param triggerMode {@link TriggerMode#TOGGLE} (default) to start and
     *                    stop with clicks, {@link TriggerMode#HOLD} for
     *                    push-to-talk
     */
    public void setTriggerMode(TriggerMode triggerMode) {
        this.triggerMode = Objects.requireNonNull(triggerMode, "triggerMode must not be null");
        recognition.bindTrigger(this, triggerMode);
        updateLook();
    }

    /**
     * Shows only the icon, e.g. for a button in a field's suffix. The texts
     * are still used as the accessible name.
     *
     * @param iconOnly {@code true} to hide the text
     */
    public void setIconOnly(boolean iconOnly) {
        this.iconOnly = iconOnly;
        updateLook();
    }

    /**
     * Sets the theme variants used while listening, by default
     * {@link ButtonVariant#ERROR}. Call without arguments to keep the button's
     * own look while listening.
     *
     * @param variants the variants
     */
    public void setActiveVariants(ButtonVariant... variants) {
        if (recognition.isActive()) {
            activeVariants.forEach(this::removeThemeVariants);
        }
        activeVariants = List.of(variants);
        updateLook();
    }

    /**
     * Sets the button texts. With {@link #setIconOnly(boolean)} they are the
     * accessible name.
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
