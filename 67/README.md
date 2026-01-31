# 67 - Meta Glasses Media Capture App

An advanced Android application that enables seamless media capture using cutting-edge Meta Ray-Ban glasses. The app efficiently handles photos and audio, transferring the data securely to a configurable HTTP endpoint.

---

## 🚀 Features
- **Photo & Audio Capture:** Syncs flawlessly with Meta Ray-Ban glasses to capture JPEG and WAV files.
- **Customizable API Endpoint:** Easily configure the server URL for uploading media.
- **Device Support:** Compatible with the latest Meta smart glasses.

---

## 🛠️ Setup

### 1️⃣ GitHub Token for SDK Access
The Meta WDA SDK is distributed via GitHub Packages. To get started:
1. Go to [GitHub Settings > Tokens](https://github.com/settings/tokens).
2. Create a **Personal Access Token (classic)** with `read:packages` scope.
3. Configure your app:
   - **Option 1:** Create a `local.properties` file in the project's root:
     ```
     github_token=ghp_YOUR_TOKEN_HERE
     ```
   - **Option 2:** Export as an environment variable:
     ```bash
     export GITHUB_TOKEN=ghp_YOUR_TOKEN_HERE
     ```

### 2️⃣ Configure API Endpoint
Set the `API_BASE_URL` in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://your-server.com/api\"")
```

### 3️⃣ Build & Run
Generate a debug build with:
```bash
./gradlew assembleDebug
```

---

## 🌐 API Contract

The app communicates with the server via a `POST` request to `/upload` using `multipart/form-data`. Below are the fields sent:

| Field      | Type   | Description                               |
|------------|--------|-------------------------------------------|
| `image`    | `file` | JPEG image taken from the camera.         |
| `audio`    | `file` | WAV audio recorded using the microphone.  |
| `timestamp`| `string` | Exact timestamp of media capture.       |

---

## 📱 Supported Devices
- ✅ **Ray-Ban Meta**
- ✅ **Oakley Meta HSTN**
- 🚧 **Coming Soon:** Oakley Meta Vanguard, Meta Ray-Ban Display

---

## ⚠️ Known Limitations
- **Video Support:** Limited to 720p @ 30fps due to Bluetooth bandwidth.
- **SDK Restrictions:** Currently in public preview; production use is not yet supported.

---

For further questions or contributions, feel free to open an issue or pull request.
