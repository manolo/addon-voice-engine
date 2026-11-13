package org.vaadin.webspeech.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.vaadin.webspeech.SpeechSynthesis;
import org.vaadin.webspeech.Voice;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.select.Select;

/**
 * Select for choosing a language from the languages that have a speech
 * synthesis voice in the user's browser. Preselects the UI's locale if
 * available.
 */
public class LanguageSelect extends Select<Locale> {

    public LanguageSelect() {
        this("Language");
    }

    /**
     * @param label the label
     */
    public LanguageSelect(String label) {
        setLabel(label);
        setItemLabelGenerator(l -> l.getDisplayName(getLocale()) + " (" + l.toLanguageTag() + ")");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        SpeechSynthesis.getVoices(attachEvent.getUI(), voices -> {
            Locale uiLocale = attachEvent.getUI().getLocale();
            List<Locale> languages = voices.stream().map(Voice::locale).distinct()
                    .sorted(Comparator.comparing(l -> l.getDisplayName(uiLocale)))
                    .toList();
            Locale previous = getValue();
            setItems(languages);
            if (previous != null && languages.contains(previous)) {
                setValue(previous);
            } else {
                languages.stream().filter(uiLocale::equals).findFirst()
                        .or(() -> languages.stream()
                                .filter(l -> l.getLanguage().equals(uiLocale.getLanguage())).findFirst())
                        .ifPresent(this::setValue);
            }
        });
    }
}
