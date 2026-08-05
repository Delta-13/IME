# ToneIME

## 中文

ToneIME 是一个面向中文、日文与英文沟通的隐私优先翻译助手，提供 Windows 桌面端和 Android 应用。它以“用户确认后再使用”为原则：生成的译文可供复制、回填或分享，但不会自动发送。

### 功能

- 中日英三语界面；Android 还支持韩语和德语互译。
- 根据关系、场景、礼貌、亲密度与直接度生成更自然的候选译文。
- Windows 端支持 LINE 输入辅助、可选的本机 UI Automation / OCR 读取，以及日中侧栏翻译。
- Android 端支持系统“处理文本”、分享入口和可拖动、可缩放的无障碍实时浮窗。
- API Key 使用系统安全存储；正文、译文与截图不持久化；网络请求使用 HTTPS，并将 Chat Completions 请求设为 `store: false`。

### 快速开始

Windows（需要 .NET 10 SDK）：

```powershell
dotnet build -c Release
dotnet .\bin\Release\net10.0-windows10.0.22621.0\ToneIME.dll --self-test
```

Android（需要 Node.js 22.11+、JDK 17+、Android SDK Platform 36 和 Build Tools 36.0.0）：

```powershell
npm ci
npm run typecheck
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。详见 [Android 构建说明](android/README.md)。

## 日本語

ToneIME は、中国語・日本語・英語でのコミュニケーションを支援する、プライバシーを重視した翻訳アシスタントです。Windows デスクトップ版と Android アプリを提供します。翻訳結果はコピー、入力欄への反映、共有に利用できますが、自動送信は行いません。

### 主な機能

- 中国語・日本語・英語の UI。Android では韓国語・ドイツ語との相互翻訳にも対応。
- 関係性、場面、丁寧さ、親しみやすさ、直接性に基づいた自然な訳文候補。
- Windows では LINE 入力支援、任意で有効化できるローカル UI Automation / OCR 読み取り、日中サイドバー翻訳を提供。
- Android ではシステムのテキスト処理、共有メニュー、ドラッグ・リサイズ可能なアクセシビリティ浮動ウィンドウに対応。
- API キーは OS の安全なストレージで保護し、本文・翻訳文・スクリーンショットは保存しません。通信は HTTPS を使用し、Chat Completions には `store: false` を設定します。

### ビルド

Windows では .NET 10 SDK、Android では Node.js 22.11+、JDK 17+、Android SDK Platform 36、Build Tools 36.0.0 が必要です。上記の「快速开始」のコマンドで Windows のビルドと自己テスト、Android の型検査・単体テスト・lint・debug APK の作成を実行できます。

APK は `android/app/build/outputs/apk/debug/app-debug.apk` に出力されます。詳細は [Android ビルド手順](android/README.md) を参照してください。

## English

ToneIME is a privacy-first translation assistant for Chinese, Japanese, and English communication, with a Windows desktop app and an Android app. Translations can be copied, inserted into a text field, or shared only after the user chooses to do so; the app never sends messages automatically.

### Features

- Chinese, Japanese, and English interfaces; Android also translates to and from Korean and German.
- Natural translation candidates shaped by relationship, context, politeness, warmth, and directness.
- Windows support for LINE input assistance, opt-in local UI Automation / OCR reading, and a Japanese–Chinese translation sidebar.
- Android support for the system Process Text action, share targets, and a draggable, resizable accessibility overlay.
- API keys are stored securely by the operating system. Source text, translations, and screenshots are not persisted. Requests use HTTPS and Chat Completions requests set `store: false`.

### Build

Windows requires the .NET 10 SDK. Android requires Node.js 22.11+, JDK 17+, Android SDK Platform 36, and Build Tools 36.0.0. Run the commands in the Chinese quick-start section to build and self-test Windows, then type-check, test, lint, and assemble the Android debug APK.

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. See the [Android build guide](android/README.md) for details.

## License / 许可证 / ライセンス

This project is licensed under the [Apache License 2.0](LICENSE).
