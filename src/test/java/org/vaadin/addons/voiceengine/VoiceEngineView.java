package org.vaadin.addons.voiceengine;

import static org.vaadin.voiceengine.VoiceEngine.Buttons.LANG;
import static org.vaadin.voiceengine.VoiceEngine.Buttons.VOICE;

import org.vaadin.voiceengine.VoiceEngine;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.aura.Aura;

@Route("")
@StyleSheet(Aura.STYLESHEET)
public class VoiceEngineView extends Div {

    enum State {
        READY("Ready"),
        RECORDING("● Listening… speak now, then press Stop"),
        SPEAKING("Speaking…");

        final String description;

        State(String description) {
            this.description = description;
        }
    }

    private State state = State.READY;

    private final VoiceEngine voiceEngine = new VoiceEngine();
    private final TextArea textArea = new TextArea("Text") {{
        setSizeFull();
        setPlaceholder("Recognized speech appears here. You can also type text to be spoken.");
    }};
    private final ActionButton recordButton = new ActionButton("Record", VaadinIcon.MICROPHONE,
            "Start speech recognition. Speak, then press Stop to finish.", voiceEngine::start) {{
        addThemeVariants(ButtonVariant.LUMO_ERROR);
    }};
    private final ActionButton stopButton = new ActionButton("Stop", VaadinIcon.STOP,
            "Stop recording (the recognized text is written to the text area) or stop speaking",
            this::stop);
    private final ActionButton speakButton = new ActionButton("Speak", VaadinIcon.PLAY,
            "Read the text aloud using the selected language and voice", voiceEngine::play);
    private final Span status = new Span();

    public VoiceEngineView() {
        setSizeFull();

        // Custom buttons with texts and tooltips, calling the VoiceEngine API,
        // followed by the built-in language and voice selects
        voiceEngine.add(recordButton, stopButton, speakButton,
                new ActionButton("Clear", VaadinIcon.CLOSE_SMALL, "Clear the text area", textArea::clear));
        voiceEngine.setButtons(LANG, VOICE);

        add(new VerticalLayout(
                new H2("Voice Engine demo"),
                new Paragraph("Choose a language, press Record and speak: the recognized text is "
                        + "written to the text area. Press Speak to have the browser read the text "
                        + "aloud with the selected voice."),
                voiceEngine,
                status,
                textArea));

        // Keep the UI in sync with what the browser is actually doing
        voiceEngine.addStartListener(e -> {
            textArea.clear();
            setState(State.RECORDING);
        });
        voiceEngine.addEndListener(e -> {
            // Set the recorded text to the TextArea
            if (voiceEngine.getRecorded() != null) {
                textArea.setValue(voiceEngine.getRecorded());
            }
            setState(State.READY);
        });
        voiceEngine.addErrorListener(e -> setState(State.READY));
        voiceEngine.addSpeakStartListener(e -> setState(State.SPEAKING));
        voiceEngine.addSpeakEndListener(e -> setState(State.READY));
        textArea.addValueChangeListener(e -> voiceEngine.setSpeech(e.getValue()));

        setState(State.READY);
    }

    private void stop() {
        if (state == State.RECORDING) {
            voiceEngine.stop();
        } else {
            voiceEngine.cancel();
        }
    }

    private void setState(State state) {
        this.state = state;
        recordButton.setEnabled(state == State.READY);
        speakButton.setEnabled(state == State.READY);
        stopButton.setEnabled(state != State.READY);
        status.setText(state.description);
    }

    static class ActionButton extends Button {
        ActionButton(String text, VaadinIcon icon, String tooltip, Runnable action) {
            super(text, icon.create(), e -> action.run());
            setTooltipText(tooltip);
        }
    }
}
