package org.vaadin.voiceengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Synchronize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.shared.Registration;

import elemental.json.JsonArray;

/**
 * VoiceEngine is a Vaadin wrapper component for the Web Recognition and
 * Speech API's
 */
@Tag("voice-engine")
@JsModule("./components/voice-engine.ts")
public class VoiceEngine extends Div {

  public enum Buttons {
    RECORD, PLAY, MICROPHONE, STOP, CANCEL, LANG, VOICE
  }

  Select<String> voiceSelect;

  public VoiceEngine() {
    addClassNames("voice-engine");
    getElement().getStyle().set("display", "inline-flex");
    getElement().getStyle().set("gap", "calc(var(--lumo-space-xs))");
  }

  /**
   * Creates a new VoiceEngine with the specified buttons.
   *
   * @param buttons The buttons to be added to the VoiceEngine.
   */
  public VoiceEngine setButtons(Buttons... buttons) {
    for (Buttons button : buttons) {
      switch (button) {
        case RECORD:
          Button recButton = new Button(VaadinIcon.CIRCLE.create());
          recButton.addClickListener(e -> start());
          recButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
          add(recButton);
          break;
        case PLAY:
          Button playButton = new Button(VaadinIcon.PLAY.create());
          playButton.addClickListener(e -> play());
          add(playButton);
          break;
        case MICROPHONE:
          Button micButton = new Button(VaadinIcon.MICROPHONE.create());
          micButton.addClickListener(e -> start());
          micButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
          add(micButton);
          break;
        case STOP:
          Button stopButton = new Button(VaadinIcon.PAUSE.create());
          stopButton.addClickListener(e -> stop());
          add(stopButton);
          break;
        case CANCEL:
          Button cancelButton = new Button(VaadinIcon.STOP.create());
          cancelButton.addClickListener(e -> cancel());
          add(cancelButton);
          break;
        case LANG:
          Select<String> langSelect = new Select<>();
          langSelect.setOverlayClassName("voice-engine-language-overlay");
          langSelect.setWidth("6em");
          add(langSelect);
          this.addVoiceChangedListener(e -> {
            langSelect.setItems(getVoices().keySet().stream().sorted().toList());
            langSelect.setValue(getLang());
            if (voiceSelect != null) {
              voiceSelect.setItems(getVoices().get(langSelect.getValue()));
              voiceSelect.setValue(getVoice());
            }
          });
          langSelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
              setLang(e.getValue());
              if (voiceSelect != null) {
                List<String> items = getVoices().get(langSelect.getValue());
                voiceSelect.setItems(items);
                if (getVoice() == null || !items.contains(getVoice())) {
                  setVoice(items.get(0));
                  voiceSelect.setValue(getVoice());
                } else {
                  voiceSelect.setValue(getVoice());
                }
              }
            }
          });
          break;
        case VOICE:
          voiceSelect = new Select<>();
          voiceSelect.setOverlayClassName("voice-engine-voice-overlay");
          add(voiceSelect);
          voiceSelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
              setVoice(e.getValue());
            }
          });
          break;
      }
    }
    return this;
  }

  /**
   * Sets whether the recognition should continue listening even if the user
   * pauses while speaking.
   *
   * @param continuous A boolean indicating whether recognition should be
   *                   continuous.
   */
  public VoiceEngine setContinuous(boolean continuous) {
    getElement().setProperty("continuous", continuous);
    return this;
  }

  /**
   * Returns whether the recognition is continuous.
   *
   * @return A boolean indicating whether recognition is continuous.
   */
  public boolean isContinuous() {
    return getElement().getProperty("continuous", true);
  }

  public VoiceEngine setLocalService(boolean localService) {
    getElement().setProperty("localService", localService);
    return this;
  }

  /**
   * Gets the text to speech
   *
   * @return A string containing the text to speech.
   */
  public String getSpeech() {
    return getElement().getProperty("speech", "");
  }

  /**
   * Sets the speech to speak.
   */
  public void setSpeech(String value) {
    getElement().setProperty("speech", value);
  }

  /**
   * Gets the text to speech
   *
   * @return A string containing the recorded speech.
   */
  @Synchronize("recorded")
  public String getRecorded() {
    return getElement().getProperty("recorded");
  }

  public VoiceEngine setLang(String val) {
    getElement().setProperty("lang", val.replace("_", "-"));
    return this;
  }

  @Synchronize("voice-changed")
  public String getVoice() {
    return getElement().getProperty("voice");
  }

  /**
   * Gets the available voices for speech synthesis.
   */
  @Synchronize("voice-changed")
  public Map<String, List<String>> getVoices() {
    Map<String, List<String>> voices = new HashMap<>();
    JsonArray a = (JsonArray) getElement().getPropertyRaw("voices");
    for (int i = 0; a != null && i < a.length(); i++) {
      String lang = a.getObject(i).getString("lang");
      voices.putIfAbsent(lang, new ArrayList<>());
      voices.get(lang).add(a.getObject(i).getString("name"));
    }
    return voices;
  }

  /**
   * Language used in record and syntesis
   * 
   * @return
   */
  @Synchronize("voice-changed")
  public String getLang() {
    return getElement().getProperty("lang");
  }

  /**
   * Language used in record and syntesis
   * 
   * @param val
   * @return this
   */
  public VoiceEngine setLang(Locale val) {
    setLang(val.toString());
    return this;
  }

  public VoiceEngine setVoice(String val) {
    getElement().setProperty("voice", val);
    return this;
  }

  /**
   * Starts the speech recognition process.
   */
  public void start() {
    cancel();
    getElement().callJsFunction("startRecording");
  }

  /**
   * Stops the speech recognition process.
   */
  public void stop() {
    getElement().callJsFunction("stopRecording");
  }

  /**
   * Aborts the speech recognition and speech processes.
   */
  public void cancel() {
    getElement().callJsFunction("cancel");
  }

  /**
   * Speak the speech string.
   */
  public void play() {
    getElement().callJsFunction("playSpeech");
  }

  /**
   * Speak the string.
   */
  public void play(String text) {
    setSpeech(text);
    play();
  }

  /**
   * Adds a listener for the start event.
   *
   * @param listener The listener to be added.
   * @return A registration for the listener.
   */
  public Registration addStartListener(ComponentEventListener<StartEvent> listener) {
    return getElement().addEventListener("start", e -> {
      listener.onComponentEvent(new StartEvent(this, true));
    });
  }

  /**
   * Adds a listener for the end event.
   *
   * @param listener The listener to be added.
   * @return A registration for the listener.
   */
  public Registration addEndListener(ComponentEventListener<EndEvent> listener) {
    return getElement().addEventListener("end", e -> {
      listener.onComponentEvent(new EndEvent(this, true));
    });
  }

  /**
   * Adds a listener for the error event.
   *
   * @param listener The listener to be added.
   * @return A registration for the listener.
   */
  public Registration addErrorListener(ComponentEventListener<ErrorEvent> listener) {
    return getElement().addEventListener("error", e -> {
      listener.onComponentEvent(new ErrorEvent(this, true));
    });
  }

  /**
   * Adds a listener for the result event, which is triggered when speech is
   * transcribed.
   * 
   * @param listener The listener to be added.
   * @return A registration for the listener.
   */
  public Registration addResultListener(ComponentEventListener<ResultEvent> listener) {
    return getElement().addEventListener("result", e -> {
      listener.onComponentEvent(new ResultEvent(this, true));
    });
  }

  /**
   * Adds a listener for the voice-changed event, which is triggered when the
   * voice engine
   * is ready, and when the language or voice is changed.
   * 
   * @param listener
   * @return
   */
  public Registration addVoiceChangedListener(ComponentEventListener<VoiceChangedEvent> listener) {
    return getElement().addEventListener("voice-changed", e -> {
      listener.onComponentEvent(new VoiceChangedEvent(this, true));
    });
  }

  /**
   * StartEvent is triggered when the speech recognition process starts.
   */
  public static class StartEvent extends ComponentEvent<VoiceEngine> {
    public StartEvent(VoiceEngine source, boolean fromClient) {
      super(source, fromClient);
    }
  }

  /**
   * ErrorEvent is triggered when an error occurs during the speech recognition
   * process.
   */
  public static class ErrorEvent extends ComponentEvent<VoiceEngine> {
    public ErrorEvent(VoiceEngine source, boolean fromClient) {
      super(source, fromClient);
    }
  }

  /**
   * EndEvent is triggered when the speech recognition process ends.
   */
  public static class EndEvent extends ComponentEvent<VoiceEngine> {
    public EndEvent(VoiceEngine source, boolean fromClient) {
      super(source, fromClient);
    }
  }

  /**
   * ResultEvent is triggered when speech is transcribed and contains the
   * transcribed text.
   */
  public static class ResultEvent extends ComponentEvent<VoiceEngine> {
    public ResultEvent(VoiceEngine source, boolean fromClient) {
      super(source, fromClient);
    }
  }

  /**
   * ReadyEvent is triggered when the voice engine is ready.
   */
  public static class VoiceChangedEvent extends ComponentEvent<VoiceEngine> {
    public VoiceChangedEvent(VoiceEngine source, boolean fromClient) {
      super(source, fromClient);
    }
  }

}