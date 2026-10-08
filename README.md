# Lumen (Phase 1)

Android TV IPTV player. This phase: project setup, design system, floating top dock, focus handling, Home screen with sample data.

## Build the APK on GitHub (no Android Studio needed)

1. Create a free account at github.com and a new empty repository (name it `lumen`, private is fine).
2. On the repository page choose **Add file > Upload files**.
3. Drag the **contents** of this folder into the browser (open the folder and select everything inside it, including the hidden `.github` folder), then click **Commit changes**.
4. Open the **Actions** tab. The "Build APK" run starts by itself and takes a few minutes.
5. When it turns green, open the run and download **lumen-debug-apk** under Artifacts. Unzip it to get `app-debug.apk`.
6. If it turns red, open the run, open the failed step, and copy the first error lines to Claude.

## Install on the TV

Put `app-debug.apk` on a USB stick, or use the "Downloader" app on the TV with a link to the file, then allow installs from unknown sources when asked.
