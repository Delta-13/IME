<div align="center">

# 🗣️ ToneIME

### A privacy-first translation assistant for Chinese, Japanese, and English communication — on Windows and Android.

<a href="#english">English</a> · <a href="#简体中文">简体中文</a> · <a href="#日本語">日本語</a>

[![License](https://img.shields.io/badge/license-Apache--2.0-6f42c1?style=flat-square)](LICENSE)
![Platforms](https://img.shields.io/badge/platform-Windows%20%2B%20Android-0078D4?style=flat-square)
![Languages](https://img.shields.io/badge/interface-English%20%7C%20中文%20%7C%20日本語-2ea44f?style=flat-square)
![Privacy](https://img.shields.io/badge/privacy-user--confirmed-ff8c00?style=flat-square)

</div>

---

<a id="english"></a>

## English

### Overview

ToneIME is a privacy-first translation assistant for Chinese, Japanese, and English communication. It helps turn intent into natural phrasing that reflects relationship, context, politeness, warmth, and directness. It includes a Windows desktop app and an Android app, while keeping the final action with you: translations can be copied, inserted, or shared only after you confirm them. ToneIME never sends messages automatically.

> ToneIME is an independent project. It is not affiliated with LINE, OpenAI, or any API provider.

### Table of contents

- [Features](#features)
- [API providers](#api-providers)
- [Quick start](#quick-start)
- [Privacy and security](#privacy-and-security)
- [Project layout](#project-layout)
- [License](#license--许可证--ライセンス)

### Features

- **Context-aware translation** — choose relationship and scene, then tune politeness, warmth, and directness on five levels.
- **Windows assistant** — supports LINE input assistance, opt-in local UI Automation / OCR reading, and a Japanese–Chinese translation sidebar.
- **Android companion** — supports Process Text, share targets, and a draggable, resizable accessibility overlay.
- **Multilingual UI** — English, Simplified Chinese, and Japanese; Android also translates to and from Korean and German.
- **Provider choice** — preset support for OpenAI, Claude, Qwen, Kimi, MiniMax, DeepSeek, and Google AI (Gemini), plus a custom OpenAI-compatible HTTPS endpoint.
- **User control** — translated text is never sent automatically.

### API providers

Choose a provider in API settings; ToneIME fills an editable recommended endpoint and model. Claude uses its native Messages API. Qwen, Kimi, MiniMax, DeepSeek, Google AI (Gemini), and custom services use their OpenAI-compatible Chat Completions endpoints. Enter the key issued by the selected provider.

### Quick start

**Android download** — install the signed APK from the [latest GitHub Release](https://github.com/Delta-13/IME/releases/latest) (Android 8.0+). Releases are built, checked, and signed by [GitHub Actions](https://github.com/Delta-13/IME/actions/workflows/android-release.yml); Metro is not required. An older debug build must be uninstalled first, which clears its settings and saved API keys.

**Windows** — requires the .NET 10 SDK.

```powershell
dotnet build -c Release
dotnet .\bin\Release\net10.0-windows10.0.22621.0\ToneIME.dll --self-test
```

**Android** — requires Node.js 22.11+, JDK 17+, Android SDK Platform 36, and Build Tools 36.0.0.

```powershell
npm ci
npm run typecheck
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. See the [Android build guide](android/README.md) for details.

### Privacy and security

- API keys are isolated per provider and stored by the operating system: Windows Credential Manager on Windows and Android Keystore-backed AES-GCM storage on Android.
- API endpoints must use HTTPS; API keys are never sent over cleartext HTTP.
- Source text, translations, and screenshots are not persisted by the app.
- OpenAI requests set `store: false`; other providers receive only their documented request fields.
- Screen reading is opt-in, limited to the selected LINE window or region, and automatically stops when appropriate.

### Project layout

| Path | Purpose |
| --- | --- |
| `*.cs`, `*.xaml` | Windows desktop application |
| `android/` | React Native Android application and native overlay |
| `android/README.md` | Android-specific build and usage notes |

---

<a id="简体中文"></a>

## 简体中文

### 概览

ToneIME 是一个面向中文、日文与英文沟通的隐私优先翻译助手，包含 Windows 桌面端和 Android 应用。它会结合关系、场景、礼貌、亲密度与直接度生成更自然的表达；译文只能在你确认后复制、回填或分享，绝不会自动发送消息。

### 功能

- **贴合语境的翻译**：可选择关系和场景，并以五级调节礼貌、亲密度与直接度。
- **Windows 助手**：支持 LINE 输入辅助、按需启用的本机 UI Automation / OCR 读取，以及日中侧栏翻译。
- **Android 伴侣应用**：支持系统“处理文本”、分享入口与可拖动、可缩放的无障碍实时浮窗。
- **多语言界面**：提供英文、简体中文、日文界面；Android 还支持韩语和德语互译。
- **服务商选择**：内置 OpenAI、Claude、通义千问（Qwen）、Kimi、MiniMax、DeepSeek、Google AI（Gemini）预设，也支持自定义 OpenAI 兼容 HTTPS 端点。
- **始终由你决定**：译文不会被自动发送。

### API 服务商

在 API 设置中选择服务商后，ToneIME 会填入可编辑的推荐端点和模型。Claude 使用原生 Messages API；通义千问、Kimi、MiniMax、DeepSeek、Google AI（Gemini）与自定义服务使用 OpenAI 兼容的 Chat Completions 端点。请填写当前所选服务商签发的 API Key。

### 快速开始

**Android 下载**：在 [最新 GitHub Release](https://github.com/Delta-13/IME/releases/latest) 下载正式签名的 APK（Android 8.0+）。安装包由 [GitHub Actions](https://github.com/Delta-13/IME/actions/workflows/android-release.yml) 检查、构建并签名，无需 Metro。首次从旧调试版迁移时需要卸载旧版，这会清除其设置和保存的 API Key。

Windows 需要 .NET 10 SDK；Android 需要 Node.js 22.11+、JDK 17+、Android SDK Platform 36 和 Build Tools 36.0.0。请运行上方 English 部分的命令构建和验证。调试 APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`，具体说明见 [Android 构建指南](android/README.md)。

### 隐私与安全

- 每个服务商的 API Key 分开存储：Windows 使用凭据管理器，Android 使用 Android Keystore 支持的 AES-GCM 加密存储。
- 仅接受 HTTPS API 地址，不会通过明文 HTTP 发送 API Key。
- 程序不持久化原文、译文或截图；OpenAI 请求使用 `store: false`，其他服务商只接收其文档规定的字段。
- 读屏功能必须由你主动开启，仅限指定的 LINE 窗口或区域，并会在适当条件下自动停止。

---

<a id="日本語"></a>

## 日本語

### 概要

ToneIME は、中国語・日本語・英語でのやり取りを支援する、プライバシー重視の翻訳アシスタントです。Windows デスクトップ版と Android アプリを提供し、関係性、場面、丁寧さ、親しみやすさ、直接性に応じた自然な表現を提案します。翻訳結果のコピー、入力欄への反映、共有は必ずユーザーの確認後に行われ、自動送信はしません。

### 主な機能

- **文脈に応じた翻訳**：関係性と場面を選び、丁寧さ・親しみやすさ・直接性を 5 段階で調整できます。
- **Windows アシスタント**：LINE 入力支援、任意で有効化できるローカル UI Automation / OCR 読み取り、日中サイドバー翻訳に対応します。
- **Android コンパニオン**：システムのテキスト処理、共有メニュー、ドラッグ・リサイズ可能なアクセシビリティ浮動ウィンドウに対応します。
- **多言語 UI**：英語・簡体字中国語・日本語の UI を提供し、Android では韓国語・ドイツ語との相互翻訳にも対応します。
- **プロバイダー選択**：OpenAI、Claude、Qwen、Kimi、MiniMax、DeepSeek、Google AI（Gemini）のプリセットに加え、カスタム OpenAI 互換 HTTPS エンドポイントに対応します。
- **最終操作はユーザー自身で**：翻訳文が自動送信されることはありません。

### API プロバイダー

API 設定でプロバイダーを選択すると、編集可能な推奨エンドポイントとモデルが入力されます。Claude にはネイティブ Messages API を使用し、Qwen、Kimi、MiniMax、DeepSeek、Google AI（Gemini）、カスタムサービスには OpenAI 互換 Chat Completions エンドポイントを使用します。選択中のプロバイダーが発行した API Key を入力してください。

### はじめに

**Android のダウンロード**：[最新の GitHub Release](https://github.com/Delta-13/IME/releases/latest) から署名済み APK を入手できます（Android 8.0 以降）。[GitHub Actions](https://github.com/Delta-13/IME/actions/workflows/android-release.yml) で検証・ビルド・署名され、Metro は不要です。旧 debug 版から移行する際は旧版をアンインストールしてください。設定と保存済み API キーは削除されます。

Windows には .NET 10 SDK、Android には Node.js 22.11+、JDK 17+、Android SDK Platform 36、Build Tools 36.0.0 が必要です。ビルドと検証には English セクションのコマンドを実行してください。debug APK は `android/app/build/outputs/apk/debug/app-debug.apk` に出力されます。詳しくは [Android ビルドガイド](android/README.md) を参照してください。

### プライバシーとセキュリティ

- API キーはプロバイダーごとに分離して OS の安全なストレージに保存されます。Windows では資格情報マネージャー、Android では Android Keystore を利用した AES-GCM 暗号化ストレージを使用します。
- API エンドポイントは HTTPS のみを受け付け、API キーを平文 HTTP で送信しません。
- 原文、翻訳文、スクリーンショットはアプリ内に永続保存されません。OpenAI リクエストには `store: false` を設定し、他のプロバイダーには各 API の文書化されたフィールドだけを送信します。
- 画面読み取りは明示的な有効化が必要で、指定した LINE ウィンドウまたは範囲だけを対象にし、適切な条件で自動停止します。

---

## License / 许可证 / ライセンス

ToneIME is licensed under the [Apache License 2.0](LICENSE).
