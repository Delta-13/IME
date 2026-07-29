# ToneIME Android

Android 自用 MVP，不替换现有中文输入法：

- 可直接安装的调试包：[`dist/ToneIME-android-debug.apk`](dist/ToneIME-android-debug.apk)
- 界面可在中文、日文、英文之间即时切换；选择会保存，实时浮窗同步刷新。
- 源语言和目标语言分别支持中文、日文、英文、韩文、德文，可进行任意不同语种之间的翻译。
- 选择关系、场景和三个语气强度后生成符合目标语言习惯的候选。
- 在主页面点“开启 / 管理实时浮窗”，启用 ToneIME 无障碍服务后，可在其他应用的当前输入框旁实时翻译。
- 浮窗可拖动并直接调整方向、关系、场景和三个语气强度；停止输入约 0.9 秒后生成推荐译文，点击译文才会替换整框原文。
- 从支持 Android“处理文本”的输入框选中文字，可直接打开 ToneIME，确认后替换原文并返回。
- 也可通过系统分享菜单把文字发送给 ToneIME；译文只能显式复制、返回或打开分享菜单，绝不自动发送。
- API Key 使用 Android Keystore 的 AES-GCM 密钥加密后保存在应用私有偏好中。
- 原文和译文不落盘；浮窗不读取密码框和 ToneIME 自己的输入框；API 请求使用 `store:false`，只允许 HTTPS。

## 构建

要求 JDK 21、Android SDK Platform 36、Build Tools 36.0.0，以及 Gradle Wrapper 9.3.1。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 输出到 `app\build\outputs\apk\debug\app-debug.apk`。

## 当前范围

当前 0.2.0 已验证三语界面、五语翻译、系统选中文本和无障碍浮窗的明确确认闭环。尚未接入 Trime/Rime，也不会自动发送消息。
