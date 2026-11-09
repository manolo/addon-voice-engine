package org.vaadin.voiceengine.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.vaadin.voiceengine.SpeechSynthesis;
import org.vaadin.voiceengine.Voice;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.select.Select;

/**
 * Select for choosing a speech synthesis voice from the voices available in the
 * user's browser. Optionally limited to a language with
 * {@link #setLanguage(Locale)}, e.g. from a {@link LanguageSelect}.
 */
public class VoiceSelect extends Select<Voice> {

    private List<Voice> allVoices = List.of();
    private Locale language;

    public VoiceSelect() {
        this("Voice");
    }

    /**
     * @param label the label
     */
    public VoiceSelect(String label) {
        setLabel(label);
        setItemLabelGenerator(v -> language == null ? v.name() + " (" + v.lang() + ")" : v.name());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        SpeechSynthesis.getVoices(attachEvent.getUI(), voices -> {
            allVoices = voices.stream().sorted(Comparator.comparing(Voice::name)).toList();
            refresh();
        });
    }

    /**
     * Shows only voices for the given language. The language matches voices
     * with the same language tag, or the same language if there are none.
     *
     * @param language the language, or {@code null} for all voices
     */
    public void setLanguage(Locale language) {
        this.language = language;
        refresh();
    }

    /**
     * @return the voices available in the browser, empty until loaded
     */
    public List<Voice> getAllVoices() {
        return allVoices;
    }

    private void refresh() {
        List<Voice> voices = allVoices;
        if (language != null) {
            String tag = language.toLanguageTag();
            voices = allVoices.stream().filter(v -> v.lang().equalsIgnoreCase(tag)).toList();
            if (voices.isEmpty()) {
                voices = allVoices.stream()
                        .filter(v -> v.locale().getLanguage().equals(language.getLanguage())).toList();
            }
        }
        Voice previous = getValue();
        setItems(voices);
        if (previous != null && voices.contains(previous)) {
            setValue(previous);
        } else if (!voices.isEmpty()) {
            setValue(voices.stream().filter(Voice::defaultVoice).findFirst().orElse(voices.get(0)));
        }
    }
}
