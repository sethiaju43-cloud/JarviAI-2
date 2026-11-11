# DEPLOYMENT_GUIDE

## 1. Build and install the app

1. Push the project to GitHub on the main (or master) branch.
2. Open GitHub, Actions, the "Build APK" run. It runs the unit tests and then builds a debug APK (JDK 17, Gradle 8.7).
3. Download the artifacts: "Jarvis-APK" (the app) and "Test-Report" (test results). The run summary shows PASS or FAIL for tests; a FAIL does not stop the APK from being built, so always read the summary.
4. Install the APK on the phone (allow install from unknown sources). The build is arm64-v8a only and needs Android 8.0 (API 26) or newer.
5. Only a debug APK is built today. A signed release build is not configured.

## 2. First run on the phone

1. Grant microphone, notification, contacts, phone and SMS permissions only for features you use. Turn on the accessibility service ("Jarvis Phone Control") only if you want screen reading or tapping.
2. Settings, Online AI providers: add your own API keys (never share them in chat, screenshots or the repository).
3. Settings, Local AI models: download one model on WiFi (the app checks storage and RAM first).
4. Settings, offline mic download for Hindi and English (Vosk, about 80 MB).
5. AI Dashboard: set a daily call limit and a monthly estimated spend limit if you want a budget.

## 3. Backend (optional, not built yet)

Today you can point the "Custom / apna backend" provider at your own server (see docs/BACKEND_PROXY.md). For production: HTTPS only, provider keys only in server environment variables, a per-user token, rate limits, request size limits, and logs without secrets. The planned gateway, device pairing and bridge are in CLOUD_LOCAL_ARCHITECTURE.md.

## 4. Rollback and backups

- Keep a copy of the last working source before each change; every change in this project replaces whole files, so reverting means restoring the earlier file contents.
- With git: revert the commit and let Actions build the previous version.
- Keep the previous APK; uninstalling the app deletes its local data (memory, chat, keys), so export what you need first.

## 5. Updating models

Models are downloaded into the app's private storage. Deleting a model from the Models screen frees the space. Checksum verification is not implemented yet (see SECURITY_AUDIT.md, S-03).
