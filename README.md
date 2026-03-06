# ClipReader

AI-powered Android system that connects TTS/ASR models, UI automation workflows, and clipboard APIs to solve a real operational problem in mobile AI interaction.

This project demonstrates how AI and continuous background automation can be integrated into practical mobile systems rather than used as isolated features.

## Problem

Many real-world mobile workflows, especially interacting with AI assistants (like Claude, ChatGPT), still rely on manual reading or repetitive UI tapping.

Example scenario:
- Users need to consume AI responses or screen text hands-free
- Manual copying, reading, or voice-input typing requires too many taps and disrupts flow
- No seamless, automated speech-to-action pipeline exists natively

The goal of this project is to demonstrate how AI and UI Automation can automate this workflow end-to-end.

## Solution Overview

This system connects AI models (TTS/ASR) with Accessibility automation logic and execution actions.

High-level workflow:
```
Input Source (Clipboard / Voice / Active App Context)
       ↓
AI Processing (Azure TTS / Doubao ASR)
       ↓
Decision Logic (App State Analysis & Element Detection)
       ↓
Automation Trigger (Accessibility Service Pipeline)
       ↓
System Action (Audio Playback / Automated UI Taps)
```

The architecture demonstrates how AI interaction components can be embedded into a larger operational system to drive mobile automation.

## Architecture

**Input Layer**
- Clipboard text capture
- Voice input (ASR)
- Active screen context detection

**AI Processing Layer**
- Azure TTS (text-to-speech synthesis)
- Doubao ASR (speech-to-text recognition)
- App state analysis and element detection

**Automation Layer**
- Accessibility Service pipeline
- Background continuous monitoring
- Dynamic UI interaction trigger system

**Execution Layer**
- Audio playback of AI responses
- Automated UI taps and form-filling
- Cross-app workflow orchestration

**Architecture Diagram:**
```
Clipboard / Voice Input
       ↓
  Azure TTS / ASR
       ↓
  State Detection
       ↓
 Accessibility Trigger
       ↓
 UI Action / Audio Output
```

## Tech Stack

**AI / ML**
- Azure Cognitive Services TTS
- Doubao ASR API
- UI element detection

**Mobile**
- Android (Kotlin)
- Accessibility Service API
- Background service architecture

**Automation**
- Clipboard monitoring pipeline
- Continuous background execution
- Cross-app UI automation

**Infrastructure**
- Android Gradle build system
- Modular app architecture

## Example Use Cases

This architecture can support:
- Hands-free AI chatbot consumption on mobile
- Automated form-filling workflows
- Screen reader enhancement for accessibility
- Voice-controlled mobile operations
- Background clipboard processing pipelines

## Results

Example performance metrics:
- Clipboard capture to audio playback: < 2 seconds
- Continuous background automation without user intervention
- Replaces repetitive manual reading and tapping workflows
- Demonstrated working cross-app UI automation pipeline

This project demonstrates how AI can drive practical operational efficiency on mobile devices.

## Demo

Example workflow:
1. User copies AI response text to clipboard
2. ClipReader detects new clipboard content in background
3. Azure TTS synthesizes natural speech from the text
4. System plays audio response hands-free
5. ASR captures voice reply and routes to target app

*Add screenshots, GIFs, or demo videos here.*

## Repository Structure

```
ClipReader/
├── app/
│   ├── src/
│       ├── main/
│           ├── java/       # Kotlin source
│           └── res/        # UI resources
├── gradle/
├── build.gradle
└── README.md
```

## Quick Start

Clone the repository
```bash
git clone https://github.com/chenweilie/ClipReader
```

Open in Android Studio and build
```bash
./gradlew assembleDebug
```

Install on device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Future Improvements

Possible extensions:
- Multi-language TTS support
- Improved ASR accuracy with custom models
- Visual automation triggers (screen OCR)
- Plugin system for additional AI model backends
- iOS companion app

## Author

William Chen  
Applied AI Engineer | AI Integration | Automation Systems

**LinkedIn:** https://linkedin.com/in/william-chen-98264938  
**GitHub:** https://github.com/chenweilie
