package org.vaadin.addons.voiceengine;

import org.vaadin.voiceengine.VoiceEngine;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoIcon;
import static org.vaadin.voiceengine.VoiceEngine.Buttons.*;

@Route("")
public class VoiceEngineView extends Div {

    public VoiceEngineView() {
        setSizeFull();

        Button clearButton = new Button(LumoIcon.CROSS.create());
        VoiceEngine voiceEngine = new VoiceEngine().setButtons(MICROPHONE, PLAY, LANG, VOICE);
        voiceEngine.add(clearButton);
        TextArea textArea = new TextArea();

        textArea.setSizeFull();
        add(new VerticalLayout(voiceEngine, textArea));

        clearButton.addClickListener(e -> textArea.clear());
        voiceEngine.addStartListener(e -> textArea.clear());
        textArea.addValueChangeListener(e -> voiceEngine.setSpeech(e.getValue()));

        voiceEngine.addEndListener(e -> {
            if (voiceEngine.getRecorded() != null) {
                textArea.setValue(voiceEngine.getRecorded());
            }
            if (!textArea.getValue().isEmpty()) {
                voiceEngine.setSpeech(voiceEngine.getRecorded());
            }
        });
    }
}
