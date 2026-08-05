# ToneIME Android

Android 自用 MVP，不替换现有中文输入法：

- 调试 APK 可通过下方构建命令生成，输出位置为 `app/build/outputs/apk/debug/app-debug.apk`。
- 主页面使用 React Native 0.86，安装包已内置 JS Bundle，运行时不需要 Metro。
- 界面可在中文、日文、英文之间即时切换；选择会保存，实时浮窗同步刷新。
- 源语言和目标语言分别支持中文、日文、英文、韩文、德文，可进行任意不同语种之间的翻译。
- 选择关系、场景和三个语气强度后生成符合目标语言习惯的候选。
- 在主页面点“开启 / 管理实时浮窗”，启用 ToneIME 无障碍服务后，可在其他应用的当前输入框旁实时翻译。
- 实时浮窗继续使用 Android 原生无障碍窗口，避免依赖 React Native 后台运行。
- 浮窗可拖动；右下角手柄可连续调整宽高，内容在小尺寸下仍可滚动，尺寸会自动保存。
- 浮窗调节面板使用滑杆控制 35%–100% 的整窗透明度，文字和按钮同步透明；浮窗外区域保持清晰并可正常操作。
- 浮窗可直接调整方向、关系、场景和三个语气强度；停止输入约 0.9 秒后生成推荐译文，点击译文才会替换整框原文。
- 从支持 Android“处理文本”的输入框选中文字，可直接打开 ToneIME，确认后替换原文并返回。
- 也可通过系统分享菜单把文字发送给 ToneIME；译文只能显式复制、返回或打开分享菜单，绝不自动发送。
- 可选择 OpenAI、Claude、Qwen、Kimi、MiniMax、DeepSeek、Google AI（Gemini）或自定义 OpenAI 兼容 HTTPS 服务；切换后会填入可编辑的推荐端点和模型。
- Claude 使用原生 Messages API；其余预设使用 OpenAI 兼容的 Chat Completions API。
- API Key 按服务商隔离，使用 Android Keystore 的 AES-GCM 密钥加密后保存在应用私有偏好中。
- 原文和译文不落盘；浮窗不读取密码框和 ToneIME 自己的输入框；OpenAI 请求使用 `store:false`，所有 API 地址只允许 HTTPS。

## 构建

要求 Node.js 22.11.0+、JDK 17+、Android SDK Platform 36 和 Build Tools 36.0.0。首次构建先在项目根目录安装锁定依赖：

```powershell
npm ci
npm run typecheck
```

再构建 Android：

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 输出到 `app\build\outputs\apk\debug\app-debug.apk`。

## 当前范围

当前 0.3.1 已验证 React Native 三语主界面、五语翻译、系统选中文本，以及支持透明度和拖拽缩放的原生无障碍浮窗。浮窗及下拉菜单之外的区域保持可操作，不使用整屏背景模糊。尚未接入 Trime/Rime，也不会自动发送消息。
