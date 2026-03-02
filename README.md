# ClipReader

ClipReader 是一款轻量级、响应迅速的安卓剪贴板语音助手，专门为 AI 聊天场景（如 Claude, ChatGPT）优化。

## 🌟 核心特性

- **双模式语音体验**：
  - **▶ TTS 播放**：自动识别前台 AI 应用，一键播放最新回复。支持 Azure TTS 高质量语音与系统 TTS 保底。
  - **🎤 语音输入**：专门适配豆包输入法，实现自动调起键盘、开始录音、语音转文字及智能自动发送。
- **智能 App 适配**：针对 ChatGPT 和 Claude 的不同界面特性进行了自动化点击优化，极致提升交互效率。
- **隐私与安全**：API 密钥通过 `local.properties` 管理，不上传至源码，保护您的云服务凭证。
- **极致轻量**：安装包仅几兆，专注核心功能，无冗余代码。

## 🚀 快速开始

### 权限说明
由于应用涉及悬浮窗、无障碍服务及剪贴板读取，首次启动请授予以下权限：
1. **显示在其他应用上 (Overlay)**：用于显示控制板。
2. **无障碍服务 (Accessibility Service)**：用于模拟点击发送按钮。
3. **通知权限**：用于保持后台服务存活。

### 配置 Azure TTS
在编译前，建议在 `local.properties` 中添加您的 Azure 凭证：
```properties
azure.api.key=YOUR_API_KEY
azure.region=YOUR_REGION
```

## 🛠 开发与构建

项目使用 Kotlin + Gradle 开发环境。
```bash
./gradlew assembleDebug
```

## 📦 下载

最新的发布版本 APK 可以前往 [GitHub Releases](https://github.com/chenweilie/ClipReader/releases) 页面下载。

---
*Created by William with Antigravity*
