package org.vaadin.addons.voiceengine;

import static org.vaadin.voiceengine.VoiceEngine.Buttons.LANG;
import static org.vaadin.voiceengine.VoiceEngine.Buttons.MICROPHONE;
import static org.vaadin.voiceengine.VoiceEngine.Buttons.PLAY;
import static org.vaadin.voiceengine.VoiceEngine.Buttons.VOICE;

import org.vaadin.voiceengine.VoiceEngine;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoIcon;

@Route("")
public class VoiceEngineView extends Div {

    public VoiceEngineView() {
        setSizeFull();

        // Create the Voice Engine component, enabling the mic for recording
        // play button for playing the text in the TextArea, language and voice
        // selection
        VoiceEngine voiceEngine = new VoiceEngine().setButtons(MICROPHONE, PLAY, LANG, VOICE);
        // Additional Button added to the VoiceEngine for clearing the TextArea
        Button clearButton = new Button(LumoIcon.CROSS.create());
        voiceEngine.add(clearButton);
        // The area for the text to be spoken
        TextArea textArea = new TextArea();
        textArea.setSizeFull();
        // Add the components to the view
        add(new VerticalLayout(voiceEngine, textArea));

        // Configure listeners
        clearButton.addClickListener(e -> textArea.clear());
        voiceEngine.addStartListener(e -> textArea.clear());
        textArea.addValueChangeListener(e -> voiceEngine.setSpeech(e.getValue()));
        voiceEngine.addEndListener(e -> {
            // Set the recorded text to the TextArea
            if (voiceEngine.getRecorded() != null) {
                textArea.setValue(voiceEngine.getRecorded());
            }
        });
    }
}
