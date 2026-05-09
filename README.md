# ClipReader

AI-powered Android system that connects TTS/ASR models, UI automation workflows, and clipboard APIs to solve real operational problems in mobile AI interaction.

AI驱动的 Android 系统，融合语音合成（TS）/语音识别（ASR）模型、UI 自动化工作流与剪贴板 API，解决移动端 AI 交互中的真实操作痛点。

---

## Problem / 问题

Many real-world mobile workflows—especially interacting with AI assistants—still rely on manual reading or repetitive tapping.

现实中的移动端工作流，特别是与 AI 助手交互的场景，仍高度依赖手动阅读或重复点击。

### Example / 示例

- Users need to consume AI responses hands-free — 需要免提消费 AI 回复内容
- Manual copying, reading, or voice-input typing disrupts flow — 手动复制、朗读、语音输入打断流程
- No seamless, automated speech-to-action pipeline exists natively — 缺乏无缝的自动化语音→操作管道

The goal: demonstrate how AI + UI Automation can automate this workflow end-to-end.

目标：展示如何用 AI + UI 自动化技术实现端到端自动化工作流。

---

## Solution Overview / 方案概览

The system connects AI models (TTS/ASR) with Accessibility automation logic and execution actions.

系统将 AI 模型（TTS/ASR）与无障碍服务自动化逻辑及执行动作连接。

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

---

## Architecture / 架构

### Input Layer / 输入层

- Clipboard text capture — 剪贴板文本捕获
- Voice input (ASR) — 语音输入（语音识别）
- Active screen context detection — 当前页面上下文检测

### AI Processing Layer / AI 处理层

- Azure TTS (text-to-speech synthesis) — Azure 语音合成
- Doubao ASR (speech-to-text recognition) — 豆包语音识别
- App state analysis and element detection — 应用状态分析与元素检测

### Automation Layer / 自动化层

- Accessibility Service pipeline — 无障碍服务管道
- Continuous background monitoring — 后台持续监听
- Dynamic UI interaction trigger system — 动态 UI 交互触发系统

### Execution Layer / 执行层

- Audio playback of AI responses — AI 回复语音播报
- Automated UI taps and form-filling — 自动点击与表单填写
- Cross-app workflow orchestration — 跨应用工作流编排

---

## Tech Stack / 技术栈

| Category | 技术 |
|----------|------|
| AI/ML | Azure Cognitive Services TTS, Doubao ASR, UI element detection |
| Mobile | Android (Kotlin), Accessibility Service API, Background Service |
| Automation | Clipboard monitoring, Background execution, Cross-app UI automation |
| Infrastructure | Android Gradle, Modular app architecture |

---

## Example Use Cases / 典型场景

- Hands-free AI chatbot consumption — 免提消费 AI 对话
- Automated form-filling workflows — 自动填写表单
- Screen reader enhancement for accessibility — 无障碍屏幕朗读增强
- Voice-controlled mobile operations — 语音控制移动操作
- Background clipboard processing pipelines — 后台剪贴板处理管道

---

## Results / 效果

- Clipboard capture to audio playback: < 2 seconds — 剪贴板捕获到语音播放 < 2 秒
- Continuous background automation without user intervention — 后台持续自动化，无需用户干预
- Replaces repetitive manual reading and tapping workflows — 替代重复的手动阅读与点击流程
- Demonstrated working cross-app UI automation pipeline — 已验证跨应用 UI 自动化管道

---

## Demo / 演示流程

```
1. User copies AI response text to clipboard  用户复制 AI 回复到剪贴板
       ↓
2. ClipReader detects new clipboard content in background  ClipReader 后台检测剪贴板内容
       ↓
3. Azure TTS synthesizes natural speech  Azure TTS 合成自然语音
       ↓
4. System plays audio response hands-free  系统免提播放语音回复
       ↓
5. ASR captures voice reply and routes to target app  ASR 捕获语音回复并路由至目标应用
```

---

## Quick Start / 快速开始

```bash
# Clone / 克隆
git clone https://github.com/gamazhige-cell/ClipReader
cd ClipReader

# Build / 构建
./gradlew assembleDebug

# Install / 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Future Improvements / 未来改进

- Multi-language TTS support — 多语言 TTS 支持
- Custom ASR model for improved accuracy — 自定义 ASR 模型提升识别精度
- Visual automation triggers (screen OCR) — 视觉自动化触发（屏幕 OCR）
- Plugin system for additional AI model backends — 插件系统接入更多 AI 模型
- iOS companion app — iOS 端配套应用

---

## Author / 作者

William Chen
Applied AI Engineer | AI Integration | Automation Systems

**LinkedIn:** https://linkedin.com/in/william-chen-98264938
**GitHub:** https://github.com/gamazhige-cell