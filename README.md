# HushSync

An Android app that **automatically uploads your phone's call recordings to Google Drive**, then
deletes them from the phone once Drive confirms the full file arrived.

- **Never records anything itself.** Your phone's built-in dialer records the calls; HushSync only
  uploads the files it saves. No microphone or phone permissions.
- **Sleeps until needed.** Android wakes it when a new recording appears; it uploads, then sleeps
  again. A safety check every 6 hours catches anything missed.
- **Safe deletes.** A recording is deleted only after Google Drive reports the same file size as on
  the phone. Files changed in the last 30 seconds are skipped, so a call still being recorded is
  never uploaded half-finished.
- **Sees only its own files.** It uses Google's `drive.file` permission, so it can access only the
  files it uploads, never the rest of your Drive.
- **Quiet.** No notifications, except *"Uploads paused"* if Google Drive needs you to sign in again.

Files land in Drive like this:

```
HushSync/
├── 2026-10/...
└── 2026-11/...
```

> **Legal note:** recording people without telling them can be illegal in some places. Tell the
> people on the call that you're recording.

## Contents

1. [Which phones work](#which-phones-work)
2. [Get a signing key](#1-get-a-signing-key)
3. [Build the app](#2-build-the-app)
4. [Set up Google Cloud (Drive sign-in)](#3-set-up-google-cloud-drive-sign-in)
5. [Set up the phone](#4-set-up-the-phone)
6. [Troubleshooting](#troubleshooting)
7. [Project layout](#project-layout)

## Which phones work

HushSync needs a dialer that saves recordings as normal files. Android only lets the phone's
built-in dialer record calls; HushSync uploads what the dialer saves.

| Phone | Where to turn on auto-record | Usual recordings folder |
|---|---|---|
| Samsung | Phone app → ⋮ → Settings → Record calls → Auto record calls | `Recordings/Call` |
| Xiaomi / Redmi / POCO | Phone app → Settings → Call recording → Record calls automatically | `MIUI/sound_recorder/call_rec` |
| Realme / Oppo / OnePlus (ColorOS dialer) | Phone app → Settings → Call recording → Auto record | `Music/Recordings/Call Recordings` or `Recordings` |
| Vivo | Phone app → Settings → Call recording → Auto record | `Recordings/Record/Call` |
| Pixel / Motorola / phones using **Google's Phone app** | Phone app → Settings → Call recording | ⚠️ Saved inside the Phone app where other apps can't reach them, so HushSync **can't** upload these |

Folder names differ between models. If you can't find yours, make one test call, then open the
**Files** app and search for the date or the contact's name.

## 1. Get a signing key

Google Drive sign-in only works for an app whose **package name + SHA-1 fingerprint** you register
in Google Cloud. The SHA-1 comes from the key the app is signed with, so every build must use
**the same key**. Keep that key out of git: anyone who has it can sign apps that pass as yours.

Create one with `keytool` (it comes with Android Studio, in `jbr/bin`):

```bash
keytool -genkeypair -keystore hushsync-release.jks -storetype PKCS12 -alias hushsync -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=HushSync"
```

Keep the file **outside** the project, e.g. in a `keys` folder next to it, then print its SHA-1:

```bash
keytool -list -v -keystore hushsync-release.jks -alias hushsync
```

Copy the line starting with **SHA1:** (it looks like `AB:12:CD:...`). You need it in step 3.

## 2. Build the app

### Option A: build in the cloud (GitHub Actions)

No Android Studio needed. The workflow in `.github/workflows/build.yml` builds the APK on GitHub's
servers on every push to `main`, or when you start it from the **Actions** tab.

1. Fork or push this repo to your own GitHub account.
2. Go to **Settings → Secrets and variables → Actions → New repository secret** and add:
   | Name | Value |
   |---|---|
   | `HUSHSYNC_KEYSTORE_BASE64` | Your `.jks` file encoded as base64 (one long line). Make it with `base64 -w0 hushsync-release.jks` (Git Bash / Linux) or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("hushsync-release.jks"))` (PowerShell). |
   | `HUSHSYNC_KEYSTORE_PASSWORD` | The key's password |
3. Open **Actions → Build HushSync APK → Run workflow**.
4. When it shows a green tick, open the run and download the **HushSync-apk** artifact. Unzip it to
   get `app-debug.apk`.

The build stops with a clear error if either secret is missing.

### Option B: build on your PC (Android Studio)

1. Put a `keystore.properties`-style file at `../keys/hushsync-keystore.properties` (outside the
   project folder):
   ```properties
   storeFile=C:/path/to/keys/hushsync-release.jks
   keyAlias=hushsync
   storePassword=your-password
   keyPassword=your-password
   ```
2. Install [Android Studio](https://developer.android.com/studio) (Standard setup; it downloads the
   Android SDK, about 3 GB). You need around 10 GB of free disk space and ideally 8 GB of RAM.
3. Open the `HushSync` folder in Android Studio, wait for Gradle sync (5–10 minutes the first
   time), then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

Without a key file or secrets, builds use your own Android debug key instead. That works too: just
register **that** key's SHA-1 in Google Cloud. Print it with:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

(On Windows the file is `%USERPROFILE%\.android\debug.keystore`.)

## 3. Set up Google Cloud (Drive sign-in)

Free, about 10 minutes, all in the browser. Until the app's package name and SHA-1 are registered,
Google refuses the Drive sign-in, so do this **before** tapping *Connect Google Drive*.

You need two values:

- **Package name:** `com.hushsync.app`
- **SHA-1:** from [step 1](#1-get-a-signing-key)

### Step 1: Create a project

1. Go to <https://console.cloud.google.com> and sign in with the Gmail you want recordings saved to.
2. Top-left, click the **project dropdown → New Project**.
3. Name it `HushSync` → **Create**. Wait a few seconds, then make sure it's **selected** in that
   dropdown.

### Step 2: Turn on the Drive API

1. Left menu (☰) → **APIs & Services → Library**.
2. Search **Google Drive API**, click it → **Enable**.

### Step 3: Set up the consent screen

1. **APIs & Services → OAuth consent screen** (in the newer layout: **Google Auth Platform →
   Branding**).
2. If asked, choose **External → Create**.
3. Fill only the required fields: **App name** = `HushSync`, **User support email** = your email,
   **Developer contact email** = your email. Save and continue through the pages.
4. Under **Data access / Scopes**, add `.../auth/drive.file`.
5. Find the **Audience** (or **Test users**) section → **Add users** → add **your own Gmail** →
   Save. This is what lets your account sign in while the app is in *Testing*.

### Step 4: Create the Android credential (the key step)

1. **APIs & Services → Credentials → + Create Credentials → OAuth client ID**.
2. **Application type:** Android.
3. **Name:** `HushSync Android` (anything works).
4. **Package name:** `com.hushsync.app`
5. **SHA-1 certificate fingerprint:** paste your SHA-1.
6. Click **Create**. You don't copy anything back into the app; Google just needs to know these.

### Step 5: Publish the app (recommended)

Apps left in *Testing* may make you reconnect Drive every 7 days.

1. Open **APIs & Services → OAuth consent screen** (newer layout: **Google Auth Platform →
   Audience**). Shortcut: <https://console.cloud.google.com/apis/credentials/consent>. Check that
   the HushSync project is selected at the top.
2. Next to **Publishing status: Testing**, click **Publish app**, then **Confirm**.
3. The status changes to **In production**. The *Test users* section disappears; you no longer
   need it.
4. There may be a note about verification. You can ignore it: `drive.file` is a non-sensitive
   scope, so Google doesn't require a review, and it works immediately.

"In production" doesn't put the app anywhere public. It only means any Google account may sign in
to *your* copy of the app, without the 7-day limit.

### Step 6: Try it

Open HushSync on the phone → **Connect Google Drive** → pick your account → **Allow**.

⚠️ After creating the credential, Google can take **5 minutes to a few hours** to activate it. If it
fails right away, wait a bit and try again.

## 4. Set up the phone

### Install the app

**Without a cable:** send `app-debug.apk` to your phone, open it and tap **Install**. Allow
"Install unknown apps" when asked.

**With a USB cable (from Android Studio):**

1. Turn on Developer options: **Settings → About phone**, tap **Build number** 7 times. (On Xiaomi
   phones, tap **MIUI/HyperOS version** instead.)
2. Go to **Settings → Developer options** and turn on **USB debugging**.
3. Connect the phone to your PC and tap **Allow** on the phone.
4. In Android Studio, pick your phone in the device dropdown and press **▶ Run**.
5. Once it's installed, you can turn USB debugging off again. The app keeps working.

### Turn on auto-record in your dialer

Use the settings path for your phone from the [table above](#which-phones-work).

### Set up HushSync (one time)

1. **Connect Google Drive**: pick your account and allow access.
2. **Choose calls folder**: go to your dialer's recordings folder → **Use this folder → Allow**.
3. **Battery:** start with HushSync on **Optimized** (the default) and make a test call. If the
   recording reaches Drive within a few minutes, you're done.
   - If it only arrives hours later, go to **Settings → Apps → HushSync → Battery** and choose
     **Unrestricted** / **Allow background activity**. Realme and Oppo sometimes block background
     triggers. Battery use stays tiny, because the app does nothing between recordings.
   - On Xiaomi, also turn on **Autostart** for HushSync.

### Using it

- Nothing to do. About 1–3 minutes after a call ends, the recording is uploaded and then deleted
  from the phone.
- It uploads on **Wi-Fi or mobile data**, whichever is available.
- To check it's working, look in Google Drive → **HushSync**.

## Troubleshooting

| Problem | Fix |
|---|---|
| *Connect Google Drive* fails with error **10** | The package name or SHA-1 in Google Cloud doesn't match the installed app. Check you registered the SHA-1 of the key that signed this APK. |
| Error **12500**, or sign-in fails right after setup | The consent screen is incomplete (support email missing), your Gmail isn't a test user while in *Testing*, or the credential hasn't activated yet. Wait up to a few hours. |
| Must reconnect every 7 days | The app is still in *Testing*. [Publish it](#step-5-publish-the-app-recommended). |
| *"Uploads paused"* notification | Google needs you to sign in again. Open HushSync and tap **Connect Google Drive**. |
| Recordings arrive hours late | Battery optimisation. Set HushSync's battery setting to **Unrestricted**. |
| App won't install over the old one | It was signed with a different key. Uninstall the old app first, then set it up again. |

## Project layout

```
.github/workflows/build.yml   Cloud build (GitHub Actions)
app/src/main/
  AndroidManifest.xml         Permissions: internet, network state, notifications
  java/com/hushsync/app/
    MainActivity.kt           The screen: Connect Drive, Choose folder
    UploadWorker.kt           Finds new recordings, uploads, verifies, deletes
    RecordingWatcher.kt       Wakes the app when a new recording appears
    Sync.kt                   Scheduling: wake trigger + 6-hour safety check
    DriveAuth.kt              Google sign-in with the drive.file scope
    DriveUploader.kt          Drive REST calls: folders + resumable upload
    Prefs.kt                  Saved settings and upload bookkeeping
```

Built with Kotlin, AndroidX WorkManager, Google Play services Identity (sign-in) and OkHttp.
Min SDK 26 (Android 8.0).
