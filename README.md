**ClipReader: AI-Powered TTS & UI Automation Assistant**

An AI-powered Android system that connects TTS/ASR models, UI automation workflows, and active context to solve the operational problem of manual AI chatbot interactions.

This project demonstrates how AI and continuous background automation can be integrated into practical mobile systems rather than used as isolated features.

---

# **Problem**

Many real-world mobile workflows, especially interacting with AI assistants (like Claude, ChatGPT), still rely on manual reading or repetitive UI tapping.

Example scenario:
• Users need to consume AI responses or screen text hands-free
• Manual copying, reading, or voice-input typing requires too many taps and disrupts flow
• No seamless, automated speech-to-action pipeline exists natively

The goal of this project is to demonstrate how **AI and UI Automation can automate this workflow end-to-end.**

---

# **Solution Overview**

This system connects AI models (TTS/ASR) with Accessibility automation logic and execution actions.

High-level workflow:

Input Source (Clipboard / Voice / Active App Context)
↓
AI Processing (Azure TTS / Doubao ASR)
↓
Decision Logic (App State Analysis & Element Detection)
↓
Automation Trigger (Accessibility Service Pipeline)
↓
System Action (Audio Playback / Automated UI Taps)

The architecture demonstrates how AI interaction components can be embedded into a larger operational system to drive mobile automation.

---

# **Architecture**

Example pipeline:

**Input Layer**
• Android Clipboard Monitor
• Overlay Window Controls
• Package state tracking

**AI Processing Layer**
• Azure TTS APIs
• Doubao Speech-to-Text integration

**Automation Layer**
• Accessibility event detection (Window state changes)
• UI node traversal and bounds calculation

**Execution Layer**
• Background Audio service
• Automated UI gestures (Auto-tap Input / Auto-tap Send)
• Contextual app switching

Architecture Diagram:
```text
Input (Text/Voice)
 ↓
AI Model (TTS/ASR Synthesis)
 ↓
Decision Logic (Determine Target Buttons & State)
 ↓
Automation Trigger (Gesture Builder)
 ↓
Action / Notification (Screen Taps / Audio Playback)
```

---

# **Tech Stack**

**AI / ML**
• Microsoft Azure TTS APIs
• Doubao ASR models
• System Offline TTS

**App / Backend**
• Kotlin / Android SDK
• Foreground Services
• OkHttp (API Integration)

**Automation**
• Android AccessibilityService API
• Floating Window Overlay (SYSTEM_ALERT_WINDOW)
• GestureDescription & UI Node Traversal

**Infrastructure**
• Local Encrypted Storage (KeyStore / EncryptedSharedPreferences)
• Gradle CI/Build Scripts

---

# **Example Use Cases**

This architecture can support:
• **AI Chatbot Automation**: Hands-free interactions with Claude/ChatGPT
• **Accessibility Enhancements**: Screen reading for visually impaired
• **Operational Alerts**: Automatic text-to-speech for critical clipboard copies
• **Content Automation**: Seamless voice-to-text pipeline with auto-submission

---

# **Results**

Example performance metrics:
• Event detection to audio playback latency: **<1 second**
• Automated workflow entirely replacing manual voice-input sending steps
• Reduced mobile operational workload significantly for power users

This project demonstrates how AI can drive **practical operational efficiency** on mobile devices.

---

# **Demo**

Example workflow:
1. System receives input signal (User opens AI app / UI detects ChatGPT/Claude)
2. AI model processes the input (Text copied to clipboard / Voice recorded)
3. Decision logic determines required action (Finds "Send" button on screen)
4. Automation pipeline triggers response (Executes gesture tap on target coordinates)

*(Add screenshots, GIFs, or demo videos here)*

---

# **Repository Structure**

```text
ClipReader
│
├── app/src/main/java/com/clipreader
│   ├── clipboard    # Automation triggers & UI traversal
│   ├── overlay      # Floating window system
│   ├── service      # Background orchestration
│   ├── tts          # AI TTS model integrations
│   └── util         # Encrypted key management
│
├── build.gradle
└── README.md
```

---

# **Quick Start**

Clone the repository
```bash
git clone https://github.com/chenweilie/ClipReader.git
```

Compile and Build (Android Studio or Gradle Wrapper)
```bash
./gradlew assembleDebug
```

Install to connected device
```bash
./gradlew installDebug
```

---

# **Future Improvements**

Possible extensions:
• multi-input support (Direct screen OCR reading)
• improved model accuracy (Multi-language auto-detection)
• additional automation triggers (Support for more LLM applications)
• dashboard monitoring (History and token usage logs)

---

# **Author**

William Chen
Applied AI Engineer | AI Integration | Automation Systems

LinkedIn
https://linkedin.com/in/william-chen-98264938

GitHub
https://github.com/chenweilie
