package org.vaadin.webspeech.demo;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;

/**
 * End-to-end checks of the demo views in headless Chromium. Speech itself
 * cannot be verified headless (no voices, no recognition service), but the
 * whole Java - module - browser API - event - Java chain can: the browser
 * reports failures or end events, and the UI reacts to them.
 * <p>
 * Run with {@code mvn verify -Pit}. Set {@code -Dheadless=false} to watch.
 */
class DemoViewsIT {

    private static final String BASE = "http://localhost:8080/";

    /**
     * A scripted SpeechRecognition (driven with window.fakeRecognition.say)
     * and a speechSynthesis that records what is spoken in window.spoken.
     */
    private static final String FAKE_SPEECH = """
            (() => {
              class FakeRecognition {
                constructor() { this.list = []; }
                start() {
                  window.fakeRecognition = this;
                  setTimeout(() => { this.onstart && this.onstart(); this.onaudiostart && this.onaudiostart(); });
                }
                say(text, isFinal) {
                  const last = this.list[this.list.length - 1];
                  const index = last && !last.isFinal ? this.list.length - 1 : this.list.length;
                  this.list[index] = Object.assign([{ transcript: text, confidence: 0.9 }], { isFinal });
                  this.onresult({ resultIndex: index, results: this.list });
                }
                stop() { setTimeout(() => this.onend()); }
                abort() { setTimeout(() => { this.onerror({ error: 'aborted', message: '' }); this.onend(); }); }
              }
              window.SpeechRecognition = FakeRecognition;
              window.webkitSpeechRecognition = FakeRecognition;
              window.spoken = [];
              speechSynthesis.speak = (u) => {
                window.spoken.push(u.text);
                setTimeout(() => { u.onstart && u.onstart(); u.onend && u.onend(); });
              };
            })();
            """;

    /** Removes speech recognition, like in Firefox. */
    private static final String NO_RECOGNITION = """
            delete window.SpeechRecognition;
            delete window.webkitSpeechRecognition;
            """;

    private static Playwright playwright;
    private static Browser browser;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(!"false".equals(System.getProperty("headless")))
                .setArgs(List.of("--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream")));
    }

    @AfterAll
    static void close() {
        playwright.close();
    }

    @BeforeEach
    void newPage() {
        page = browser.newPage();
        page.onPageError(pageErrors::add);
        page.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                pageErrors.add(m.text());
            }
        });
    }

    @AfterEach
    void noErrors() {
        page.close();
        assertEquals(List.of(), pageErrors, "JavaScript errors on the page");
    }

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator log() {
        return page.locator("pre");
    }

    @Test
    void lowLevelApi_moduleLoadsAndSupportIsDetected() {
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText(Pattern.compile("support: synthesis=SUPPORTED, recognition=SUPPORTED"));
    }

    @Test
    void lowLevelApi_synthesisEventsReachServer() {
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("support:");
        page.locator("vaadin-text-area textarea").fill("Hello from the test");
        page.locator("vaadin-text-area textarea").blur();
        button("Speak").click();
        // Headless Chromium has no voices, so the utterance fails, but either
        // way the browser's event must arrive and the state return to idle
        assertThat(log()).containsText(Pattern.compile("utterance (ended|error)"));
        assertThat(log()).containsText("synthesis state: IDLE");
        assertThat(button("Speak")).isEnabled();
    }

    @Test
    void lowLevelApi_recognitionStateDrivesButtons() {
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("support:");
        button("Record").click();
        assertThat(log()).containsText("recognition active: true");
        assertThat(button("Record")).isDisabled();
        assertThat(button("Stop")).isEnabled();

        button("Stop").click();
        assertThat(log()).containsText("recognition end");
        assertThat(button("Record")).isEnabled();
        assertThat(button("Stop")).isDisabled();
    }

    @Test
    void lowLevelApi_worksAfterReloadWithPreservedUi() {
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("support:");
        page.reload();
        // Same UI instance, so the log survives, but the page and its module are new
        assertThat(log()).containsText("support:");
        button("Record").click();
        assertThat(log()).containsText("recognition active: true");
        button("Stop").click();
        assertThat(log()).containsText("recognition end");
        assertThat(button("Record")).isEnabled();
    }

    @Test
    void dictationView_rendersComponents() {
        page.navigate(BASE);
        assertThat(button("Dictate")).isVisible();
        assertThat(button("Read aloud")).isVisible();
        assertTrue(page.locator("vaadin-select").count() >= 2);
    }

    @Test
    void dictationView_buttonTogglesWhileListening() {
        page.navigate(BASE);
        button("Dictate").click();
        assertThat(button("Stop dictation")).isVisible();
        button("Stop dictation").click();
        assertThat(button("Dictate")).isVisible();
    }

    @Test
    void dictationView_worksInModalDialog() {
        page.navigate(BASE);
        button("Dictate in a modal dialog").click();
        Locator dialog = page.locator("vaadin-dialog");
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dictate")).click();
        Locator stop = dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Stop dictation"));
        assertThat(stop).isVisible();
        stop.click();
        // The end event from the browser must get through although the UI is inert
        assertThat(dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dictate"))).isVisible();
    }

    private void say(String text, boolean isFinal) {
        page.waitForFunction("() => !!window.fakeRecognition");
        page.evaluate("([t, f]) => window.fakeRecognition.say(t, f)", List.of(text, isFinal));
    }

    private Locator textArea() {
        return page.locator("vaadin-text-area").first();
    }

    @Test
    void dictation_textAppearsLiveAndReachesServer() {
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE);
        textArea().locator("textarea").fill("Before");
        textArea().locator("textarea").blur();
        button("Dictate").click();
        assertThat(button("Stop dictation")).isVisible();

        say("hello wor", false);
        assertThat(textArea().locator("textarea")).hasValue("Before hello wor");
        say("hello world", true);
        say("again", false);
        assertThat(textArea().locator("textarea")).hasValue("Before hello world again");

        button("Stop dictation").click();
        assertThat(button("Dictate")).isVisible();
        // The interim "again" is dropped, the final text is the server's value
        assertThat(textArea().locator("textarea")).hasValue("Before hello world");
        button("Read aloud").click();
        page.waitForFunction("() => window.spoken.includes('Before hello world')");
    }

    @Test
    void dictation_escapeRestoresField() {
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE);
        textArea().locator("textarea").fill("Keep me");
        textArea().locator("textarea").blur();
        button("Dictate").click();
        say("discard this", true);
        assertThat(textArea().locator("textarea")).hasValue("Keep me discard this");

        page.keyboard().press("Escape");
        assertThat(button("Dictate")).isVisible();
        assertThat(textArea().locator("textarea")).hasValue("Keep me");
        button("Read aloud").click();
        page.waitForFunction("() => window.spoken.includes('Keep me')");
    }

    @Test
    void dictation_pushToTalk() {
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE);
        Locator field = page.locator("vaadin-text-field");
        Locator mic = field.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Hold to dictate"));
        mic.hover();
        page.mouse().down();
        say("pushed", true);
        assertThat(field.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Listening")))
                .isVisible();
        page.mouse().up();
        assertThat(mic).isVisible();
        assertThat(field.locator("input")).hasValue("pushed");
    }

    @Test
    void dictation_replacingFieldIsRestored() {
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE);
        Locator field = page.locator("vaadin-text-field");
        Locator input = field.locator("input");
        Locator mic = field.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Hold to dictate"));
        input.fill("kept");
        input.blur();

        // A press without speech leaves the value as it was
        mic.hover();
        page.mouse().down();
        page.waitForFunction("() => !!window.fakeRecognition");
        // Wait for the round trip, so the session has really ended before the next press
        assertThat(field.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Listening")))
                .isVisible();
        page.mouse().up();
        assertThat(mic).isVisible();
        assertThat(input).hasValue("kept");

        // Escape restores the original value, also against late results
        page.evaluate("() => window.fakeRecognition = null");
        page.mouse().down();
        say("replacement", false);
        assertThat(input).hasValue("replacement");
        page.keyboard().press("Escape");
        assertThat(mic).isVisible();
        page.evaluate("() => window.fakeRecognition.say('late', true)");
        assertThat(input).hasValue("kept");
        page.mouse().up();
    }

    @Test
    void lowLevelApi_phasesAndInterimResults() {
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("support:");
        button("Record").click();
        assertThat(log()).containsText("recognition phase: AUDIO_START");
        say("one", false);
        assertThat(log()).containsText("result #0 interim: one");
        say("one two", true);
        assertThat(log()).containsText("result #0 final: one two");
        button("Stop").click();
        assertThat(log()).containsText("recognition end: \"one two\"");
    }

    @Test
    void lowLevelApi_serverFallbackWithoutRecognition() {
        page.addInitScript(NO_RECOGNITION);
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("recognition=UNSUPPORTED");
        button("Record").click();
        assertThat(log()).containsText("recognition active: true");
        page.waitForTimeout(1500);
        button("Stop").click();
        assertThat(log()).containsText(Pattern.compile("result #0 final: server fallback received \\d+ bytes of audio/\\S+ as recording\\.\\w+ in \\w+"));
        assertThat(log()).containsText("recognition end");
    }

    @Test
    void lowLevelApi_onDeviceCheckDoesNotCrashAutomatedBrowser() {
        // available() crashes the tab in Playwright's Chromium, so the add-on
        // reports UNAVAILABLE in automated browsers without calling it
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("support:");
        button("On-device?").click();
        assertThat(log()).containsText("on-device availability for null: UNAVAILABLE");
        engineSelect("PREFER_ON_DEVICE");
        button("Record").click();
        assertThat(log()).containsText("recognition active: true");
        button("Stop").click();
        assertThat(log()).containsText("recognition end");
    }

    @Test
    void lowLevelApi_serverEnginesSkipBrowserRecognition() {
        // The browser could recognize, but these engines never use its cloud
        // service: no on-device recognition in an automated browser, so both
        // go to the server fallback
        page.addInitScript(FAKE_SPEECH);
        page.navigate(BASE + "low-level");
        assertThat(log()).containsText("recognition=SUPPORTED");
        List<String> engines = List.of("ON_DEVICE_OR_SERVER", "SERVER");
        for (int i = 1; i <= engines.size(); i++) {
            engineSelect(engines.get(i - 1));
            button("Record").click();
            assertThat(log()).containsText(Pattern.compile("(recognition active: true[\\s\\S]*){" + i + "}"));
            page.waitForTimeout(1000);
            button("Stop").click();
            assertThat(log()).containsText(
                    Pattern.compile("(result #0 final: server fallback received \\d+ bytes[\\s\\S]*){" + i + "}"));
            assertThat(log()).containsText(Pattern.compile("(recognition end[\\s\\S]*){" + i + "}"));
        }
        assertEquals(Boolean.FALSE, page.evaluate("() => !!window.fakeRecognition"));
    }

    private void engineSelect(String value) {
        page.locator("vaadin-select").filter(new Locator.FilterOptions().setHasText("Engine")).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(value).setExact(true)).click();
    }

    @Test
    void legacyReplica_rendersOldToolbar() {
        page.navigate(BASE + "legacy");
        Locator toolbar = page.locator(".voice-engine");
        // MICROPHONE, RECORD, PLAY, STOP + the view's own clear button
        assertThat(toolbar.locator("vaadin-button")).hasCount(5);
        // LANG and VOICE
        assertThat(toolbar.locator("vaadin-select")).hasCount(2);
    }
}
