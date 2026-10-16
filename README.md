# 2VI Study

2VI Study is a free, no-login Android study tutor. It is an original learning
app, not affiliated with Gauth or any other study service.

## First release

- Ask questions in a text chat, with Math, Physics, Chemistry, Biology, and
  Other subject choices.
- Get step-by-step explanations and follow-up help in the same session.
- Tap **Talk** to ask one spoken question and hear the answer read aloud.
- Start a fresh chat at any time. No account, ads, subscriptions, API key, or
  locally saved chat history.

The voice tutor is turn-based rather than continuous: Android speech recognition
transcribes one spoken question, then Android text-to-speech reads the reply.
Speech recognition and text-to-speech availability and behavior depend on the
Android device's configured speech services.

## Free AI and privacy

The anonymous free text endpoint is provided by
[Pollinations](https://github.com/pollinations/pollinations/blob/master/APIDOCS.md).
It is best-effort, has no uptime guarantee, and currently limits text requests
to one at a time per IP with about 30 seconds between requests. Busy or
rate-limited responses are shown in the app; they are not silently retried.
The endpoint and model availability can change. 2VI Study does not contain an
API key.

When you send a question, 2VI Study sends the question and up to the last 12
messages in the current chat to Pollinations. Voice input uses the Android
speech recognition and speech services selected on the device; those services
may process audio or text online. The app does not save chat history, but those
external services have their own handling, privacy policies, and terms. Avoid
entering personal or sensitive information. Review the
[Pollinations privacy policy](https://pollinations.ai/privacy) and
[terms](https://pollinations.ai/terms).

## Build an APK on GitHub

This checkout does not need Android Studio installed. The included GitHub
Actions workflow builds the Android project and uploads a debug APK:

1. Create a GitHub repository and push this project to its `main` branch.
2. Open the repository's **Actions** tab and select **Build Android app**.
3. Wait for the workflow to finish, then download the
   `2vi-study-debug-apk` artifact from that run.
4. Extract `app-debug.apk` and install it on an Android device. Android may ask
   you to allow installation from the app used to open the downloaded file.

You can also run the workflow manually from **Actions → Build Android app →
Run workflow**. The workflow runs the JVM unit tests before building the APK.

For a local build with JDK 17, Android SDK platform 35, and Gradle 8.11.1
installed:

```shell
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. A debug APK is for
testing and sideloading, not a signed Play Store release.
