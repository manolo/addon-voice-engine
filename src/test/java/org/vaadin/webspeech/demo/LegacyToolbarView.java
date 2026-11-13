package org.vaadin.webspeech.demo;

import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.LANG;
import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.MICROPHONE;
import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.PLAY;
import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.RECORD;
import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.STOP;
import static org.vaadin.webspeech.demo.LegacyVoiceEngine.Buttons.VOICE;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoIcon;

/**
 * The 1.x/2.x example view, unchanged except that it uses
 * {@link LegacyVoiceEngine}, a replica of the removed 1.x/2.x component built on
 * the 3.0 API.
 */
@Route("legacy")
@PageTitle("1.x/2.x toolbar replica")
public class LegacyToolbarView extends Div {

    public LegacyToolbarView() {
        setSizeFull();

        // Create the Voice Engine component, enabling the mic for recording
        // play button for playing the text in the TextArea, language and voice
        // selection
        LegacyVoiceEngine voiceEngine = new LegacyVoiceEngine().setButtons(MICROPHONE, RECORD, PLAY, STOP, LANG, VOICE);
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
