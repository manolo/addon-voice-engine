package org.vaadin.addons.voiceengine;

import org.vaadin.voiceengine.ui.DictationButton;
import org.vaadin.voiceengine.ui.LanguageSelect;
import org.vaadin.voiceengine.ui.ReadAloudButton;
import org.vaadin.voiceengine.ui.VoiceSelect;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
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
        add(new Button("Dictate in a modal dialog", e -> new DictationDialog().open()));

        languageSelect.addValueChangeListener(e -> {
            voiceSelect.setLanguage(e.getValue());
            dictationButton.setLang(e.getValue());
            readAloudButton.setLang(e.getValue());
        });
        voiceSelect.addValueChangeListener(e -> readAloudButton.setVoice(e.getValue()));
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
