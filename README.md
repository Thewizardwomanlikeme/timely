# Timely

Timely is an Android application that serves as a custom time announcer. It runs in the background and uses Text-To-Speech (TTS) to announce the current time at user-specified intervals, on chosen days of the week, and within specific active hours.

## Features

*   **Time Announcements:** Hear the current time announced automatically via TTS.
*   **Customizable Intervals:** Choose how often you want to be reminded (e.g., every 15, 30, or 60 minutes).
*   **Active Time Range:** Set a start and stop time to ensure announcements only happen when you want them (default 7:00 AM to 10:00 PM).
*   **Day Selection:** Toggle individual days of the week so the app only runs on the days you need it.
*   **Background Service:** The app includes a reliable background service that ensures time announcements happen even when the app is closed.
*   **Persistent Settings:** Your preferences are saved and remembered automatically.

## How to use

1. Open the Timely app.
2. Select the days of the week you want the app to be active.
3. Set your preferred 'Start time' and 'Stop time' for the announcements.
4. Set the 'Interval' for how often the time should be announced.
5. Tap the 'Start' button to begin the service.
6. The app will now announce the time based on your configuration in the background!

## Technical Stack

*   **Platform:** Android
*   **Language:** Kotlin
*   **Key Components:** Background Services, Text-to-Speech (TTS), SharedPreferences for settings storage.
