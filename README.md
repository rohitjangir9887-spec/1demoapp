# Aura AI / OmniTask AI — Android app

This is the existing native Android (Kotlin + Jetpack Compose) project with Chat, Tasks, Models, and Screen Share screens.

## Download your APK to Android

1. Open **[Build 1demoapp APK](../../actions/workflows/android-apk.yml)** in the GitHub Actions tab.
2. Select the latest **successful** run (green checkmark). If no run is present, choose **Run workflow** on the `main` branch.
3. Scroll to **Artifacts**, then tap **1demoapp-debug-apk**.
4. Extract the downloaded ZIP and open `app-debug.apk` to install it on your phone.

**Note:** This is a debug APK for testing, not a Play Store release. Each GitHub Actions build generates its own debug signing key; to install future APKs over an older one you may need to uninstall the older debug build first.

## NVIDIA NIM API — where to enter it

Open the installed Android app, sign in if required by your Firebase setup, then open **33 Models → NVIDIA NIM • API Settings**.

Paste your key from https://build.nvidia.com/ and tap **Save key**. The key is stored encrypted *on that device* using Android Keystore. It is **not** sent to GitHub and it is not embedded into the downloadable APK. Use **Remove** to erase the saved key.

The original Gradle secrets plugin still has the placeholder `NVIDIA_API_KEY` in `.env.example` for project compatibility, but the updated NVIDIA client deliberately uses the key set inside the app, not a key compiled into the APK. **Do not commit real API keys** or put them in repository settings hoping to configure the installed app.

## Build details

- Existing application ID: `com.aistudio.auraai.k9x4mp`
- Existing code and layout are preserved
- Gradle 9.3.1, Android Gradle Plugin 9.1.1, JDK 17, Android SDK 36.1
- GitHub Actions automatically runs on pushes to `main` and supports manual runs
- Build artifact: `1demoapp-debug-apk`

## Important limitations

The app uses Firebase services for login, chat/task storage, and some Gemini-based features; deploying those features requires a valid Firebase project and configuration. NVIDIA NIM does not automatically provide a cloud computer or permission to control all Android apps. Screen capture features require Android user consent. NIM model availability and usage quotas depend on your NVIDIA account.

If the build fails, open the failed workflow run to read the first Gradle error. Do not assume the APK was produced until the workflow succeeds.
