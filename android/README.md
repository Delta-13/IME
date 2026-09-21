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
- 浮窗可直接调整方向、关系、场景和三个语气强度；停止输入约 0.45 秒后生成推荐译文，点击译文才会替换整框原文。浮窗会显示发送中和等待服务端回复的状态。
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

## GitHub Actions 发布

在 [Releases](https://github.com/Delta-13/IME/releases/latest) 下载正式签名的 APK，支持 Android 8.0+。APK 已包含 JS Bundle，不需要 Metro。旧调试版使用不同证书，首次迁移到正式版时需要先卸载旧版（会清除本地设置和保存的 API Key）；后续正式版保持同一签名，可直接更新。

工作流 `.github/workflows/android-release.yml` 在 main 推送、Pull Request 和手动运行时执行 TypeScript 检查、Release 单元测试、Lint 与 Release 构建。推送匹配应用版本的 `v*` 标签时，检查成功后会另外签名并发布 APK、`SHA256SUMS` 和公开签名指纹。签名凭据只提供给独立发布任务，不提供给依赖安装和构建任务。

仓库 Actions Secrets：

- `ANDROID_KEYSTORE_BASE64`：固定 PKCS12 发布密钥库的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：密钥库与私钥的相同密码。
- `ANDROID_KEY_ALIAS`：发布私钥别名。

发布新版本时，同步更新 `package.json`、`package-lock.json` 和 `app/build.gradle` 的版本号，递增 Android `versionCode`，并新增 `docs/releases/<版本>.md` 三语说明。提交后推送对应的标签，例如 `git tag v0.3.2`、`git push origin v0.3.2`。Actions 使用 JDK 21、Node.js 22、SDK 36、Build Tools 36.0.0 和 NDK 27.1.12297006。未签名的中间产物仅用于 CI，正式安装包位于 Release 页面。

本地签名备份位于被忽略的 `.tools/android-signing/`；密钥库有密码保护，密码备份通过 Windows DPAPI 加密，仅生成它的 Windows 用户能够解密。请妥善保管固定密钥，后续版本需要它来保持覆盖安装能力。密钥库和密码不得提交到 Git。

## 当前范围

当前 0.3.2 已验证 React Native 三语主界面、五语翻译、系统选中文本，以及支持透明度和拖拽缩放的原生无障碍浮窗。浮窗及下拉菜单之外的区域保持可操作，不使用整屏背景模糊。尚未接入 Trime/Rime，也不会自动发送消息。
