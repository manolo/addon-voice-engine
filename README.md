# WebSpeech for Vaadin

Speech recognition (speech-to-text) and speech synthesis (text-to-speech) for
Vaadin Flow, using the browser's
[Web Speech API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API).

- A typed Java API that mirrors the browser API:
  [`SpeechRecognition`](src/main/java/org/vaadin/webspeech/SpeechRecognition.java) and
  [`SpeechSynthesis`](src/main/java/org/vaadin/webspeech/SpeechSynthesis.java).
- Ready-made components for the common cases: `DictationButton`,
  `ReadAloudButton`, `LanguageSelect` and `VoiceSelect`.
- No frontend build impact: the add-on has no `@JsModule` or npm dependency,
  so adding it does not require a custom frontend bundle.

Requires Vaadin 25.

```xml
<dependency>
    <groupId>org.parttio</groupId>
    <artifactId>web-speech</artifactId>
    <version>3.0.0</version>
</dependency>
```

WebSpeech continues the [Voice Engine add-on](https://vaadin.com/directory/component/voice-engine)
by Manolo Carrasco Moñino (`org.vaadin.addons.manolo:voice-engine`, versions
1.x and 2.x). Version 3.0 was renamed when its API was rewritten, see
[Migrating from Voice Engine](#migrating-from-voice-engine-1x2x).

## Dictate into a field, read text aloud

```java
TextArea notes = new TextArea("Notes");
add(notes, new DictationButton(notes), new ReadAloudButton(notes));
```

Tap `DictationButton` to start and tap again to stop, or hold it down while
speaking and release to stop (`TriggerMode.TAP_OR_HOLD`). Escape cancels and
restores the field. The browser starts listening right in the press handler
and writes the text into the field while the user speaks; only final results
go to the server. For push-to-talk only, in a field's suffix:

```java
TextField field = new TextField();
field.setSuffixComponent(new DictationButton(field) {{
    setTriggerMode(TriggerMode.HOLD);   // listen while held down
    setIconOnly(true);
    setTexts("Hold to dictate", "Listening");
    addThemeVariants(ButtonVariant.TERTIARY);
    setActiveVariants();                // keep the own look while listening
    getRecognition().bindKey(Key.F8, TriggerMode.HOLD);  // also while F8 is held, anywhere on the page
}});
```

After a tap, the button listens until tapped again, which suits dictating
longer text.

The server gets the dictated text when the session ends, so a button that acts
on the field's value should finish the session first. `finishBefore` stops the
session on a click and replays the click once the server has the text. Enter
in a single-line field does the same automatically:

```java
TextField query = new TextField();
DictationButton mic = new DictationButton(query);
Button search = new Button("Search", e -> search(query.getValue()));
mic.getRecognition().finishBefore(search);
query.addKeyDownListener(Key.ENTER, e -> search(query.getValue()));
```

For a single request, such as a search box or a command, let a pause end
the session and act on the text in an end listener. The field already has its
final value there:

```java
TextField goal = new TextField();
DictationButton mic = new DictationButton(goal);
mic.setContinuous(false);   // stop after the first phrase
mic.getRecognition().addEndListener(text -> runGoal(goal.getValue()));
```

To pick the language and voice:

```java
LanguageSelect language = new LanguageSelect();   // languages with a voice, preselects the UI locale
VoiceSelect voice = new VoiceSelect();
language.addValueChangeListener(e -> {
    voice.setLanguage(e.getValue());
    dictationButton.setLang(e.getValue());
    readAloudButton.setLang(e.getValue());
});
voice.addValueChangeListener(e -> readAloudButton.setVoice(e.getValue()));
```

## Speech synthesis

The browser has one speech queue per page, so `SpeechSynthesis` is a static
facade that works on the current UI (overloads take the `UI` explicitly).

```java
SpeechSynthesis.speak("Hello world");

SpeechSynthesis.speak(Utterance.builder("Hei maailma")
                .lang(Locale.of("fi", "FI"))
                .rate(1.2)
                .build(),
        () -> Notification.show("Done"),
        error -> Notification.show("Failed: " + error.code()));

SpeechSynthesis.cancel();                 // also pause() and resume()
SpeechSynthesis.getVoices(voices -> ...); // List<Voice>
SpeechSynthesis.addStateChangeListener(this, state -> ...); // IDLE, SPEAKING, PAUSED
```

Browsers only allow speaking after the user has interacted with the page.
Before that, utterances fail with `SynthesisErrorCode.NOT_ALLOWED`. iOS Safari
is stricter: speech must first be started inside a user gesture. If you speak
later, e.g. when an AI agent's answer arrives, prime the button that starts it:

```java
SpeechSynthesis.prime(askButton);   // speaks a silent utterance on the first click
```

## Speech recognition

A recognition session is bound to an owner component: it is aborted
automatically when the owner detaches.

```java
SpeechRecognition recognition = SpeechRecognition.create(this,
        RecognitionOptions.builder()
                .lang(Locale.of("fi", "FI"))
                .continuous(true)        // keep listening until stop()
                .interimResults(true)    // also report results that may still change
                .build());

recognition.addResultListener(result -> preview.setText(
        recognition.getTranscript() + " " + recognition.getInterimTranscript()));
recognition.addEndListener(transcript -> textArea.setValue(transcript));
recognition.addErrorListener(error -> Notification.show("Recognition failed: " + error.code()));
recognition.addActiveChangeListener(active -> {
    startButton.setEnabled(!active);
    stopButton.setEnabled(active);
});

startButton.addClickListener(e -> recognition.start());
stopButton.addClickListener(e -> recognition.stop());   // abort() discards pending audio
```

For a single phrase:

```java
SpeechRecognition.listenOnce(this, text -> field.setValue(text), error -> { });
```

### Without server round trips

`start()` goes through the server, so listening begins only after a round
trip, and each interim result makes another one. For a responsive UI, let the
browser handle both:

```java
recognition.bindTrigger(micButton, TriggerMode.TOGGLE);  // or HOLD for push-to-talk; Escape aborts
recognition.setLiveTarget(textArea);                     // text written in the browser, final value set on the server at the end
recognition.setInterimResultsToServer(false);            // only final results to the server
```

`isActive()` and all listeners work the same for sessions started by the
trigger. `DictationButton` uses exactly this.

### More options and events

```java
RecognitionOptions.builder()
        .engine(RecognitionEngine.PREFER_ON_DEVICE)  // see "Where speech is processed"
        .lang(Locale.ENGLISH)                        // a region is added in the browser ("en-US"), Safari needs one
        .phrases(List.of("Vaadin", "SKU-1234"))      // contextual biasing: on the device and the server fallback
        .cancelSpeechOnStart(true)                   // stop reading aloud when the user starts talking
        .build();

recognition.addPhaseListener(phase -> ...);  // AUDIO_START, SPEECH_START, SPEECH_END, NO_MATCH, ...
SpeechRecognition.whenSupportKnown(ui, support -> mic.setVisible(support == SpeechSupport.SUPPORTED));
```

Phase events are only sent by the browser while there is a phase listener.

### Server fallback

The session can also record the audio and let your server transcribe it, with
e.g. Spring AI, OpenAI, Azure or a local Whisper. Reasons to set up such a
fallback:

- Browsers without usable speech recognition, such as Firefox for Android, and
  locked-down Chromium builds that have the API but no service behind it.
- A policy that audio must never go to the browser vendor's cloud: with
  `RecognitionEngine.ON_DEVICE_OR_SERVER` it is recognized on the device or on
  your server, see [Where speech is processed](#where-speech-is-processed).

```java
recognition.setServerFallback(recording -> transcriber.transcribe(recording.audio(), recording.lang()));
```

The `Recording` has the audio, its content type, the language and the
session's `phrases`, plus a `fileName()` with an extension matching the
format, which e.g. OpenAI's API needs. With Spring AI:

```java
recognition.setServerFallback(recording -> transcriptionModel.transcribe(
        new InputStreamResource(recording.audio()) {
            @Override
            public String getFilename() {
                return recording.fileName();
            }
        }));
```

The recording is uploaded when the session is stopped, and the text arrives as
a single final result. The transcriber runs in the upload request without the
session lock, so it can take its time but must not touch components.

With the default engine, the fallback is used only when the browser cannot
recognize speech. `RecognitionEngine.SERVER` always uses it.

## Browser support

Speech synthesis works in all modern browsers. Speech recognition works in
current Chrome, Edge, Safari and desktop Firefox, but not in Firefox for
Android or in Chromium builds without a recognition service. Check with
`SpeechRecognition.getSupport()` / `SpeechSynthesis.getSupport()`; they return
`UNKNOWN` until the browser has reported its capabilities, shortly after the
add-on is first used in a UI. When recognition is unavailable, sessions end
with `RecognitionErrorCode.RECOGNITION_UNAVAILABLE` and `DictationButton`
disables itself.

The available voices depend on the browser and the operating system.

## Where speech is processed

By default the browser decides where speech is recognized: Chrome sends the
audio to a Google service, Edge to a Microsoft one. Newer Chrome and Edge can
also recognize on the device, which keeps the audio private and works offline:

- `RecognitionEngine.ON_DEVICE_ONLY`: never leaves the device; fails if the
  language pack is not installed.
- `RecognitionEngine.PREFER_ON_DEVICE`: on the device when the language pack is
  installed, otherwise the browser's default (the vendor's cloud). A
  downloadable pack is downloaded for the next time.
- `RecognitionEngine.ON_DEVICE_OR_SERVER`: on the device when the language pack
  is installed, otherwise your [server fallback](#server-fallback), never the
  vendor's cloud. Also covers browsers without recognition. A downloadable pack is downloaded for
  the next time.
- `RecognitionEngine.SERVER`: always your server fallback.
- `SpeechRecognition.checkOnDeviceAvailability(lang, callback)` and
  `installOnDevice(lang, callback)` for building your own flow.

Only check availability in response to a user action: in Chromium builds
without the on-device service (such as the one Playwright uses), the browser's
check crashes the tab. The add-on therefore treats browsers controlled by
automation (`navigator.webdriver`) as having no on-device recognition.

For speech synthesis, `Voice.localService()` tells whether a voice runs on the
device or sends the text to a service.

## Testing without a browser

`SimulatedSpeech` plays the browser's part in unit tests and browserless
tests:

```java
SimulatedSpeech.start(dictationButton.getRecognition());   // as if the user pressed the button
SimulatedSpeech.finalResult(dictationButton.getRecognition(), "hello world");
SimulatedSpeech.end(dictationButton.getRecognition());
assertEquals("hello world", textArea.getValue());

assertEquals("Done.", SimulatedSpeech.getQueuedUtterances(ui).get(0).text());
SimulatedSpeech.finishUtterance(ui);
```

For end-to-end tests in a headless browser (e.g. Playwright), replace
`window.SpeechRecognition` with a fake in an init script; the add-on only uses
the standard API. The fake must call `onend` when stopped, and also on its own
after a final result when `continuous` is false, as the browser does. See
`FAKE_SPEECH` in
[`DemoViewsIT`](src/test/java/org/vaadin/webspeech/demo/DemoViewsIT.java).

## How it works

The client-side code is a small ES module (`speech.js`) shipped as a classpath
resource. It is served through a `DownloadHandler` on the UI element and loaded
on demand with a dynamic `import()`. Events from the browser are dispatched on
the UI element and routed to the right Java listeners. This keeps the add-on
free of frontend build dependencies and works the same in every deployment,
including with `@PreserveOnRefresh` and while a modal dialog is open.

### A candidate for Vaadin Flow

The add-on is deliberately built like the modern browser API wrappers that
have recently been added to Vaadin Flow itself, so that it can later be moved
into Flow, or serve as the basis for such an API:

- The Java API follows the browser API's names and concepts
  (`SpeechRecognition`, `SpeechSynthesis`, `Utterance`) with typed options,
  results and error codes instead of strings.
- It only depends on Flow's public API (`Element.executeJs`, element event
  listeners, `DownloadHandler`, `ElementRequestHandler`), with no web
  component, npm package or frontend build step.
- State is per UI and tied to component attach and detach, so nothing leaks
  between views or sessions.
- `SimulatedSpeech` gives a browserless testing story out of the box.

The ready-made components in `org.vaadin.webspeech.ui` are a separate layer on
top of that API and could stay in an add-on.

## Migrating from Voice Engine 1.x/2.x

Replace the `org.vaadin.addons.manolo:voice-engine` dependency with
`org.parttio:web-speech`; the Java package is now `org.vaadin.webspeech`. The
`VoiceEngine` component is replaced by the API above. Typical replacements:

| 1.x/2.x `VoiceEngine`                           | 3.0                                                         |
|-------------------------------------------------|-------------------------------------------------------------|
| `setButtons(MICROPHONE, PLAY, LANG, VOICE)`     | `DictationButton`, `ReadAloudButton`, `LanguageSelect`, `VoiceSelect` |
| `start()` / `stop()` / `cancel()`               | `SpeechRecognition#start()` / `stop()` / `abort()`, `SpeechSynthesis.cancel()` |
| `addEndListener` + `getRecorded()`              | `SpeechRecognition#addEndListener(transcript -> ...)`        |
| `addResultListener`                             | `SpeechRecognition#addResultListener(result -> ...)`         |
| `setSpeech(text)` + `play()`, `play(text)`      | `SpeechSynthesis.speak(text)` / `speak(Utterance)`           |
| `setLang(...)`, `setVoice(name)`                | `RecognitionOptions.lang`, `Utterance.lang` / `Utterance.voice` |
| `getVoices()`, `addVoiceChangedListener`        | `SpeechSynthesis.getVoices(callback)`                       |
| `setContinuous(true)`                           | `RecognitionOptions.continuous(true)`                       |

If you want to keep the old toolbar, the test sources contain
[`LegacyVoiceEngine`](src/test/java/org/vaadin/webspeech/demo/LegacyVoiceEngine.java),
a replica of the 1.x/2.x component built on the 3.0 API, which you can copy into
your project.

## Development

```
mvn jetty:run      # demos at http://localhost:8080
mvn verify -Pit    # integration tests with Playwright (headless Chromium)
```

The demo views are in `src/test/java`: typical use with the ready-made
components, the low-level API, and the 1.x/2.x toolbar replica.
