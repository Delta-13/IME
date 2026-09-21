import React, {useEffect, useRef, useState} from 'react';
import {
  ActivityIndicator,
  AppState,
  Keyboard,
  Modal,
  NativeModules,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

type UiLanguage = 'zh' | 'ja' | 'en';
type LanguageCode = 'zh' | 'ja' | 'en' | 'ko' | 'de';
type Relation = 'elder' | 'boss' | 'peer' | 'friend' | 'close_friend' | 'partner';
type Scene = 'auto' | 'chat' | 'request' | 'apology' | 'thanks' | 'decline' | 'care';
type ProviderId =
  | 'openai'
  | 'claude'
  | 'qwen'
  | 'kimi'
  | 'minimax'
  | 'deepseek'
  | 'google'
  | 'custom';

type SettingsState = {
  uiLanguage: UiLanguage;
  sourceLanguage: LanguageCode;
  targetLanguage: LanguageCode;
  relation: Relation;
  scene: Scene;
  politeness: number;
  warmth: number;
  directness: number;
  overlayOpacity: number;
  provider: ProviderId;
  baseUrl: string;
  model: string;
};

type NativeState = SettingsState & {
  hasApiKey: boolean;
  accessibilityEnabled: boolean;
  incomingText: string;
  canReturnToApp: boolean;
};

type Candidate = {label: string; text: string};
type TranslationResult = {
  candidates: Candidate[];
  backTranslation: string;
  warnings: string[];
};

type ToneImeApi = {
  loadState(): Promise<NativeState>;
  setUiLanguage(language: UiLanguage): Promise<void>;
  changeLanguage(
    language: LanguageCode,
    sourceChanged: boolean,
  ): Promise<Pick<SettingsState, 'sourceLanguage' | 'targetLanguage'>>;
  saveSettings(settings: SettingsState, changedApiKey: string | null): Promise<void>;
  translate(
    request: SettingsState & {source: string},
    changedApiKey: string | null,
  ): Promise<TranslationResult>;
  cancelTranslation(): void;
  openAccessibilitySettings(): void;
  copyText(text: string): void;
  shareText(text: string): void;
  returnToApp(text: string): Promise<void>;
};

const ToneIme = NativeModules.ToneIme as ToneImeApi;

const HEADER_HEIGHT = 74 + (StatusBar.currentHeight ?? 0);

const DEFAULTS: SettingsState = {
  uiLanguage: 'zh',
  sourceLanguage: 'zh',
  targetLanguage: 'ja',
  relation: 'friend',
  scene: 'auto',
  politeness: 3,
  warmth: 3,
  directness: 3,
  overlayOpacity: 90,
  provider: 'openai',
  baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-5.6-luna',
};

const copy = {
  zh: {
    overlayReady: '浮窗服务已就绪',
    overlayOff: '浮窗服务未开启',
    workspace: '翻译工作区',
    hint: '输入想表达的话，生成符合关系与场景的自然译文。',
    source: '源语言',
    target: '目标语言',
    relation: '关系语气',
    scene: '对话场景',
    original: '输入原文',
    inputHint: '输入要翻译的文字，最多 800 字',
    tone: '语气控制',
    politeness: '礼貌',
    warmth: '亲密',
    directness: '直接',
    generate: '生成关系语气译文',
    translating: '正在生成关系语气译文…',
    ready: '等待输入 · 不会自动发送',
    complete: '译文已生成 · 请确认后操作',
    failed: '翻译失败，请重试。',
    minimum: '请输入至少 2 个字符。',
    https: 'API 地址必须使用 HTTPS。',
    modelKey: '请填写模型 ID 和 API Key。',
    result: '选择译文',
    recommended: '推荐',
    alternative: '候选',
    backTranslation: '回译检查',
    warnings: '提示',
    copy: '复制所选',
    copied: '译文已复制；请回到聊天应用手动发送',
    share: '打开分享菜单',
    returnToApp: '替换原文并返回',
    safe: '点击候选后才会替换原文，不会自动发送。',
    apiSettings: 'API 与模型设置',
    provider: '服务商',
    providerHint: '切换服务商会填入推荐端点和模型；请改用该服务商的 API Key。',
    baseUrl: 'Base URL',
    model: '模型 ID',
    apiKey: 'API Key',
    savedKey: '已安全保存；留空则保持不变',
    apiKeyHint: '使用 Android Keystore 加密',
    save: '保存设置',
    saved: '设置已保存',
    saveFailed: '设置保存失败，请重试。',
    overlayButton: '开启 / 管理实时浮窗',
    privacy: '仅读取当前获得焦点的非密码输入框；停顿约 0.45 秒后翻译。必须点击译文才会替换，绝不自动发送。',
    overlayDisplay: '浮窗显示',
    overlayOpacity: '透明度',
    overlaySizeHint: '在浮窗右下角拖动可调整大小；内容会随宽度自动收纳。',
    select: '选择',
    close: '关闭',
    notAvailable: '—',
  },
  ja: {
    overlayReady: 'フローティング翻訳は準備完了',
    overlayOff: 'フローティング翻訳はオフ',
    workspace: '翻訳ワークスペース',
    hint: '伝えたい内容を入力すると、関係性と場面に合う自然な訳文を作成します。',
    source: '原文の言語',
    target: '翻訳先',
    relation: '関係性',
    scene: '場面',
    original: '原文',
    inputHint: '翻訳する文章を入力（最大800文字）',
    tone: 'トーン調整',
    politeness: '丁寧',
    warmth: '親しさ',
    directness: '率直',
    generate: '関係性に合う訳文を作成',
    translating: '訳文を作成しています…',
    ready: '入力待ち · 自動送信しません',
    complete: '訳文を作成しました · 内容をご確認ください',
    failed: '翻訳に失敗しました。もう一度お試しください。',
    minimum: '2文字以上入力してください。',
    https: 'API URL は HTTPS が必要です。',
    modelKey: 'モデル ID と API Key を入力してください。',
    result: '訳文を選択',
    recommended: 'おすすめ',
    alternative: '候補',
    backTranslation: '逆翻訳',
    warnings: '注意',
    copy: '選択した訳文をコピー',
    copied: '訳文をコピーしました。チャットアプリで手動送信してください',
    share: '共有メニューを開く',
    returnToApp: '原文を置き換えて戻る',
    safe: '候補をタップした時だけ置き換えます。自動送信はしません。',
    apiSettings: 'API・モデル設定',
    provider: 'プロバイダー',
    providerHint: '切り替えると推奨エンドポイントとモデルが入ります。対応する API Key を入力してください。',
    baseUrl: 'Base URL',
    model: 'モデル ID',
    apiKey: 'API Key',
    savedKey: '安全に保存済み。空欄のままなら変更しません',
    apiKeyHint: 'Android Keystore で暗号化',
    save: '設定を保存',
    saved: '設定を保存しました',
    saveFailed: '設定を保存できませんでした。',
    overlayButton: 'リアルタイム表示を設定',
    privacy: '現在フォーカス中のパスワード以外の入力欄だけを読み取り、約0.45秒後に翻訳します。訳文をタップした時だけ置き換え、自動送信はしません。',
    overlayDisplay: 'フローティング表示',
    overlayOpacity: '透明度',
    overlaySizeHint: '右下をドラッグしてサイズ変更できます。幅に合わせて内容を収めます。',
    select: '選択',
    close: '閉じる',
    notAvailable: '—',
  },
  en: {
    overlayReady: 'Live overlay is ready',
    overlayOff: 'Live overlay is off',
    workspace: 'Translation workspace',
    hint: 'Write what you mean and get a natural translation for the relationship and situation.',
    source: 'Source',
    target: 'Target',
    relation: 'Relationship',
    scene: 'Situation',
    original: 'Original',
    inputHint: 'Enter up to 800 characters',
    tone: 'Tone controls',
    politeness: 'Polite',
    warmth: 'Close',
    directness: 'Direct',
    generate: 'Create tone-aware translation',
    translating: 'Creating a tone-aware translation…',
    ready: 'Waiting for input · Never auto-sends',
    complete: 'Translation ready · Review before using',
    failed: 'Translation failed. Please try again.',
    minimum: 'Enter at least 2 characters.',
    https: 'The API URL must use HTTPS.',
    modelKey: 'Enter a model ID and API key.',
    result: 'Choose a translation',
    recommended: 'Best fit',
    alternative: 'Alternative',
    backTranslation: 'Back translation',
    warnings: 'Notes',
    copy: 'Copy selected',
    copied: 'Translation copied; return to the chat app and send it manually',
    share: 'Open share menu',
    returnToApp: 'Replace original and return',
    safe: 'Text is replaced only after you tap a candidate. Nothing is sent automatically.',
    apiSettings: 'API & model settings',
    provider: 'Provider',
    providerHint: 'Switching fills the recommended endpoint and model. Enter that provider’s API key.',
    baseUrl: 'Base URL',
    model: 'Model ID',
    apiKey: 'API Key',
    savedKey: 'Saved securely; leave blank to keep it',
    apiKeyHint: 'Encrypted with Android Keystore',
    save: 'Save settings',
    saved: 'Settings saved',
    saveFailed: 'Could not save settings. Please try again.',
    overlayButton: 'Enable / manage live overlay',
    privacy: 'Only the focused, non-password input is read and translated after about 0.45 seconds. Text changes only when you tap a translation and is never auto-sent.',
    overlayDisplay: 'Overlay display',
    overlayOpacity: 'Opacity',
    overlaySizeHint: 'Drag the lower-right corner to resize; content adapts to the available width.',
    select: 'Select',
    close: 'Close',
    notAvailable: '—',
  },
} as const;

const languageNames: Record<UiLanguage, Record<LanguageCode, string>> = {
  zh: {zh: '中文', ja: '日文', en: '英文', ko: '韩文', de: '德文'},
  ja: {zh: '中国語', ja: '日本語', en: '英語', ko: '韓国語', de: 'ドイツ語'},
  en: {zh: 'Chinese', ja: 'Japanese', en: 'English', ko: 'Korean', de: 'German'},
};

const relationNames: Record<UiLanguage, Record<Relation, string>> = {
  zh: {elder: '长辈', boss: '领导', peer: '平辈', friend: '朋友', close_friend: '好朋友', partner: '情侣'},
  ja: {elder: '年長者', boss: '上司', peer: '同輩', friend: '友人', close_friend: '親友', partner: '恋人'},
  en: {elder: 'Elder', boss: 'Manager', peer: 'Peer', friend: 'Friend', close_friend: 'Close friend', partner: 'Partner'},
};

const sceneNames: Record<UiLanguage, Record<Scene, string>> = {
  zh: {auto: '自动', chat: '闲聊', request: '请求', apology: '道歉', thanks: '感谢', decline: '拒绝', care: '关心'},
  ja: {auto: '自動', chat: '雑談', request: '依頼', apology: '謝罪', thanks: '感謝', decline: '断り', care: '気遣い'},
  en: {auto: 'Auto', chat: 'Chat', request: 'Request', apology: 'Apology', thanks: 'Thanks', decline: 'Decline', care: 'Care'},
};

const providerPresets: Record<ProviderId, {baseUrl: string; model: string}> = {
  openai: {baseUrl: 'https://api.openai.com/v1', model: 'gpt-5.6-luna'},
  claude: {baseUrl: 'https://api.anthropic.com/v1', model: 'claude-sonnet-4-5'},
  qwen: {baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus'},
  kimi: {baseUrl: 'https://api.moonshot.ai/v1', model: 'kimi-k3'},
  minimax: {baseUrl: 'https://api.minimax.io/v1', model: 'MiniMax-M2.7'},
  deepseek: {baseUrl: 'https://api.deepseek.com', model: 'deepseek-v4-flash'},
  google: {
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    model: 'gemini-3.6-flash',
  },
  custom: {baseUrl: 'https://api.openai.com/v1', model: 'gpt-5.6-luna'},
};

const providerNames: Record<UiLanguage, Record<ProviderId, string>> = {
  zh: {
    openai: 'OpenAI',
    claude: 'Claude（Anthropic）',
    qwen: '通义千问（Qwen）',
    kimi: 'Kimi（月之暗面）',
    minimax: 'MiniMax',
    deepseek: 'DeepSeek',
    google: 'Google AI（Gemini）',
    custom: '自定义 OpenAI 兼容服务',
  },
  ja: {
    openai: 'OpenAI',
    claude: 'Claude (Anthropic)',
    qwen: 'Qwen（通義千問）',
    kimi: 'Kimi（月之暗面）',
    minimax: 'MiniMax',
    deepseek: 'DeepSeek',
    google: 'Google AI (Gemini)',
    custom: 'カスタム OpenAI 互換サービス',
  },
  en: {
    openai: 'OpenAI',
    claude: 'Claude (Anthropic)',
    qwen: 'Qwen',
    kimi: 'Kimi',
    minimax: 'MiniMax',
    deepseek: 'DeepSeek',
    google: 'Google AI (Gemini)',
    custom: 'Custom OpenAI-compatible API',
  },
};

const languageCodes = Object.keys(languageNames.zh) as LanguageCode[];
const relations = Object.keys(relationNames.zh) as Relation[];
const scenes = Object.keys(sceneNames.zh) as Scene[];
const providerIds = Object.keys(providerPresets) as ProviderId[];

type Option<T extends string> = {value: T; label: string};

function SelectField<T extends string>({
  label,
  value,
  options,
  onChange,
  testID,
}: {
  label: string;
  value: T;
  options: Option<T>[];
  onChange: (value: T) => void;
  testID?: string;
}) {
  const [open, setOpen] = useState(false);
  const selected = options.find(option => option.value === value)?.label ?? value;
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${selected}`}
        onPress={() => setOpen(true)}
        style={({pressed}) => [styles.select, pressed && styles.pressed]}
        testID={testID}>
        <Text numberOfLines={1} style={styles.selectText}>{selected}</Text>
        <Text style={styles.chevron}>⌄</Text>
      </Pressable>
      <Modal
        animationType="fade"
        onRequestClose={() => setOpen(false)}
        transparent
        visible={open}>
        <Pressable style={styles.modalBackdrop} onPress={() => setOpen(false)}>
          <View style={styles.optionSheet}>
            <View style={styles.optionHeader}>
              <Text style={styles.optionTitle}>{label}</Text>
              <Pressable
                accessibilityLabel="Close"
                hitSlop={10}
                onPress={() => setOpen(false)}>
                <Text style={styles.optionClose}>×</Text>
              </Pressable>
            </View>
            {options.map(option => (
              <Pressable
                accessibilityRole="radio"
                accessibilityState={{checked: option.value === value}}
                key={option.value}
                onPress={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
                style={({pressed}) => [
                  styles.option,
                  option.value === value && styles.optionSelected,
                  pressed && styles.pressed,
                ]}>
                <Text style={[
                  styles.optionText,
                  option.value === value && styles.optionTextSelected,
                ]}>
                  {option.label}
                </Text>
                {option.value === value && <Text style={styles.optionCheck}>✓</Text>}
              </Pressable>
            ))}
          </View>
        </Pressable>
      </Modal>
    </View>
  );
}

function RangeSlider({
  label,
  value,
  min,
  max,
  valueLabel = String(value),
  onChange,
  onComplete,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  valueLabel?: string;
  onChange: (value: number) => void;
  onComplete: (value: number) => void;
}) {
  const width = useRef(1);
  const valueFromX = (x: number) =>
    Math.max(min, Math.min(max, Math.round((x / width.current) * (max - min)) + min));
  const progress = ((value - min) / (max - min)) * 100;
  return (
    <View style={styles.rangeRow}>
      <Text style={styles.rangeLabel}>{label}</Text>
      <View
        accessibilityActions={[{name: 'increment'}, {name: 'decrement'}]}
        accessibilityRole="adjustable"
        accessibilityValue={{min, max, now: value, text: valueLabel}}
        onAccessibilityAction={event => {
          const next = event.nativeEvent.actionName === 'increment'
            ? Math.min(max, value + 1)
            : Math.max(min, value - 1);
          onChange(next);
          onComplete(next);
        }}
        onLayout={event => {
          width.current = event.nativeEvent.layout.width;
        }}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={event => onChange(valueFromX(event.nativeEvent.locationX))}
        onResponderMove={event => onChange(valueFromX(event.nativeEvent.locationX))}
        onResponderRelease={event => {
          const next = valueFromX(event.nativeEvent.locationX);
          onChange(next);
          onComplete(next);
        }}
        onStartShouldSetResponder={() => true}
        style={styles.rangeTouch}>
        <View style={styles.rangeTrack}>
          <View style={[styles.rangeFill, {width: `${progress}%`}]} />
          <View style={[styles.rangeThumb, {left: `${progress}%`}]} />
        </View>
      </View>
      <Text style={[styles.rangeValue, max > 5 && styles.rangeValueWide]}>{valueLabel}</Text>
    </View>
  );
}

function ToneSlider(props: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  onComplete: (value: number) => void;
}) {
  return <RangeSlider {...props} min={1} max={5} />;
}

export default function App() {
  const [settings, setSettings] = useState<SettingsState>(DEFAULTS);
  const [source, setSource] = useState('');
  const [hasApiKey, setHasApiKey] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [apiKeyChanged, setApiKeyChanged] = useState(false);
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(false);
  const [canReturnToApp, setCanReturnToApp] = useState(false);
  const [apiExpanded, setApiExpanded] = useState(true);
  const [busy, setBusy] = useState(false);
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [status, setStatus] = useState<'ready' | 'translating' | 'complete' | 'error'>('ready');
  const [message, setMessage] = useState('');
  const [result, setResult] = useState<TranslationResult | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState(0);
  const loaded = useRef(false);
  const scrollView = useRef<ScrollView>(null);
  const sourceInput = useRef<TextInput>(null);
  const baseUrlInput = useRef<TextInput>(null);
  const modelInput = useRef<TextInput>(null);
  const apiKeyInput = useRef<TextInput>(null);
  const focusedInput = useRef<TextInput | null>(null);
  const t = copy[settings.uiLanguage];
  const providerOptions = providerIds.map(value => ({
    value,
    label: providerNames[settings.uiLanguage][value],
  }));
  const selectedText = result?.candidates[selectedCandidate]?.text ?? '';

  useEffect(() => {
    ToneIme.loadState()
      .then(state => {
        setSettings({
          uiLanguage: state.uiLanguage,
          sourceLanguage: state.sourceLanguage,
          targetLanguage: state.targetLanguage,
          relation: state.relation,
          scene: state.scene,
          politeness: state.politeness,
          warmth: state.warmth,
          directness: state.directness,
          overlayOpacity: state.overlayOpacity,
          provider: state.provider,
          baseUrl: state.baseUrl,
          model: state.model,
        });
        setSource(state.incomingText);
        setHasApiKey(state.hasApiKey);
        setAccessibilityEnabled(state.accessibilityEnabled);
        setCanReturnToApp(state.canReturnToApp);
        setApiExpanded(!state.hasApiKey);
        loaded.current = true;
      })
      .catch(() => {
        setMessage(copy.zh.saveFailed);
        setStatus('error');
      });

    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active' && loaded.current) {
        ToneIme.loadState()
          .then(state => setAccessibilityEnabled(state.accessibilityEnabled))
          .catch(() => undefined);
      }
    });
    return () => subscription.remove();
  }, []);

  const save = async (
    next: SettingsState = settings,
    includeChangedKey = false,
  ) => {
    await ToneIme.saveSettings(
      next,
      includeChangedKey && apiKeyChanged ? apiKey : null,
    );
    if (includeChangedKey && apiKeyChanged) {
      setHasApiKey(apiKey.trim().length > 0);
      setApiKeyChanged(false);
    }
  };

  const saveQuietly = (next: SettingsState) => {
    void save(next).catch(() => undefined);
  };

  const scrollFocusedInputIntoView = () => {
    const input = focusedInput.current;
    if (input == null) {
      return;
    }
    scrollView.current?.scrollResponderScrollNativeHandleToKeyboard(
      input,
      HEADER_HEIGHT + 12,
      true,
    );
  };

  const focusInput = (input: TextInput | null) => {
    focusedInput.current = input;
    requestAnimationFrame(scrollFocusedInputIntoView);
    setTimeout(scrollFocusedInputIntoView, 250);
  };

  const blurInput = (input: TextInput | null) => {
    if (focusedInput.current === input) {
      focusedInput.current = null;
    }
  };

  useEffect(() => {
    const shown = Keyboard.addListener('keyboardDidShow', event => {
      setKeyboardHeight(event.endCoordinates.height);
    });
    const hidden = Keyboard.addListener('keyboardDidHide', () => {
      setKeyboardHeight(0);
    });
    return () => {
      shown.remove();
      hidden.remove();
    };
  }, []);

  useEffect(() => {
    if (keyboardHeight > 0) {
      requestAnimationFrame(scrollFocusedInputIntoView);
    }
  }, [keyboardHeight]);

  const updateSetting = <K extends keyof SettingsState>(
    key: K,
    value: SettingsState[K],
    persist = false,
  ) => {
    const next = {...settings, [key]: value};
    setSettings(next);
    if (persist) {
      saveQuietly(next);
    }
  };

  const clearResult = () => {
    ToneIme.cancelTranslation();
    setBusy(false);
    setResult(null);
    setSelectedCandidate(0);
    setMessage('');
    setStatus('ready');
  };

  const changeProvider = (provider: ProviderId) => {
    if (provider === settings.provider) {
      return;
    }
    const preset = providerPresets[provider];
    setSettings(current => ({
      ...current,
      provider,
      baseUrl: preset.baseUrl,
      model: preset.model,
    }));
    setApiKey('');
    setApiKeyChanged(false);
    setHasApiKey(false);
    clearResult();
  };

  const changeUiLanguage = (language: UiLanguage) => {
    if (language === settings.uiLanguage) {
      return;
    }
    setSettings(current => ({...current, uiLanguage: language}));
    clearResult();
    void ToneIme.setUiLanguage(language).catch(() => {
      setMessage(copy[language].saveFailed);
      setStatus('error');
    });
  };

  const changeLanguage = (language: LanguageCode, sourceChanged: boolean) => {
    clearResult();
    void ToneIme.changeLanguage(language, sourceChanged)
      .then(pair => setSettings(current => ({...current, ...pair})))
      .catch(() => {
        setMessage(t.saveFailed);
        setStatus('error');
      });
  };

  const translate = async () => {
    if (source.trim().length < 2) {
      setMessage(t.minimum);
      setStatus('error');
      return;
    }
    setBusy(true);
    setResult(null);
    setMessage('');
    setStatus('translating');
    try {
      const translation = await ToneIme.translate(
        {...settings, source},
        apiKeyChanged ? apiKey : null,
      );
      if (apiKeyChanged) {
        setHasApiKey(apiKey.trim().length > 0);
        setApiKeyChanged(false);
      }
      setResult(translation);
      setSelectedCandidate(0);
      setHasApiKey(true);
      setStatus('complete');
    } catch (error) {
      const code = (error as {code?: string}).code;
      if (code === 'E_CANCELLED') {
        return;
      }
      setMessage(
        code === 'E_MINIMUM_INPUT' || code === 'E_INPUT_TOO_LONG'
          ? t.minimum
          : code === 'E_HTTPS_REQUIRED'
            ? t.https
            : code === 'E_SAVE_SETTINGS'
              ? settings.baseUrl.startsWith('https://') ? t.saveFailed : t.https
            : code === 'E_MODEL_AND_KEY'
              ? t.modelKey
              : t.failed,
      );
      setStatus('error');
    } finally {
      setBusy(false);
    }
  };

  const saveApiSettings = async () => {
    try {
      await save(settings, true);
      void ToneIme.loadState()
        .then(state => setHasApiKey(state.hasApiKey))
        .catch(() => undefined);
      setMessage(t.saved);
      setStatus('complete');
    } catch {
      setMessage(settings.baseUrl.startsWith('https://') ? t.saveFailed : t.https);
      setStatus('error');
    }
  };

  const openOverlay = async () => {
    try {
      await save(settings, true);
      ToneIme.openAccessibilitySettings();
    } catch {
      setMessage(t.saveFailed);
      setStatus('error');
    }
  };

  const languageOptions = languageCodes.map(value => ({
    value,
    label: languageNames[settings.uiLanguage][value],
  }));
  const relationOptions = relations.map(value => ({
    value,
    label: relationNames[settings.uiLanguage][value],
  }));
  const sceneOptions = scenes.map(value => ({
    value,
    label: sceneNames[settings.uiLanguage][value],
  }));

  return (
    <View style={styles.app}>
      <StatusBar backgroundColor="#F7F7FA" barStyle="dark-content" />
      <View style={styles.header}>
        <View style={styles.brand}>
          <View style={styles.brandMark}><Text style={styles.brandLetter}>T</Text></View>
          <View style={styles.brandCopy}>
            <Text style={styles.brandName}>ToneIME</Text>
            <View style={styles.serviceStatus}>
              <View style={[
                styles.statusDot,
                !accessibilityEnabled && styles.statusDotOff,
              ]} />
              <Text numberOfLines={1} style={styles.serviceStatusText}>
                {accessibilityEnabled ? t.overlayReady : t.overlayOff}
              </Text>
            </View>
          </View>
        </View>
        <View accessibilityRole="tablist" style={styles.languageTabs}>
          {(['zh', 'ja', 'en'] as UiLanguage[]).map(language => (
            <Pressable
              accessibilityRole="tab"
              accessibilityState={{selected: settings.uiLanguage === language}}
              key={language}
              onPress={() => changeUiLanguage(language)}
              style={[
                styles.languageTab,
                settings.uiLanguage === language && styles.languageTabActive,
              ]}
              testID={`ui-language-${language}`}>
              <Text style={[
                styles.languageTabText,
                settings.uiLanguage === language && styles.languageTabTextActive,
              ]}>
                {language === 'zh' ? '中' : language === 'ja' ? '日' : 'EN'}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <ScrollView
        contentContainerStyle={[
          styles.content,
          keyboardHeight > 0 && {paddingBottom: keyboardHeight + 24},
        ]}
        keyboardShouldPersistTaps="handled"
        ref={scrollView}
        showsVerticalScrollIndicator={false}>
        <View style={styles.contentInner}>
          <Text style={styles.kicker}>01 · TRANSLATE</Text>
          <Text style={styles.title}>{t.workspace}</Text>
          <Text style={styles.subtitle}>{t.hint}</Text>

          <View style={styles.card}>
            <View style={styles.languageRow}>
              <View style={styles.languageField}>
                <SelectField
                  label={t.source}
                  onChange={value => changeLanguage(value, true)}
                  options={languageOptions}
                  testID="source-language"
                  value={settings.sourceLanguage}
                />
              </View>
              <Pressable
                accessibilityLabel={`${t.source} / ${t.target}`}
                onPress={() => changeLanguage(settings.targetLanguage, true)}
                style={({pressed}) => [styles.swap, pressed && styles.pressed]}
                testID="swap-languages">
                <Text style={styles.swapText}>⇄</Text>
              </Pressable>
              <View style={styles.languageField}>
                <SelectField
                  label={t.target}
                  onChange={value => changeLanguage(value, false)}
                  options={languageOptions}
                  testID="target-language"
                  value={settings.targetLanguage}
                />
              </View>
            </View>

            <View style={styles.contextRow}>
              <View style={styles.contextField}>
                <SelectField
                  label={t.relation}
                  onChange={value => updateSetting('relation', value, true)}
                  options={relationOptions}
                  value={settings.relation}
                />
              </View>
              <View style={styles.contextField}>
                <SelectField
                  label={t.scene}
                  onChange={value => updateSetting('scene', value, true)}
                  options={sceneOptions}
                  value={settings.scene}
                />
              </View>
            </View>

            <Text style={[styles.fieldLabel, styles.inputLabel]}>{t.original}</Text>
            <View style={styles.inputShell}>
              <TextInput
                accessibilityLabel={t.original}
                maxLength={800}
                multiline
                onBlur={() => blurInput(sourceInput.current)}
                onChangeText={text => {
                  setSource(text);
                  if (result) {
                    clearResult();
                  }
                }}
                onFocus={() => focusInput(sourceInput.current)}
                placeholder={t.inputHint}
                placeholderTextColor="#A0A1AF"
                ref={sourceInput}
                style={styles.sourceInput}
                testID="source-input"
                textAlignVertical="top"
                value={source}
              />
              <Text style={styles.counter}>{source.length} / 800</Text>
            </View>
          </View>

          <View style={[styles.card, styles.toneCard]}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{t.tone}</Text>
              <Text style={styles.cardMeta}>
                {settings.politeness} · {settings.warmth} · {settings.directness}
              </Text>
            </View>
            <ToneSlider
              label={t.politeness}
              onChange={value => updateSetting('politeness', value)}
              onComplete={value => {
                const next = {...settings, politeness: value};
                setSettings(next);
                saveQuietly(next);
              }}
              value={settings.politeness}
            />
            <ToneSlider
              label={t.warmth}
              onChange={value => updateSetting('warmth', value)}
              onComplete={value => {
                const next = {...settings, warmth: value};
                setSettings(next);
                saveQuietly(next);
              }}
              value={settings.warmth}
            />
            <ToneSlider
              label={t.directness}
              onChange={value => updateSetting('directness', value)}
              onComplete={value => {
                const next = {...settings, directness: value};
                setSettings(next);
                saveQuietly(next);
              }}
              value={settings.directness}
            />
          </View>

          <Pressable
            accessibilityState={{disabled: busy}}
            disabled={busy}
            onPress={translate}
            style={({pressed}) => [
              styles.primary,
              pressed && styles.primaryPressed,
              busy && styles.disabled,
            ]}
            testID="translate-button">
            <Text style={styles.primaryText}>
              {busy ? t.translating : t.generate}
            </Text>
            <View style={styles.primaryIcon}>
              {busy
                ? <ActivityIndicator color="#FFFFFF" size="small" />
                : <Text style={styles.primaryArrow}>→</Text>}
            </View>
          </Pressable>

          <View style={[
            styles.status,
            status === 'error' && styles.statusError,
          ]}>
            <View style={[
              styles.statusDot,
              status === 'error' && styles.statusDotError,
            ]} />
            <Text style={[
              styles.statusText,
              status === 'error' && styles.statusTextError,
            ]}>
              {message || (
                status === 'translating' ? t.translating :
                status === 'complete' ? t.complete :
                t.ready
              )}
            </Text>
          </View>

          {result && (
            <View style={[styles.card, styles.resultCard]}>
              <View style={styles.cardHeader}>
                <Text style={styles.cardTitle}>{t.result}</Text>
                <View style={styles.successBadge}><Text style={styles.successText}>✓</Text></View>
              </View>
              {result.candidates.map((candidate, index) => (
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{checked: selectedCandidate === index}}
                  key={`${candidate.text}-${index}`}
                  onPress={() => setSelectedCandidate(index)}
                  style={[
                    styles.candidate,
                    selectedCandidate === index && styles.candidateSelected,
                  ]}>
                  <View style={[
                    styles.radio,
                    selectedCandidate === index && styles.radioSelected,
                  ]} />
                  <View style={styles.candidateCopy}>
                    <Text style={styles.candidateText}>{candidate.text}</Text>
                    <Text style={styles.candidateLabel}>
                      {index === 0 ? t.recommended : candidate.label || t.alternative}
                    </Text>
                  </View>
                </Pressable>
              ))}
              <Text style={styles.resultLabel}>{t.backTranslation}</Text>
              <Text style={styles.resultDetail}>
                {result.backTranslation || t.notAvailable}
              </Text>
              {result.warnings.length > 0 && (
                <>
                  <Text style={styles.resultLabel}>{t.warnings}</Text>
                  <Text style={[styles.resultDetail, styles.warning]}>
                    {result.warnings.join('\n')}
                  </Text>
                </>
              )}
              <View style={styles.actionRow}>
                <Pressable
                  onPress={() => {
                    ToneIme.copyText(selectedText);
                    setMessage(t.copied);
                    setStatus('complete');
                  }}
                  style={({pressed}) => [styles.secondary, pressed && styles.pressed]}>
                  <Text style={styles.secondaryText}>{t.copy}</Text>
                </Pressable>
                <Pressable
                  onPress={() => ToneIme.shareText(selectedText)}
                  style={({pressed}) => [styles.secondary, pressed && styles.pressed]}>
                  <Text style={styles.secondaryText}>{t.share}</Text>
                </Pressable>
              </View>
              {canReturnToApp && (
                <Pressable
                  onPress={() => void ToneIme.returnToApp(selectedText)}
                  style={({pressed}) => [
                    styles.returnButton,
                    pressed && styles.primaryPressed,
                  ]}>
                  <Text style={styles.returnText}>{t.returnToApp}</Text>
                </Pressable>
              )}
            </View>
          )}

          <View style={styles.safeNote}>
            <Text style={styles.safeIcon}>⌁</Text>
            <Text style={styles.safeText}>{t.safe}</Text>
          </View>

          <Pressable
            onPress={openOverlay}
            style={({pressed}) => [styles.overlayButton, pressed && styles.pressed]}
            testID="overlay-settings-button">
            <View style={styles.overlayButtonIcon}><Text style={styles.overlayButtonLetter}>T</Text></View>
            <View style={styles.overlayButtonCopy}>
              <Text style={styles.overlayButtonTitle}>{t.overlayButton}</Text>
              <Text style={styles.overlayButtonHint}>{t.privacy}</Text>
            </View>
            <Text style={styles.overlayButtonArrow}>›</Text>
          </Pressable>

          <View style={[styles.card, styles.overlayDisplayCard]}>
            <Text style={styles.cardTitle}>{t.overlayDisplay}</Text>
            <RangeSlider
              label={t.overlayOpacity}
              max={100}
              min={35}
              onChange={value => updateSetting('overlayOpacity', value)}
              onComplete={value => {
                const next = {...settings, overlayOpacity: value};
                setSettings(next);
                saveQuietly(next);
              }}
              value={settings.overlayOpacity}
              valueLabel={`${settings.overlayOpacity}%`}
            />
            <Text style={styles.overlayDisplayHint}>{t.overlaySizeHint}</Text>
          </View>

          <View style={[styles.card, styles.apiCard]}>
            <Pressable
              accessibilityState={{expanded: apiExpanded}}
              onPress={() => setApiExpanded(value => !value)}
              style={styles.apiHeader}>
              <Text style={styles.cardTitle}>{t.apiSettings}</Text>
              <Text style={styles.apiToggle}>{apiExpanded ? '−' : '+'}</Text>
            </Pressable>
            {apiExpanded && (
              <View style={styles.apiBody}>
                <SelectField
                  label={t.provider}
                  onChange={changeProvider}
                  options={providerOptions}
                  testID="provider"
                  value={settings.provider}
                />
                <Text style={styles.providerHint}>{t.providerHint}</Text>
                <Text style={styles.fieldLabel}>{t.baseUrl}</Text>
                <TextInput
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="url"
                  onBlur={() => blurInput(baseUrlInput.current)}
                  onChangeText={value => updateSetting('baseUrl', value)}
                  onFocus={() => focusInput(baseUrlInput.current)}
                  placeholder={providerPresets[settings.provider].baseUrl}
                  placeholderTextColor="#A0A1AF"
                  ref={baseUrlInput}
                  style={styles.textField}
                  value={settings.baseUrl}
                />
                <Text style={styles.fieldLabel}>{t.model}</Text>
                <TextInput
                  autoCapitalize="none"
                  autoCorrect={false}
                  onBlur={() => blurInput(modelInput.current)}
                  onChangeText={value => updateSetting('model', value)}
                  onFocus={() => focusInput(modelInput.current)}
                  placeholder={providerPresets[settings.provider].model}
                  placeholderTextColor="#A0A1AF"
                  ref={modelInput}
                  style={styles.textField}
                  value={settings.model}
                />
                <Text style={styles.fieldLabel}>{t.apiKey}</Text>
                <TextInput
                  autoCapitalize="none"
                  autoCorrect={false}
                  onBlur={() => blurInput(apiKeyInput.current)}
                  onChangeText={value => {
                    setApiKey(value);
                    setApiKeyChanged(true);
                  }}
                  onFocus={() => focusInput(apiKeyInput.current)}
                  placeholder={hasApiKey && !apiKeyChanged ? t.savedKey : t.apiKeyHint}
                  placeholderTextColor="#A0A1AF"
                  ref={apiKeyInput}
                  secureTextEntry
                  style={styles.textField}
                  value={apiKey}
                />
                <Pressable
                  onPress={saveApiSettings}
                  style={({pressed}) => [styles.saveButton, pressed && styles.pressed]}>
                  <Text style={styles.saveButtonText}>{t.save}</Text>
                </Pressable>
              </View>
            )}
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

const colors = {
  background: '#F7F7FA',
  surface: '#FFFFFF',
  ink: '#17182B',
  muted: '#73758B',
  line: '#E4E3ED',
  accent: '#635BDF',
  accentDark: '#4C45C6',
  accentSoft: '#EEECFF',
  success: '#18A77A',
  successSoft: '#DDF6ED',
  warning: '#9A3412',
};

const shadow = {
  elevation: 2,
  shadowColor: '#2A284B',
  shadowOffset: {width: 0, height: 7},
  shadowOpacity: 0.06,
  shadowRadius: 18,
};

const styles = StyleSheet.create({
  app: {backgroundColor: colors.background, flex: 1},
  header: {
    alignItems: 'center',
    backgroundColor: colors.background,
    borderBottomColor: '#E9E8F0',
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: HEADER_HEIGHT,
    paddingHorizontal: 18,
    paddingTop: StatusBar.currentHeight ?? 0,
  },
  brand: {alignItems: 'center', flex: 1, flexDirection: 'row'},
  brandMark: {
    alignItems: 'center',
    backgroundColor: colors.accent,
    borderRadius: 12,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  brandLetter: {color: '#FFFFFF', fontSize: 17, fontWeight: '900'},
  brandCopy: {flex: 1, marginLeft: 10},
  brandName: {color: colors.ink, fontSize: 16, fontWeight: '800'},
  serviceStatus: {alignItems: 'center', flexDirection: 'row', marginTop: 3},
  statusDot: {
    backgroundColor: colors.success,
    borderRadius: 4,
    height: 7,
    marginRight: 6,
    width: 7,
  },
  statusDotOff: {backgroundColor: '#B0B1BE'},
  serviceStatusText: {color: colors.muted, flexShrink: 1, fontSize: 10},
  languageTabs: {
    backgroundColor: '#EFEFF5',
    borderRadius: 12,
    flexDirection: 'row',
    padding: 3,
  },
  languageTab: {
    alignItems: 'center',
    borderRadius: 9,
    height: 34,
    justifyContent: 'center',
    minWidth: 38,
    paddingHorizontal: 7,
  },
  languageTabActive: {backgroundColor: '#FFFFFF', elevation: 1},
  languageTabText: {color: '#77798E', fontSize: 11, fontWeight: '800'},
  languageTabTextActive: {color: colors.accent},
  content: {paddingBottom: 36, paddingHorizontal: 16, paddingTop: 23},
  contentInner: {alignSelf: 'center', maxWidth: 620, width: '100%'},
  kicker: {
    color: colors.accent,
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1.4,
  },
  title: {
    color: colors.ink,
    fontSize: 27,
    fontWeight: '800',
    letterSpacing: -0.7,
    marginTop: 7,
  },
  subtitle: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 20,
    marginBottom: 18,
    marginTop: 7,
  },
  card: {
    ...shadow,
    backgroundColor: colors.surface,
    borderColor: '#E6E5EE',
    borderRadius: 22,
    borderWidth: 1,
    padding: 17,
  },
  languageRow: {alignItems: 'flex-end', flexDirection: 'row'},
  languageField: {flex: 1, minWidth: 0},
  swap: {
    alignItems: 'center',
    backgroundColor: colors.accentSoft,
    borderRadius: 14,
    height: 44,
    justifyContent: 'center',
    marginHorizontal: 8,
    width: 44,
  },
  swapText: {color: colors.accent, fontSize: 20, fontWeight: '900'},
  contextRow: {flexDirection: 'row', marginTop: 14},
  contextField: {flex: 1, minWidth: 0},
  field: {flex: 1},
  fieldLabel: {
    color: colors.muted,
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 7,
  },
  select: {
    alignItems: 'center',
    backgroundColor: '#F9F9FB',
    borderColor: '#E2E1EA',
    borderRadius: 13,
    borderWidth: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    marginRight: 5,
    paddingHorizontal: 12,
  },
  selectText: {color: '#343548', flex: 1, fontSize: 13, fontWeight: '700'},
  chevron: {color: colors.accent, fontSize: 16, marginLeft: 4},
  inputLabel: {marginTop: 16},
  inputShell: {position: 'relative'},
  sourceInput: {
    backgroundColor: '#FBFBFD',
    borderColor: '#E2E1EA',
    borderRadius: 15,
    borderWidth: 1,
    color: '#2E2F42',
    fontSize: 14,
    lineHeight: 21,
    minHeight: 112,
    paddingBottom: 29,
    paddingHorizontal: 13,
    paddingTop: 12,
  },
  counter: {
    bottom: 9,
    color: '#A0A1AF',
    fontSize: 10,
    position: 'absolute',
    right: 12,
  },
  toneCard: {marginTop: 13, paddingBottom: 12},
  cardHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 11,
  },
  cardTitle: {color: colors.ink, fontSize: 14, fontWeight: '800'},
  cardMeta: {color: '#8B8D9E', fontSize: 10, fontWeight: '700'},
  rangeRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 38,
  },
  rangeLabel: {color: '#686A7C', fontSize: 11, fontWeight: '700', width: 54},
  rangeTouch: {flex: 1, height: 38, justifyContent: 'center', paddingHorizontal: 8},
  rangeTrack: {backgroundColor: '#DEDEE8', borderRadius: 2, height: 4},
  rangeFill: {
    backgroundColor: colors.accent,
    borderRadius: 2,
    bottom: 0,
    left: 0,
    position: 'absolute',
    top: 0,
  },
  rangeThumb: {
    backgroundColor: colors.accent,
    borderColor: '#FFFFFF',
    borderRadius: 9,
    borderWidth: 3,
    height: 18,
    marginLeft: -9,
    marginTop: -7,
    position: 'absolute',
    width: 18,
  },
  rangeValue: {
    backgroundColor: colors.accentSoft,
    borderRadius: 8,
    color: colors.accent,
    fontSize: 11,
    fontWeight: '800',
    lineHeight: 24,
    overflow: 'hidden',
    textAlign: 'center',
    width: 25,
  },
  rangeValueWide: {width: 42},
  primary: {
    alignItems: 'center',
    backgroundColor: colors.accent,
    borderRadius: 17,
    elevation: 4,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 13,
    minHeight: 56,
    paddingLeft: 18,
    paddingRight: 10,
  },
  primaryPressed: {backgroundColor: colors.accentDark, transform: [{translateY: 1}]},
  primaryText: {color: '#FFFFFF', flex: 1, fontSize: 14, fontWeight: '800'},
  primaryIcon: {
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderRadius: 12,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  primaryArrow: {color: '#FFFFFF', fontSize: 18, fontWeight: '800'},
  disabled: {opacity: 0.7},
  status: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: '#EDF8F4',
    borderRadius: 11,
    flexDirection: 'row',
    marginTop: 10,
    minHeight: 34,
    paddingHorizontal: 11,
    paddingVertical: 7,
  },
  statusError: {backgroundColor: '#FFF1ED'},
  statusDotError: {backgroundColor: colors.warning},
  statusText: {color: colors.success, flexShrink: 1, fontSize: 11, fontWeight: '700'},
  statusTextError: {color: colors.warning},
  resultCard: {marginTop: 13},
  successBadge: {
    alignItems: 'center',
    backgroundColor: colors.successSoft,
    borderRadius: 8,
    height: 24,
    justifyContent: 'center',
    width: 24,
  },
  successText: {color: colors.success, fontSize: 12, fontWeight: '900'},
  candidate: {
    alignItems: 'flex-start',
    backgroundColor: '#FAFAFD',
    borderColor: '#E5E4ED',
    borderRadius: 15,
    borderWidth: 1,
    flexDirection: 'row',
    marginTop: 8,
    minHeight: 76,
    padding: 12,
  },
  candidateSelected: {backgroundColor: '#F3F1FF', borderColor: '#9C96EC'},
  radio: {
    backgroundColor: '#FFFFFF',
    borderColor: '#C9C8D5',
    borderRadius: 9,
    borderWidth: 2,
    height: 18,
    marginRight: 10,
    marginTop: 1,
    width: 18,
  },
  radioSelected: {borderColor: colors.accent, borderWidth: 5},
  candidateCopy: {flex: 1},
  candidateText: {color: '#393A4D', fontSize: 13, fontWeight: '700', lineHeight: 20},
  candidateLabel: {color: colors.accent, fontSize: 10, fontWeight: '800', marginTop: 6},
  resultLabel: {
    color: colors.muted,
    fontSize: 10,
    fontWeight: '800',
    marginTop: 13,
  },
  resultDetail: {color: '#868797', fontSize: 11, lineHeight: 17, marginTop: 4},
  warning: {color: colors.warning},
  actionRow: {flexDirection: 'row', marginTop: 14},
  secondary: {
    alignItems: 'center',
    backgroundColor: colors.accentSoft,
    borderRadius: 13,
    flex: 1,
    justifyContent: 'center',
    marginRight: 7,
    minHeight: 46,
    paddingHorizontal: 8,
  },
  secondaryText: {color: colors.accent, fontSize: 11, fontWeight: '800', textAlign: 'center'},
  returnButton: {
    alignItems: 'center',
    backgroundColor: colors.accent,
    borderRadius: 13,
    justifyContent: 'center',
    marginTop: 8,
    minHeight: 48,
    paddingHorizontal: 12,
  },
  returnText: {color: '#FFFFFF', fontSize: 12, fontWeight: '800'},
  safeNote: {alignItems: 'flex-start', flexDirection: 'row', marginHorizontal: 5, marginVertical: 13},
  safeIcon: {color: colors.success, fontSize: 17, lineHeight: 18, marginRight: 7},
  safeText: {color: '#858696', flex: 1, fontSize: 10, lineHeight: 16},
  overlayButton: {
    ...shadow,
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#E6E5EE',
    borderRadius: 19,
    borderWidth: 1,
    flexDirection: 'row',
    marginBottom: 13,
    minHeight: 88,
    padding: 13,
  },
  overlayButtonIcon: {
    alignItems: 'center',
    backgroundColor: colors.accent,
    borderRadius: 12,
    height: 42,
    justifyContent: 'center',
    width: 42,
  },
  overlayButtonLetter: {color: '#FFFFFF', fontSize: 16, fontWeight: '900'},
  overlayButtonCopy: {flex: 1, marginHorizontal: 11},
  overlayButtonTitle: {color: colors.ink, fontSize: 13, fontWeight: '800'},
  overlayButtonHint: {color: colors.muted, fontSize: 9, lineHeight: 14, marginTop: 5},
  overlayButtonArrow: {color: colors.accent, fontSize: 25},
  overlayDisplayCard: {marginBottom: 13, paddingBottom: 12},
  overlayDisplayHint: {color: colors.muted, fontSize: 10, lineHeight: 15, marginTop: 5},
  apiCard: {overflow: 'hidden', padding: 0},
  apiHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 58,
    paddingHorizontal: 17,
  },
  apiToggle: {color: colors.accent, fontSize: 21, fontWeight: '500'},
  apiBody: {paddingBottom: 17, paddingHorizontal: 17},
  providerHint: {
    color: colors.muted,
    fontSize: 10,
    lineHeight: 15,
    marginBottom: 12,
    marginTop: 5,
  },
  textField: {
    backgroundColor: '#FAFAFD',
    borderColor: '#E2E1EA',
    borderRadius: 13,
    borderWidth: 1,
    color: colors.ink,
    fontSize: 12,
    height: 45,
    marginBottom: 12,
    paddingHorizontal: 12,
  },
  saveButton: {
    alignItems: 'center',
    backgroundColor: colors.accentSoft,
    borderRadius: 13,
    justifyContent: 'center',
    minHeight: 46,
  },
  saveButtonText: {color: colors.accent, fontSize: 12, fontWeight: '800'},
  pressed: {opacity: 0.72},
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(23,24,43,0.42)',
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  optionSheet: {
    backgroundColor: '#FFFFFF',
    borderRadius: 22,
    elevation: 10,
    maxWidth: 420,
    padding: 12,
    width: '100%',
  },
  optionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 42,
    paddingHorizontal: 7,
  },
  optionTitle: {color: colors.ink, fontSize: 15, fontWeight: '800'},
  optionClose: {color: colors.muted, fontSize: 24, paddingHorizontal: 8},
  option: {
    alignItems: 'center',
    borderRadius: 13,
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 48,
    paddingHorizontal: 13,
  },
  optionSelected: {backgroundColor: colors.accentSoft},
  optionText: {color: '#4D4E61', fontSize: 14, fontWeight: '600'},
  optionTextSelected: {color: colors.accent, fontWeight: '800'},
  optionCheck: {color: colors.accent, fontSize: 14, fontWeight: '900'},
});
