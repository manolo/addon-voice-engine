# Voice Addon

An addon for voice recognition and speach text

## Example


```java
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
```
