package org.vaadin.webspeech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecordingTest {

    private static String fileName(String contentType) {
        return new Recording(new ByteArrayInputStream(new byte[0]), contentType, null, List.of()).fileName();
    }

    @Test
    void fileNameMatchesContentType() {
        assertEquals("recording.webm", fileName("audio/webm;codecs=opus"));
        assertEquals("recording.mp4", fileName("audio/mp4"));
        assertEquals("recording.ogg", fileName("audio/ogg; codecs=opus"));
        assertEquals("recording.mp3", fileName("audio/mpeg"));
        assertEquals("recording.wav", fileName("audio/x-wav"));
        assertEquals("recording.webm", fileName("application/octet-stream"));
    }
}
