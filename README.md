# Aura AI (Julie • जुली) — Android App & Web Portal

> 🚀 **Live Web App & Demo**: [https://rohitjangir9887-spec.github.io/1demoapp/](https://rohitjangir9887-spec.github.io/1demoapp/)  
> 📲 **Download Latest Android APK**: [GitHub Releases](https://github.com/rohitjangir9887-spec/1demoapp/releases/latest)

Aura AI is a modern native Android application (Kotlin + Jetpack Compose) and Web Portal powered by **Google Gemini Free AI** and **NVIDIA NIM Swarm (Nemotron)**.

---

## ✨ Key Features & Enhancements

1. **🤖 Google Gemini Free AI Mode (100% Free • No Subscription)**
   * Built-in support for **Google Gemini 2.0 Flash** and **1.5 Flash**.
   * Instant responses without requiring an NVIDIA key or paid subscription.
   * Free API key from Google AI Studio (`aistudio.google.com`) can be saved securely in Android Keystore.

2. **🔥 33+ NVIDIA Models Swarm Fleet**
   * Nemotron-first routing with ONE Manager model for complex multi-step tasks.
   * Cross-vendor verified failover candidates (Llama 3.3, Mistral Large).

3. **🎨 Glassmorphic UI & Smooth Animations**
   * Pulsing animated AI Neural Orb in Chat.
   * Modern glass cards, interactive prompt chips, and glowing cyber accents.
   * Real-time streaming response indicator.

4. **🌐 24h Background Voice & Wake Word ('Hey Julie' / 'हाय जुली')**
   * Background Foreground Service keeps microphone active 24/7 without being killed by Android OS.
   * Say **"Hey Julie open YouTube"**, **"Hey Julie call Papa"**, **"Hey Julie set alarm 7:00"**, or ask questions anytime hands-free.
   * Interruptible dual-language Text-to-Speech (TTS) response.

5. **🎯 Visual Touch / Tap Pointer Indicator on Screen**
   * Concentric glowing neon cyan & lime animated touch ring displays on screen whenever Julie taps or scrolls.
   * Badge displays exact coordinates `🎯 Julie Tap (X, Y) • Action Name` so the user always sees where Julie touches.

6. **💬 Floating Julie Chat Bubble & Notification Actions**
   * Floating draggable orb on top of any app with expandable Mini Chat.
   * Notification controls with **Chat Bubble**, **Capture Frame**, and **STOP** buttons.

7. **📱 Live Screen Vision & Continuous OCR**
   * 1–3 FPS real-time screen sharing and visual reasoning with Julie Vision.

---

## 📲 How to Download & Install APK

1. Go to **[GitHub Releases Latest](https://github.com/rohitjangir9887-spec/1demoapp/releases/latest)**.
2. Tap on `1demoapp-debug.apk` to download directly to your Android device.
3. Open the APK from your phone's Notification or File Manager.
4. Allow **"Install unknown apps"** when prompted, and tap **Install**.
5. Open the app and start chatting immediately with Google Free AI!

---

## 🛠️ GitHub Deployment & CI/CD

* **GitHub Actions Android CI**: Automatically builds `:app:assembleDebug` and attaches `1demoapp-debug.apk` to GitHub Releases on push to `main`.
* **GitHub Pages Web Portal**: Automatically deploys the interactive landing page and web chat demo from `index.html`.
