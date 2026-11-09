package org.vaadin.addons.voiceengine;

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
