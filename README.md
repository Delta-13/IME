# ToneIME

面向中文母语者的中日关系语气翻译助手。当前包含 Windows + LINE 自用版，以及 Android 自用 MVP：

- 使用现有系统中文输入法，不重造拼音内核。
- 中→日实时预览，明确确认后才回填，绝不自动发送。
- 长辈、领导、平辈、朋友、好朋友、情侣六种关系语气。
- 闲聊、请求、道歉、感谢、拒绝、关心六种场景，可自动建议或手动指定。
- 可选 LINE 会话读取：优先 UI Automation，失败后使用 Windows 本机 OCR，也可按次启用 OpenAI OCR。
- 日→中侧栏翻译，并可引用原消息起草回复。
- Android 支持中、日、英、韩、德五语互译，中、日、英三语界面，以及系统“处理文本”、分享入口和实时浮窗。

## 快速使用

1. 运行 `ToneIME.exe`。
2. 在“设置与隐私”填写：
   - OpenAI 兼容 API 的 Base URL，例如 `https://api.openai.com/v1`
   - 模型 ID；文字翻译优先 `gpt-5.6-luna`，Realtime 文字可选 `gpt-realtime-2.1-mini`
   - API Key
3. 点击“测试连接”，成功后保存。
4. 将光标放入 LINE 输入框，按 `Ctrl+Alt+J`。
5. 在 ToneIME 输入中文，检查候选与回译，点击“插入 LINE”。
6. 回到 LINE 后由你亲自发送。

如果自动回填失败，译文会复制到剪贴板供手动粘贴。

## LINE 读屏

读屏默认关闭。

1. 在设置页阅读说明并勾选读屏同意。
2. 打开 LINE 聊天窗口。
3. 点击“开始本次会话”使用本机读取，或点击“OpenAI OCR 本次会话”。
4. 如果 LINE 没有通过 UI Automation 暴露消息文本，停止读取，点击“框选 OCR 区域”，只框选聊天消息区，再重新开始。

保存的框选区域不在当前 LINE 窗口内时，程序会自动改用当前窗口右侧的聊天正文区，避免移动窗口或切换显示器后读到错误屏幕。

读屏开启期间：

- 始终显示读取状态。
- 只绑定一个 LINE 窗口或一个明确框选区域。
- 本机 OCR 截图只在内存中处理，不保存、不上传。
- 只有明确点击“OpenAI OCR 本次会话”时，框选截图才会发送到所配置 API，并固定使用 `gpt-5.6-luna`。
- 只有提取并去重后的新文字会发送给所配置的模型。
- 屏幕锁定、LINE 关闭或 30 分钟无新消息后自动停止。

## 自定义语气

关系决定默认社会距离，场景决定请求、道歉等具体表达方式。三个五级微调分别控制：

- 礼貌程度
- 亲密温度
- 表达直接度

还可逐行填写最多三条偏好示例和需要避免的表达。程序不会从聊天历史自动学习，也不会自动推断联系人关系。

## Android

Android 源码和构建说明位于 [`android`](android/README.md)。首版继续使用手机现有中文输入法，通过应用、系统选中文本或分享入口打开 ToneIME；确认候选后再复制或返回原应用，不会自动发送。

## 隐私边界

- API Key 保存在 Windows 凭据管理器的 `ToneIME/OpenAICompatibleApiKey` 项目中。
- 普通设置保存在 `%LOCALAPPDATA%\ToneIME\settings.json`。
- 程序不保存正文、译文或截图，也没有账号、数据库或自建后端。
- Chat Completions 请求会携带 `store: false`；Realtime 会话使用其专用隐私机制。供应商是否实际留存仍由你选择的 API 条款决定。
- 原文、引用内容和自定义示例均被标记为待翻译数据，不能覆盖系统翻译规则。

## 构建与自检

要求 Windows 10/11 和 .NET 10 SDK。

```powershell
dotnet build -c Release
dotnet .\bin\Release\net10.0-windows10.0.22621.0\ToneIME.dll --self-test
dotnet publish -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o publish
```

自检覆盖结构化响应解析、关键数字/URL 保留、消息去重、纯汉字日语检测和模型端点分流。

## 当前限制

- LINE 各版本暴露的可访问性文本不同，必要时必须重新框选 OCR 区域。
- 本机 OCR 依赖 Windows 的日语 OCR 语言组件；OpenAI OCR 不依赖该组件。
- 不能控制管理员权限高于 ToneIME 的窗口。
- Android 版不是完整输入法；尚未接入 Trime/Rime，实时浮窗只读取当前获得焦点的非密码输入框。
