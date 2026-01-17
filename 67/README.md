# 67 - Meta Glasses Media Capture App

Android app that captures photos and audio from Meta Ray-Ban glasses and sends them to an HTTP endpoint.

## Setup

### 1. GitHub Token for SDK Access
The Meta WDA SDK is distributed via GitHub Packages. You need a Personal Access Token:

1. Go to [GitHub Settings > Tokens](https://github.com/settings/tokens)
2. Create a **Personal Access Token (classic)** with `read:packages` scope
3. Either:
   - Create `local.properties` in project root:
     ```
     github_token=ghp_YOUR_TOKEN_HERE
     ```
   - Or set environment variable:
     ```bash
     export GITHUB_TOKEN=ghp_YOUR_TOKEN_HERE
     ```

### 2. Configure API Endpoint
Update `API_BASE_URL` in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://your-server.com/api\"")
```

### 3. Build & Run
```bash
./gradlew assembleDebug
```

## API Contract

The app sends a `POST` request to `/upload` with `multipart/form-data`:

| Field | Type | Description |
|-------|------|-------------|
| image | file | JPEG image from glasses camera |
| audio | file | WAV audio from glasses microphone |
| timestamp | string | Unix timestamp of capture |

## Supported Devices
- Ray-Ban Meta
- Oakley Meta HSTN
- (Coming soon) Oakley Meta Vanguard, Meta Ray-Ban Display

## Limitations
- Video: Max 720p @ 30fps (Bluetooth bandwidth)
- SDK is in public preview - can't ship to production yet
