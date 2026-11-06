# HushSync: setup guide

HushSync is an Android app that:

- **Auto-uploads** your phone's call recordings to Google Drive. The app sleeps until Android reports a new recording, uploads it, then sleeps again. A safety check every 6 hours catches anything missed.
- **Deletes each recording from the phone** once Google Drive confirms the full file arrived

HushSync never records anything itself. Your phone's dialer records the calls; HushSync only uploads them. It needs no microphone or phone permissions.

In Google Drive, files go into:

```
HushSync/
├── 2026-10/...
└── 2026-11/...
```

The app only gets the `drive.file` permission. That means it can see **only the files it uploads**, never the rest of your Drive.

---

## Step 1: Install Android Studio (about 30 minutes, mostly downloading)

1. Go to **https://developer.android.com/studio** and click **Download Android Studio**.
2. Run the installer. Keep the defaults and click **Next → Install**.
3. When Android Studio opens the first time, choose **Standard** setup.
4. Read and accept the license agreements yourself, then let it download the Android SDK (about 3 GB).

You need around **10 GB of free disk space** and ideally **8 GB of RAM** or more.

## Step 2: Open the project

1. In Android Studio, click **Open** and select the `HushSync` folder (the one containing `settings.gradle.kts`).
2. Wait for **Gradle sync** to finish (the progress bar at the bottom). The first time takes 5–10 minutes.
3. If it offers to **upgrade the Android Gradle Plugin**, you can accept.
4. Click **Build → Make Project**. If you see red errors, check the message in the Build tab.

## Step 3: Get your SHA-1 fingerprint

Google needs this to know the app is really yours. It comes from the key the app is signed with.
See [README → Get a signing key](README.md#1-get-a-signing-key) to create the key and print its
SHA-1. If you build without your own key, use your Android debug key's SHA-1 instead:

```
"C:\Program Files\Android\Android Studio\jbr\bin\keytool" -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

(If the terminal is PowerShell, put `&` in front of the first quoted path and use `$env:USERPROFILE` instead of `%USERPROFILE%`.)

## Step 4: Set up Google Drive access (about 10 minutes, free)

Follow [README → Set up Google Cloud](README.md#3-set-up-google-cloud-drive-sign-in): create a
project, turn on the Drive API, set up the consent screen, create the Android credential with
package `com.hushsync.app` and your SHA-1, then publish the app.

## Step 5: Put the app on your phone

1. Turn on Developer options. On your phone, go to **Settings → About phone** and tap **Build number** 7 times. (On Xiaomi phones, tap **MIUI/HyperOS version** instead.)
2. Go to **Settings → Developer options** and turn on **USB debugging**.
3. Connect the phone to your PC with a USB cable, and tap **Allow** on the phone.
4. In Android Studio, select your phone in the device dropdown at the top, then press the green **▶ Run** button.
5. After it's installed, you can turn **USB debugging** off again. The app keeps working.

**Without USB debugging:** in Android Studio click **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Send the `app-debug.apk` file to your phone, open it and tap **Install**. Allow "Install unknown apps" when asked.

## Step 6: Turn on automatic call recording in your phone's dialer

Android only lets the phone's built-in dialer record calls. HushSync uploads what the dialer saves.

| Phone | Where to turn on auto-record | Usual recordings folder |
|---|---|---|
| Samsung | Phone app → ⋮ → Settings → Record calls → Auto record calls | `Recordings/Call` |
| Xiaomi / Redmi / POCO | Phone app → Settings → Call recording → Record calls automatically | `MIUI/sound_recorder/call_rec` |
| Realme / Oppo / OnePlus (ColorOS dialer) | Phone app → Settings → Call recording → Auto record | `Music/Recordings/Call Recordings` or `Recordings` |
| Vivo | Phone app → Settings → Call recording → Auto record | `Recordings/Record/Call` |
| Pixel / Motorola / phones using **Google's Phone app** | Phone app → Settings → Call recording | ⚠️ Saved inside the Phone app where other apps can't reach them, so HushSync **can't** upload these |

The folder names differ between models. If you can't find yours, make one test call, then open the **Files** app and search for the date or the contact's name.

## Step 7: Set up the app (one time)

1. **Connect Google Drive**: pick your account and allow access.
2. **Choose calls folder**: go to the folder from the table above and tap **Use this folder → Allow**.
3. **Battery setting.** Start with HushSync on **Optimized**, the default, and make a test call. If the recording reaches Drive within a few minutes, you're done.
   - If it only arrives hours later, go to **Settings → Apps → HushSync → Battery** and set it to **Unrestricted** / **Allow background activity**. Realme and Oppo sometimes block background triggers. Battery use stays tiny, because the app does nothing between recordings.
   - On Xiaomi, also turn on **Autostart** for HushSync.

## Using it

- Nothing to do. About 1–3 minutes after a call ends, the recording is uploaded and then always deleted from the phone.
- It uploads on **Wi-Fi or mobile data**, whichever is available.
- HushSync shows no notifications, except **"Uploads paused"** if Google Drive needs you to sign in again.
- To check it's working, look in Google Drive → **HushSync**.

## Safety details

- A file is deleted **only after** Google Drive reports the same file size as on the phone. If the upload fails, the file stays on the phone and the app retries later.
- Call files changed in the last 30 seconds are skipped, and the app checks again 2 minutes later. So a call that's still being recorded is never uploaded half-finished.

## Legal note

Recording people without telling them can be illegal in some places. Tell the people on the call that you're recording.
