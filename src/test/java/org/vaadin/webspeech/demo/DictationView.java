package org.vaadin.webspeech.demo;

import org.vaadin.webspeech.SpeechSynthesis;
import org.vaadin.webspeech.TriggerMode;
import org.vaadin.webspeech.ui.DictationButton;
import org.vaadin.webspeech.ui.LanguageSelect;
import org.vaadin.webspeech.ui.ReadAloudButton;
import org.vaadin.webspeech.ui.VoiceSelect;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The typical use: dictate into a field and read text aloud, using the
 * ready-made components.
 */
@Route("")
@PageTitle("Dictation & read aloud")
public class DictationView extends VerticalLayout {

    private final TextArea text = new TextArea("Text") {{
        setWidthFull();
        setMinHeight("12em");
        setPlaceholder("Dictate or type text here");
    }};
    private final DictationButton dictationButton = new DictationButton(text);
    private final ReadAloudButton readAloudButton = new ReadAloudButton(text);
    private final LanguageSelect languageSelect = new LanguageSelect();
    private final VoiceSelect voiceSelect = new VoiceSelect();

    public DictationView() {
        add(new Paragraph("Press Dictate and speak: the text appears in the field while you speak. "
                + "Read aloud speaks the field's content with the selected voice. "
                + "The language is used for both."));
        add(new HorizontalLayout(languageSelect, voiceSelect) {{
            setWrap(true);
        }});
        add(new HorizontalLayout(dictationButton, readAloudButton) {{
            setWrap(true);
            setAlignItems(FlexComponent.Alignment.CENTER);
        }});
        add(text);
        add(new PushToTalkField());
        add(new SearchBar());
        add(new Button("Dictate in a modal dialog", e -> new DictationDialog().open()));
        // iOS Safari only speaks later if speech was started in a user gesture first
        SpeechSynthesis.prime(dictationButton);

        languageSelect.addValueChangeListener(e -> {
            voiceSelect.setLanguage(e.getValue());
            dictationButton.setLang(e.getValue());
            readAloudButton.setLang(e.getValue());
        });
        voiceSelect.addValueChangeListener(e -> readAloudButton.setVoice(e.getValue()));
    }

    /** An icon-only push-to-talk button in a field's suffix. */
    static class PushToTalkField extends TextField {
        PushToTalkField() {
            super("Push to talk");
            setWidthFull();
            setHelperText("Hold the microphone (or Space on it, or F8 anywhere) while speaking. "
                    + "The spoken text replaces the value, Escape cancels.");
            setSuffixComponent(new DictationButton(this) {{
                setTriggerMode(TriggerMode.HOLD);
                setAppend(false);
                setIconOnly(true);
                setTexts("Hold to dictate", "Listening");
                addThemeVariants(ButtonVariant.TERTIARY);
                setActiveVariants();
                getRecognition().bindKey(Key.F8, TriggerMode.HOLD);
            }});
        }
    }

    /**
     * A search field with a microphone: Search and Enter finish dictation
     * first, so the search sees the dictated text.
     */
    static class SearchBar extends HorizontalLayout {
        private final TextField query = new TextField("Search") {{
            setPlaceholder("Tap or hold the microphone, then Search or Enter");
            setWidthFull();
        }};
        private final DictationButton mic = new DictationButton(query) {{
            setIconOnly(true);
            setTexts("Dictate search", "Listening");
            addThemeVariants(ButtonVariant.TERTIARY);
        }};
        private final Button search = new Button("Search", e -> search());
        private final Span result = new Span() {{
            setId("search-result");
        }};

        SearchBar() {
            setWidthFull();
            setAlignItems(FlexComponent.Alignment.BASELINE);
            query.setSuffixComponent(mic);
            query.addKeyDownListener(Key.ENTER, e -> search());
            mic.getRecognition().finishBefore(search);
            add(query, search, result);
        }

        private void search() {
            result.setText(query.getValue().isBlank() ? "Type or say something first"
                    : "Searched for \"" + query.getValue() + "\"");
        }
    }

    /** Checks that speech works while a modal dialog makes the rest of the UI inert. */
    static class DictationDialog extends Dialog {
        DictationDialog() {
            setHeaderTitle("Modal dialog");
            TextArea dialogText = new TextArea("Text");
            add(dialogText, new DictationButton(dialogText));
            getFooter().add(new Button("Close", e -> close()));
        }
    }
}
