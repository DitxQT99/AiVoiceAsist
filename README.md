# VoxMind AI — Android native source

Kotlin + Jetpack Compose starter: visible user-started recording, local Room history, Indonesian transcription, AI summary/chat, search, TXT/PDF export.

## Build
1. Extract ZIP and open `VoxMindAI` in Android Studio.
2. Use JDK 17 and wait for Gradle Sync.
3. Run on an Android 8.0+ device. Grant microphone permission.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Debug APK usually appears at `app/build/outputs/apk/debug/app-debug.apk`.

## API setup
Open Settings in the app and enter a valid API key. Default base URL: `https://api.openai.com/v1/`. Defaults: transcription `gpt-4o-mini-transcribe`, text `gpt-4.1-mini`. Check current model access for your account.
This direct API-key approach is for personal testing only: keys embedded/stored in a mobile app can be extracted. For public distribution, put the API calls behind your own authenticated backend and keep the provider key on the server.

## Behavior / limitations
- Recording starts only after the user taps Start while the activity is visible and grants microphone permission.
- A foreground notification remains visible during recording; Android may still stop a service due to device policy, battery restrictions, microphone conflicts, or system conditions.
- Audio is saved locally. AI upload occurs only after the user explicitly presses “Proses dengan AI”.
- Transcription is post-recording, not live streaming. Very long audio may exceed upload limits/timeouts.
- API errors are shown as errors; the app does not fabricate transcription or AI responses.
- Get consent before recording other people. The audio is uploaded to the configured provider when processing is requested.
