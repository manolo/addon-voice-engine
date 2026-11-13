package org.vaadin.webspeech.demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.vaadin.webspeech.RecognitionOptions;
import org.vaadin.webspeech.SpeechRecognition;
import org.vaadin.webspeech.SpeechSynthesis;
import org.vaadin.webspeech.Utterance;
import org.vaadin.webspeech.Voice;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;

/**
 * Replica of the 1.x/2.x {@code org.vaadin.voiceengine.VoiceEngine} component,
 * built on the 3.0 API. Verifies that everything the old component did can
 * still be done, and serves as a migration example. Not part of the add-on.
 */
public class LegacyVoiceEngine extends Div {

    public enum Buttons {
        RECORD, PLAY, MICROPHONE, STOP, CANCEL, LANG, VOICE
    }

    private final SpeechRecognition recognition = SpeechRecognition.create(this);
    private Map<String, List<Voice>> voices = new LinkedHashMap<>();
    private String lang = Locale.getDefault().toLanguageTag();
    private String voice;
    private String speech = "";
    private String recorded;
    private boolean continuous;
    private boolean localService = true;
    private Select<String> langSelect;
    private Select<String> voiceSelect;

    public LegacyVoiceEngine() {
        addClassNames("voice-engine");
        getStyle().setDisplay(Style.Display.INLINE_FLEX).setFlexWrap(Style.FlexWrap.WRAP)
                .setAlignItems(Style.AlignItems.CENTER).setGap("var(--vaadin-gap-s, 0.5rem)");
        recognition.addActiveChangeListener(active -> {
            if (active) {
                fireEvent(new StartEvent(this));
            }
        });
        recognition.addResultListener(r -> {
            recorded = recognition.getTranscript();
            fireEvent(new ResultEvent(this));
        });
        recognition.addErrorListener(e -> fireEvent(new ErrorEvent(this)));
        recognition.addEndListener(transcript -> {
            recorded = transcript;
            fireEvent(new EndEvent(this));
        });
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (attachEvent.isInitialAttach()) {
            if (attachEvent.getUI().getLocale() != null) {
                lang = attachEvent.getUI().getLocale().toLanguageTag();
            }
            SpeechSynthesis.getVoices(attachEvent.getUI(), list -> {
                voices = new LinkedHashMap<>();
                list.stream()
                        // 1.x/2.x preferred local voices (setLocalService)
                        .sorted(Comparator.comparing((Voice v) -> v.localService() != localService))
                        .forEach(v -> voices.computeIfAbsent(v.lang(), k -> new ArrayList<>()).add(v));
                selectVoiceForLang();
                fireEvent(new VoiceChangedEvent(this));
            });
        }
    }

    public LegacyVoiceEngine setButtons(Buttons... buttons) {
        for (Buttons button : buttons) {
            switch (button) {
                case RECORD -> add(new Button(VaadinIcon.CIRCLE.create(), e -> start()) {{
                    addThemeVariants(ButtonVariant.ERROR);
                }});
                case MICROPHONE -> add(new Button(VaadinIcon.MICROPHONE.create(), e -> start()) {{
                    addThemeVariants(ButtonVariant.ERROR);
                }});
                case PLAY -> add(new Button(VaadinIcon.PLAY.create(), e -> play()));
                case STOP -> add(new Button(VaadinIcon.PAUSE.create(), e -> stop()));
                case CANCEL -> add(new Button(VaadinIcon.STOP.create(), e -> cancel()));
                case LANG -> {
                    langSelect = new Select<>();
                    langSelect.setWidth("6em");
                    add(langSelect);
                    addVoiceChangedListener(e -> {
                        langSelect.setItems(voices.keySet().stream().sorted().toList());
                        langSelect.setValue(getLang());
                    });
                    langSelect.addValueChangeListener(e -> {
                        if (e.isFromClient() && e.getValue() != null) {
                            setLang(e.getValue());
                        }
                    });
                }
                case VOICE -> {
                    voiceSelect = new Select<>();
                    add(voiceSelect);
                    addVoiceChangedListener(e -> {
                        voiceSelect.setItems(voices.getOrDefault(getLang(), List.of()).stream()
                                .map(Voice::name).toList());
                        voiceSelect.setValue(getVoice());
                    });
                    voiceSelect.addValueChangeListener(e -> {
                        if (e.isFromClient() && e.getValue() != null) {
                            setVoice(e.getValue());
                        }
                    });
                }
            }
        }
        return this;
    }

    public LegacyVoiceEngine setContinuous(boolean continuous) {
        this.continuous = continuous;
        return this;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public LegacyVoiceEngine setLocalService(boolean localService) {
        this.localService = localService;
        return this;
    }

    public String getSpeech() {
        return speech;
    }

    public void setSpeech(String value) {
        speech = value == null ? "" : value;
    }

    public String getRecorded() {
        return recorded;
    }

    public LegacyVoiceEngine setLang(String val) {
        lang = val.replace("_", "-");
        selectVoiceForLang();
        fireEvent(new VoiceChangedEvent(this));
        return this;
    }

    public LegacyVoiceEngine setLang(Locale val) {
        return setLang(val.toLanguageTag());
    }

    public String getLang() {
        return lang;
    }

    public String getVoice() {
        return voice;
    }

    public LegacyVoiceEngine setVoice(String val) {
        voice = val;
        fireEvent(new VoiceChangedEvent(this));
        return this;
    }

    public Map<String, List<String>> getVoices() {
        Map<String, List<String>> names = new LinkedHashMap<>();
        voices.forEach((l, list) -> names.put(l, list.stream().map(Voice::name).toList()));
        return names;
    }

    /** Starts recording, or stops it if already recording (as in 1.x/2.x). */
    public void start() {
        SpeechSynthesis.cancel();
        if (recognition.isActive()) {
            recognition.stop();
        } else {
            recognition.setOptions(RecognitionOptions.builder()
                    .lang(Locale.forLanguageTag(lang)).continuous(continuous).build());
            recognition.start();
        }
    }

    public void stop() {
        recognition.stop();
        SpeechSynthesis.pause();
    }

    public void cancel() {
        recognition.abort();
        SpeechSynthesis.cancel();
    }

    public void play() {
        SpeechSynthesis.cancel();
        if (!speech.isEmpty()) {
            SpeechSynthesis.speak(Utterance.builder(speech).lang(Locale.forLanguageTag(lang))
                    .voice(currentVoice()).build());
        }
    }

    public void play(String text) {
        setSpeech(text);
        play();
    }

    private Voice currentVoice() {
        return voices.values().stream().flatMap(List::stream)
                .filter(v -> v.name().equals(voice)).findFirst().orElse(null);
    }

    /** Picks the selected voice if it fits the language, else the first one for it (as in 1.x/2.x). */
    private void selectVoiceForLang() {
        List<Voice> forLang = voices.getOrDefault(lang, List.of());
        if (forLang.isEmpty()) {
            String language = lang.split("-")[0];
            forLang = voices.entrySet().stream().filter(e -> e.getKey().split("-")[0].equals(language))
                    .flatMap(e -> e.getValue().stream()).toList();
        }
        if (forLang.isEmpty()) {
            forLang = voices.getOrDefault("en-US", List.of());
        }
        if (forLang.stream().noneMatch(v -> v.name().equals(voice))) {
            voice = forLang.isEmpty() ? null : forLang.get(0).name();
        }
    }

    public Registration addStartListener(ComponentEventListener<StartEvent> listener) {
        return addListener(StartEvent.class, listener);
    }

    public Registration addEndListener(ComponentEventListener<EndEvent> listener) {
        return addListener(EndEvent.class, listener);
    }

    public Registration addErrorListener(ComponentEventListener<ErrorEvent> listener) {
        return addListener(ErrorEvent.class, listener);
    }

    public Registration addResultListener(ComponentEventListener<ResultEvent> listener) {
        return addListener(ResultEvent.class, listener);
    }

    public Registration addVoiceChangedListener(ComponentEventListener<VoiceChangedEvent> listener) {
        return addListener(VoiceChangedEvent.class, listener);
    }

    public static class StartEvent extends ComponentEvent<LegacyVoiceEngine> {
        public StartEvent(LegacyVoiceEngine source) {
            super(source, true);
        }
    }

    public static class EndEvent extends ComponentEvent<LegacyVoiceEngine> {
        public EndEvent(LegacyVoiceEngine source) {
            super(source, true);
        }
    }

    public static class ErrorEvent extends ComponentEvent<LegacyVoiceEngine> {
        public ErrorEvent(LegacyVoiceEngine source) {
            super(source, true);
        }
    }

    public static class ResultEvent extends ComponentEvent<LegacyVoiceEngine> {
        public ResultEvent(LegacyVoiceEngine source) {
            super(source, true);
        }
    }

    public static class VoiceChangedEvent extends ComponentEvent<LegacyVoiceEngine> {
        public VoiceChangedEvent(LegacyVoiceEngine source) {
            super(source, true);
        }
    }
}
