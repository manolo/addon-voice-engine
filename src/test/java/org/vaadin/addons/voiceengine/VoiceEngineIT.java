package org.vaadin.addons.voiceengine;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.testbench.TestBenchElement;

public class VoiceEngineIT extends AbstractViewTest {

    @Test
    public void addonIsRendered() {
        waitUntil(driver -> $("voice-engine").exists());

        TestBenchElement element = $("voice-engine").first();
        Assert.assertNotNull(element);

        ButtonElement microphone = element.$(ButtonElement.class).first();
        Assert.assertNotNull(microphone);
    }
}
