"use client";

import { useRef, useState } from "react";
import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";

type UiLanguage = "zh" | "ja" | "en";
type LanguageCode = "zh" | "ja" | "en" | "ko" | "de";

const copy = {
  zh: {
    appLanguage: "界面语言",
    status: "浮窗服务已就绪",
    workspace: "翻译工作区",
    hint: "输入想表达的话，生成符合关系与场景的自然译文。",
    source: "源语言",
    target: "目标语言",
    relationship: "关系",
    scene: "场景",
    colleague: "同事",
    workChat: "工作聊天",
    inputLabel: "原文",
    input: "谢谢你今天帮了我，真的很开心。",
    tone: "语气控制",
    polite: "礼貌",
    close: "亲密",
    direct: "直接",
    generate: "生成关系语气译文",
    result: "推荐译文",
    recommended: "推荐",
    natural: "自然亲切",
    safe: "点击候选后才会替换原文，不会自动发送",
    backTranslation: "回译：谢谢你今天帮了我，我真的很开心。",
    settings: "API 与模型设置",
    overlay: "实时浮窗",
    overlayTitle: "ToneIME 实时翻译",
    overlayHint: "点击译文以替换输入框",
    overlayAdjust: "调节",
    overlayDisplay: "浮窗显示",
    overlayOpacity: "背景透明度",
    resizeHint: "拖动右下角调整大小",
    editState: "输入态",
    resultState: "结果态",
    previewTitle: "圆角界面提案",
    previewIntro: "减少表单感，突出“输入 → 调整 → 选择译文”的主路径。",
    previewState: "页面状态",
    overlayPreview: "显示浮窗预览",
    designNotes: ["核心操作集中在首屏", "浮窗右上角可调大小与透明度", "触控目标至少 44px", "主页面与浮窗共用视觉语言"],
  },
  ja: {
    appLanguage: "表示言語",
    status: "フローティング翻訳は準備完了",
    workspace: "翻訳ワークスペース",
    hint: "伝えたい内容を入力すると、関係性と場面に合う自然な訳文を作成します。",
    source: "原文の言語",
    target: "翻訳先",
    relationship: "関係",
    scene: "場面",
    colleague: "同僚",
    workChat: "仕事のチャット",
    inputLabel: "原文",
    input: "今日は手伝ってくれてありがとう。本当に嬉しかったです。",
    tone: "トーン調整",
    polite: "丁寧",
    close: "親しさ",
    direct: "率直",
    generate: "関係性に合う訳文を作成",
    result: "おすすめの訳文",
    recommended: "おすすめ",
    natural: "自然で親しみやすい",
    safe: "候補をタップした時だけ置き換えます。自動送信はしません",
    backTranslation: "逆翻訳：今日は手伝ってくれてありがとう。本当に嬉しかったです。",
    settings: "API・モデル設定",
    overlay: "リアルタイム表示",
    overlayTitle: "ToneIME リアルタイム翻訳",
    overlayHint: "訳文をタップして入力欄を置き換え",
    overlayAdjust: "調整",
    overlayDisplay: "表示設定",
    overlayOpacity: "背景の透明度",
    resizeHint: "右下をドラッグしてサイズ変更",
    editState: "入力",
    resultState: "結果",
    previewTitle: "角丸 UI の提案",
    previewIntro: "フォーム感を減らし、「入力 → 調整 → 選択」に集中させます。",
    previewState: "画面の状態",
    overlayPreview: "フローティング表示",
    designNotes: ["主要操作を最初の画面に集約", "右上からサイズと透明度を調整", "タップ領域は 44px 以上", "本体とフローティング表示を統一"],
  },
  en: {
    appLanguage: "Interface language",
    status: "Live overlay is ready",
    workspace: "Translation workspace",
    hint: "Write what you mean and get a natural translation for the relationship and situation.",
    source: "Source",
    target: "Target",
    relationship: "Relationship",
    scene: "Situation",
    colleague: "Colleague",
    workChat: "Work chat",
    inputLabel: "Original",
    input: "Thank you for helping me today. It made me really happy.",
    tone: "Tone controls",
    polite: "Polite",
    close: "Close",
    direct: "Direct",
    generate: "Create tone-aware translation",
    result: "Recommended translation",
    recommended: "Best fit",
    natural: "Warm and natural",
    safe: "Text is replaced only after you tap a candidate. Nothing is sent automatically.",
    backTranslation: "Back translation: Thank you for helping me today. I was really happy.",
    settings: "API & model settings",
    overlay: "Live overlay",
    overlayTitle: "ToneIME live translation",
    overlayHint: "Tap a translation to replace the input",
    overlayAdjust: "Adjust",
    overlayDisplay: "Overlay display",
    overlayOpacity: "Background opacity",
    resizeHint: "Drag the lower-right corner to resize",
    editState: "Compose",
    resultState: "Results",
    previewTitle: "Rounded UI proposal",
    previewIntro: "Less form-like, with a clear “write → tune → choose” path.",
    previewState: "Screen state",
    overlayPreview: "Show overlay preview",
    designNotes: ["Core actions stay above the fold", "Size and opacity live in the overlay", "Tap targets are at least 44px", "Main screen and overlay share one system"],
  },
} as const;

const languageNames: Record<UiLanguage, Record<LanguageCode, string>> = {
  zh: { zh: "中文", ja: "日语", en: "英语", ko: "韩语", de: "德语" },
  ja: { zh: "中国語", ja: "日本語", en: "英語", ko: "韓国語", de: "ドイツ語" },
  en: { zh: "Chinese", ja: "Japanese", en: "English", ko: "Korean", de: "German" },
};

const candidates = [
  "今日は手伝ってくれてありがとう。本当に嬉しかったです。",
  "今日は助けてくれて、本当にありがとう。とても嬉しかったです。",
] as const;

export default function Home() {
  const [uiLanguage, setUiLanguage] = useState<UiLanguage>("zh");
  const [sourceLanguage, setSourceLanguage] = useState<LanguageCode>("zh");
  const [targetLanguage, setTargetLanguage] = useState<LanguageCode>("ja");
  const [showResult, setShowResult] = useState(true);
  const [showOverlay, setShowOverlay] = useState(true);
  const [showOverlaySettings, setShowOverlaySettings] = useState(false);
  const [overlayOpacity, setOverlayOpacity] = useState(90);
  const [overlayBox, setOverlayBox] = useState<{
    left: number;
    top: number;
    width: number;
    height: number;
  } | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState(0);
  const overlayRef = useRef<HTMLElement>(null);
  const opacityPointer = useRef<number | null>(null);
  const resizeStart = useRef<{
    pointerId: number;
    x: number;
    y: number;
    left: number;
    top: number;
    width: number;
    height: number;
    maxWidth: number;
    maxHeight: number;
  } | null>(null);
  const t = copy[uiLanguage];

  function swapLanguages() {
    setSourceLanguage(targetLanguage);
    setTargetLanguage(sourceLanguage);
  }

  function changeSource(value: LanguageCode) {
    if (value === targetLanguage) setTargetLanguage(sourceLanguage);
    setSourceLanguage(value);
  }

  function changeTarget(value: LanguageCode) {
    if (value === sourceLanguage) setSourceLanguage(targetLanguage);
    setTargetLanguage(value);
  }

  function beginOverlayResize(event: ReactPointerEvent<HTMLButtonElement>) {
    const overlay = overlayRef.current;
    const screen = overlay?.parentElement;
    if (!overlay || !screen) return;

    const rect = overlay.getBoundingClientRect();
    const screenRect = screen.getBoundingClientRect();
    const left = rect.left - screenRect.left;
    const top = rect.top - screenRect.top;
    resizeStart.current = {
      pointerId: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      left,
      top,
      width: rect.width,
      height: rect.height,
      maxWidth: screenRect.width - left - 8,
      maxHeight: screenRect.height - top - 8,
    };
    setOverlayBox({ left, top, width: rect.width, height: rect.height });
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function resizeOverlay(event: ReactPointerEvent<HTMLButtonElement>) {
    const start = resizeStart.current;
    if (!start || start.pointerId !== event.pointerId) return;

    setOverlayBox({
      left: start.left,
      top: start.top,
      width: Math.min(start.maxWidth, Math.max(260, start.width + event.clientX - start.x)),
      height: Math.min(start.maxHeight, Math.max(150, start.height + event.clientY - start.y)),
    });
  }

  function endOverlayResize(event: ReactPointerEvent<HTMLButtonElement>) {
    if (resizeStart.current?.pointerId !== event.pointerId) return;
    resizeStart.current = null;
    event.currentTarget.releasePointerCapture(event.pointerId);
  }

  function setOpacityFromPointer(event: ReactPointerEvent<HTMLButtonElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    setOverlayOpacity(Math.round((35 + ratio * 65) / 5) * 5);
  }

  return (
    <main className="mock">
      <div className="wash wash-one" />
      <div className="wash wash-two" />

      <aside className="review-card" aria-label="Mock review controls">
        <div className="eyebrow">TONEIME · ANDROID</div>
        <h1>{t.previewTitle}</h1>
        <p>{t.previewIntro}</p>

        <div className="review-group">
          <span>{t.previewState}</span>
          <div className="segmented segmented-wide">
            <button className={!showResult ? "active" : ""} onClick={() => setShowResult(false)}>
              {t.editState}
            </button>
            <button className={showResult ? "active" : ""} onClick={() => setShowResult(true)}>
              {t.resultState}
            </button>
          </div>
        </div>

        <label className="switch-row">
          <span>{t.overlayPreview}</span>
          <input
            type="checkbox"
            checked={showOverlay}
            onChange={(event) => setShowOverlay(event.target.checked)}
          />
          <i aria-hidden="true" />
        </label>

        <ul>
          {t.designNotes.map((note) => <li key={note}>{note}</li>)}
        </ul>
        <div className="review-foot">Interactive HTML mock · v0.2</div>
      </aside>

      <section className="phone" aria-label="ToneIME Android mock">
        <div className="phone-speaker" />
        <div className="screen">
          <header className="app-header">
            <div className="brand">
              <span className="brand-mark">T</span>
              <div>
                <strong>ToneIME</strong>
                <span><i />{t.status}</span>
              </div>
            </div>
            <div className="segmented language-tabs" aria-label={t.appLanguage}>
              {(["zh", "ja", "en"] as UiLanguage[]).map((language) => (
                <button
                  key={language}
                  className={uiLanguage === language ? "active" : ""}
                  onClick={() => setUiLanguage(language)}
                >
                  {language === "zh" ? "中" : language === "ja" ? "日" : "EN"}
                </button>
              ))}
            </div>
          </header>

          <div className="screen-scroll">
            <section className="intro">
              <span className="section-kicker">01 · TRANSLATE</span>
              <h2>{t.workspace}</h2>
              <p>{t.hint}</p>
            </section>

            <section className="workspace-card">
              <div className="language-row">
                <label>
                  <span>{t.source}</span>
                  <select value={sourceLanguage} onChange={(event) => changeSource(event.target.value as LanguageCode)}>
                    {(Object.keys(languageNames[uiLanguage]) as LanguageCode[]).map((language) => (
                      <option key={language} value={language}>{languageNames[uiLanguage][language]}</option>
                    ))}
                  </select>
                </label>
                <button className="swap" onClick={swapLanguages} aria-label="Swap languages">⇄</button>
                <label>
                  <span>{t.target}</span>
                  <select value={targetLanguage} onChange={(event) => changeTarget(event.target.value as LanguageCode)}>
                    {(Object.keys(languageNames[uiLanguage]) as LanguageCode[]).map((language) => (
                      <option key={language} value={language}>{languageNames[uiLanguage][language]}</option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="context-row">
                <label>
                  <span>{t.relationship}</span>
                  <select defaultValue="colleague">
                    <option value="colleague">{t.colleague}</option>
                  </select>
                </label>
                <label>
                  <span>{t.scene}</span>
                  <select defaultValue="work">
                    <option value="work">{t.workChat}</option>
                  </select>
                </label>
              </div>

              <label className="input-block">
                <span>{t.inputLabel}</span>
                <textarea key={uiLanguage} defaultValue={t.input} />
                <small>23 / 2000</small>
              </label>
            </section>

            <section className="tone-card">
              <div className="card-title">
                <span>{t.tone}</span>
                <small>3 · 4 · 2</small>
              </div>
              {[
                [t.polite, 3],
                [t.close, 4],
                [t.direct, 2],
              ].map(([label, value]) => (
                <label className="range-row" key={String(label)}>
                  <span>{label}</span>
                  <input type="range" min="1" max="5" defaultValue={Number(value)} />
                  <b>{value}</b>
                </label>
              ))}
            </section>

            <button className="primary" onClick={() => setShowResult(true)}>
              <span>{t.generate}</span>
              <b>→</b>
            </button>

            {showResult && (
              <section className="result-card">
                <div className="card-title">
                  <span>{t.result}</span>
                  <small className="success-dot">✓</small>
                </div>
                <div className="candidate-list">
                  {candidates.map((candidate, index) => (
                    <button
                      key={candidate}
                      className={`candidate ${selectedCandidate === index ? "selected" : ""}`}
                      onClick={() => setSelectedCandidate(index)}
                    >
                      <span className="radio" />
                      <span>
                        <b>{candidate}</b>
                        <small>{index === 0 ? `${t.recommended} · ${t.natural}` : t.natural}</small>
                      </span>
                    </button>
                  ))}
                </div>
                <p className="back-translation">{t.backTranslation}</p>
              </section>
            )}

            <p className="safe-note"><span>⌁</span>{t.safe}</p>

            <details className="settings-card">
              <summary>{t.settings}<span>＋</span></summary>
              <div className="settings-body">
                <label>Endpoint<input defaultValue="https://api.openai.com/v1" /></label>
                <label>Model<input defaultValue="gpt-5.6-luna" /></label>
              </div>
            </details>
          </div>

          {showOverlay && (
            <section
              ref={overlayRef}
              className={`overlay-card ${showOverlaySettings ? "settings-open" : ""}`}
              style={{
                "--overlay-alpha": overlayOpacity / 100,
                ...(overlayBox && {
                  left: overlayBox.left,
                  top: overlayBox.top,
                  bottom: "auto",
                  width: overlayBox.width,
                  height: overlayBox.height,
                  transform: "none",
                }),
              } as CSSProperties}
            >
              <div className="overlay-top">
                <div>
                  <span className="overlay-icon">T</span>
                  <strong>{t.overlayTitle}</strong>
                </div>
                <div className="overlay-actions">
                  <button
                    className={showOverlaySettings ? "active" : ""}
                    onClick={() => setShowOverlaySettings(!showOverlaySettings)}
                    aria-label={t.overlayAdjust}
                    title={t.overlayAdjust}
                  >
                    ◐
                  </button>
                  <button onClick={() => setShowOverlay(false)} aria-label="Close overlay">×</button>
                </div>
              </div>
              {showOverlaySettings && (
                <div className="overlay-controls">
                  <div className="overlay-control-head">
                    <strong>{t.overlayDisplay}</strong>
                    <output>{overlayOpacity}%</output>
                  </div>
                  <div className="opacity-control">
                    <span>{t.overlayOpacity}</span>
                    <button
                      className="opacity-slider"
                      role="slider"
                      aria-label={t.overlayOpacity}
                      aria-valuemin={35}
                      aria-valuemax={100}
                      aria-valuenow={overlayOpacity}
                      style={{
                        "--slider-progress": `${((overlayOpacity - 35) / 65) * 100}%`,
                      } as CSSProperties}
                      onPointerDown={(event) => {
                        opacityPointer.current = event.pointerId;
                        event.currentTarget.setPointerCapture(event.pointerId);
                        setOpacityFromPointer(event);
                      }}
                      onPointerMove={(event) => {
                        if (opacityPointer.current === event.pointerId) {
                          setOpacityFromPointer(event);
                        }
                      }}
                      onPointerUp={(event) => {
                        opacityPointer.current = null;
                        event.currentTarget.releasePointerCapture(event.pointerId);
                      }}
                      onPointerCancel={(event) => {
                        opacityPointer.current = null;
                        event.currentTarget.releasePointerCapture(event.pointerId);
                      }}
                      onKeyDown={(event) => {
                        if (event.key === "ArrowLeft" || event.key === "ArrowDown") {
                          event.preventDefault();
                          setOverlayOpacity(Math.max(35, overlayOpacity - 5));
                        } else if (event.key === "ArrowRight" || event.key === "ArrowUp") {
                          event.preventDefault();
                          setOverlayOpacity(Math.min(100, overlayOpacity + 5));
                        } else if (event.key === "Home") {
                          event.preventDefault();
                          setOverlayOpacity(35);
                        } else if (event.key === "End") {
                          event.preventDefault();
                          setOverlayOpacity(100);
                        }
                      }}
                    >
                      <span className="opacity-track">
                        <i />
                        <b />
                      </span>
                    </button>
                  </div>
                  <small className="resize-hint">⌟ {t.resizeHint}</small>
                </div>
              )}
              <div className="overlay-meta">
                <span>{languageNames[uiLanguage][sourceLanguage]} → {languageNames[uiLanguage][targetLanguage]}</span>
                <span>{t.colleague}</span>
                <span>{t.workChat}</span>
              </div>
              <button className="overlay-result">
                <span>{candidates[selectedCandidate]}</span>
                <b>↗</b>
              </button>
              <small>{t.overlayHint}</small>
              <button
                className="resize-grip"
                aria-label={t.resizeHint}
                title={t.resizeHint}
                onPointerDown={beginOverlayResize}
                onPointerMove={resizeOverlay}
                onPointerUp={endOverlayResize}
                onPointerCancel={endOverlayResize}
              />
            </section>
          )}
        </div>
      </section>
    </main>
  );
}
