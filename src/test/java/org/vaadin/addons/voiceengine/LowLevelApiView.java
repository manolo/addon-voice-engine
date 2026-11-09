package org.vaadin.addons.voiceengine;

import java.util.Locale;

import org.vaadin.voiceengine.RecognitionOptions;
import org.vaadin.voiceengine.SpeechRecognition;
import org.vaadin.voiceengine.SpeechSynthesis;
import org.vaadin.voiceengine.SynthesisState;
import org.vaadin.voiceengine.Utterance;
import org.vaadin.voiceengine.ui.LanguageSelect;
import org.vaadin.voiceengine.ui.VoiceSelect;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;

/**
 * Uses {@link SpeechRecognition} and {@link SpeechSynthesis} directly, with own
 * buttons whose enabled state follows what the browser is doing.
 */
@Route("low-level")
// Verifies that the add-on keeps working when a reload reuses the same UI
@PreserveOnRefresh
@PageTitle("Low-level API")
public class LowLevelApiView extends VerticalLayout {

    private final SpeechRecognition recognition = SpeechRecognition.create(this);

    private final TextArea text = new TextArea("Text") {{
        setWidthFull();
        setMinHeight("8em");
        setPlaceholder("Recognized speech appears here. You can also type text to be spoken.");
    }};
    private final Pre log = new Pre() {{
        setWidthFull();
        setMaxHeight("16em");
        getStyle().setOverflow(com.vaadin.flow.dom.Style.Overflow.AUTO);
    }};
    private final Span status = new Span();
    private final LanguageSelect languageSelect = new LanguageSelect();
    private final VoiceSelect voiceSelect = new VoiceSelect();
    private final Checkbox continuous = new Checkbox("Continuous");
    private final Checkbox interim = new Checkbox("Interim results", true);

    private final ActionButton recordButton = new ActionButton("Record", VaadinIcon.MICROPHONE,
            "Start speech recognition", this::record) {{
        addThemeVariants(ButtonVariant.ERROR);
    }};
    private final ActionButton stopButton = new ActionButton("Stop", VaadinIcon.STOP,
            "Stop recognition (results so far are kept) or stop speaking", this::stop);
    private final ActionButton abortButton = new ActionButton("Abort", VaadinIcon.CLOSE_CIRCLE,
            "Abort recognition, discarding audio not yet recognized", recognition::abort);
    private final ActionButton speakButton = new ActionButton("Speak", VaadinIcon.PLAY,
            "Speak the text with the selected language and voice", this::speak);
    private final ActionButton listenOnceButton = new ActionButton("Listen once", VaadinIcon.COMMENT,
            "SpeechRecognition.listenOnce: recognize a single phrase", this::listenOnce);

    public LowLevelApiView() {
        add(new Paragraph("SpeechRecognition and SpeechSynthesis used directly. "
                + "Button states follow the listeners, the log shows the raw events."));
        add(new HorizontalLayout(languageSelect, voiceSelect, continuous, interim) {{
            setWrap(true);
            setAlignItems(FlexComponent.Alignment.BASELINE);
        }});
        add(new HorizontalLayout(recordButton, stopButton, abortButton, speakButton, listenOnceButton,
                new ActionButton("Clear", VaadinIcon.CLOSE_SMALL, "Clear text and log", () -> {
                    text.clear();
                    log.setText("");
                })) {{
            setWrap(true);
        }});
        add(status, text, log);

        languageSelect.addValueChangeListener(e -> voiceSelect.setLanguage(e.getValue()));

        recognition.addActiveChangeListener(active -> {
            log("recognition active: " + active);
            updateState();
        });
        recognition.addResultListener(result -> {
            log("result #" + result.index() + (result.isFinal() ? " final" : " interim") + ": "
                    + result.transcript() + " (confidence " + result.confidence() + ")");
            text.setValue(join(recognition.getTranscript(), recognition.getInterimTranscript()));
        });
        recognition.addErrorListener(error -> log("recognition error: " + error));
        recognition.addEndListener(transcript -> {
            log("recognition end: \"" + transcript + "\"");
            text.setValue(transcript);
        });
        SpeechSynthesis.addStateChangeListener(this, state -> {
            log("synthesis state: " + state);
            updateState();
        });
        updateState();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Support is detected asynchronously, the voices arrive after it
        SpeechSynthesis.getVoices(attachEvent.getUI(), voices -> log("support: synthesis="
                + SpeechSynthesis.getSupport() + ", recognition=" + SpeechRecognition.getSupport()
                + ", " + voices.size() + " voices"));
    }

    private void record() {
        recognition.setOptions(RecognitionOptions.builder()
                .lang(languageSelect.getValue())
                .continuous(continuous.getValue())
                .interimResults(interim.getValue())
                .build());
        recognition.start();
    }

    private void stop() {
        if (recognition.isActive()) {
            recognition.stop();
        } else {
            SpeechSynthesis.cancel();
        }
    }

    private void speak() {
        SpeechSynthesis.speak(Utterance.builder(text.getValue())
                        .lang(languageSelect.getValue())
                        .voice(voiceSelect.getValue())
                        .build(),
                () -> log("utterance ended"),
                error -> log("utterance error: " + error));
    }

    private void listenOnce() {
        Locale lang = languageSelect.getValue();
        SpeechRecognition.listenOnce(this, RecognitionOptions.builder().lang(lang).build(),
                phrase -> {
                    log("listenOnce: \"" + phrase + "\"");
                    text.setValue(phrase);
                },
                error -> log("listenOnce error: " + error));
    }

    private void updateState() {
        boolean recording = recognition.isActive();
        SynthesisState synthesis = SpeechSynthesis.getState();
        boolean idle = !recording && synthesis == SynthesisState.IDLE;
        recordButton.setEnabled(idle);
        speakButton.setEnabled(idle);
        listenOnceButton.setEnabled(idle);
        stopButton.setEnabled(!idle);
        abortButton.setEnabled(recording);
        status.setText(recording ? "● Listening… speak now, then press Stop"
                : synthesis == SynthesisState.IDLE ? "Ready" : "Synthesis: " + synthesis);
    }

    private void log(String line) {
        log.setText(line + "\n" + log.getText());
    }

    private static String join(String a, String b) {
        return (a + " " + b).trim();
    }

    static class ActionButton extends Button {
        ActionButton(String text, VaadinIcon icon, String tooltip, Runnable action) {
            super(text, icon.create(), e -> action.run());
            setTooltipText(tooltip);
        }
    }
}
