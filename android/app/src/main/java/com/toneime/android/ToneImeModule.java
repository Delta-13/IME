package com.toneime.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ToneImeModule extends ReactContextBaseJavaModule implements NativeModule {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();

    ToneImeModule(ReactApplicationContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "ToneIme";
    }

    @ReactMethod
    public void loadState(Promise promise) {
        try {
            SharedPreferences preferences = AppSettings.preferences(getReactApplicationContext());
            AppSettings.migrate(preferences);
            String provider = ApiProvider.normalize(
                    preferences.getString("provider", AppSettings.DEFAULT_PROVIDER));
            WritableMap state = Arguments.createMap();
            state.putString("uiLanguage", AppSettings.uiLanguage(getReactApplicationContext()));
            state.putString("sourceLanguage",
                    preferences.getString("source_language", "zh"));
            state.putString("targetLanguage",
                    preferences.getString("target_language", "ja"));
            state.putString("relation",
                    preferences.getString("relation_id", "friend"));
            state.putString("scene",
                    preferences.getString("scene_id", "auto"));
            state.putInt("politeness",
                    AppSettings.level(preferences.getInt("politeness", 3)));
            state.putInt("warmth",
                    AppSettings.level(preferences.getInt("warmth", 3)));
            state.putInt("directness",
                    AppSettings.level(preferences.getInt("directness", 3)));
            state.putInt("overlayOpacity",
                    AppSettings.overlayOpacity(preferences.getInt(
                            AppSettings.OVERLAY_OPACITY,
                            AppSettings.OVERLAY_OPACITY_DEFAULT)));
            state.putString("provider", provider);
            state.putString("baseUrl",
                    preferences.getString("base_url", AppSettings.DEFAULT_BASE_URL));
            state.putString("model",
                    preferences.getString("model", AppSettings.DEFAULT_MODEL));
            state.putBoolean("hasApiKey",
                    !SecurePrefs.loadApiKey(getReactApplicationContext(), provider).trim().isEmpty());
            state.putBoolean("accessibilityEnabled", isAccessibilityEnabled());
            addIncomingText(state);
            promise.resolve(state);
        } catch (Exception exception) {
            promise.reject("E_LOAD_SETTINGS", exception);
        }
    }

    @ReactMethod
    public void setUiLanguage(String language, Promise promise) {
        if (!AppSettings.isAllowed(language, AppSettings.UI_LANGUAGES)) {
            promise.reject("E_UI_LANGUAGE", "Unsupported interface language.");
            return;
        }
        generation.incrementAndGet();
        AppSettings.preferences(getReactApplicationContext())
                .edit()
                .putString("ui_language", language)
                .apply();
        notifyOverlay();
        promise.resolve(null);
    }

    @ReactMethod
    public void changeLanguage(String language, boolean sourceChanged, Promise promise) {
        SharedPreferences preferences = AppSettings.preferences(getReactApplicationContext());
        AppSettings.migrate(preferences);
        AppSettings.LanguagePair pair = AppSettings.afterLanguageChange(
                preferences.getString("source_language", "zh"),
                preferences.getString("target_language", "ja"),
                language,
                sourceChanged);
        preferences.edit()
                .putString("source_language", pair.source)
                .putString("target_language", pair.target)
                .apply();
        generation.incrementAndGet();
        notifyOverlay();
        WritableMap result = Arguments.createMap();
        result.putString("sourceLanguage", pair.source);
        result.putString("targetLanguage", pair.target);
        promise.resolve(result);
    }

    @ReactMethod
    public void saveSettings(
            ReadableMap settings,
            @Nullable String changedApiKey,
            Promise promise) {
        try {
            persistSettings(settings, changedApiKey);
            notifyOverlay();
            promise.resolve(null);
        } catch (Exception exception) {
            promise.reject("E_SAVE_SETTINGS", exception);
        }
    }

    @ReactMethod
    public void translate(
            ReadableMap request,
            @Nullable String changedApiKey,
            Promise promise) {
        String source = string(request, "source", "").trim();
        if (source.length() < 2) {
            promise.reject("E_MINIMUM_INPUT", "Enter at least two characters.");
            return;
        }
        if (source.length() > 800) {
            promise.reject("E_INPUT_TOO_LONG", "Input exceeds 800 characters.");
            return;
        }

        try {
            persistSettings(request, changedApiKey);
        } catch (Exception exception) {
            promise.reject("E_SAVE_SETTINGS", exception);
            return;
        }

        SharedPreferences preferences = AppSettings.preferences(getReactApplicationContext());
        String provider = ApiProvider.normalize(
                preferences.getString("provider", AppSettings.DEFAULT_PROVIDER));
        String endpoint = preferences.getString("base_url", AppSettings.DEFAULT_BASE_URL);
        String model = preferences.getString("model", AppSettings.DEFAULT_MODEL);
        String apiKey = SecurePrefs.loadApiKey(getReactApplicationContext(), provider);
        if (endpoint == null || !endpoint.startsWith("https://")) {
            promise.reject("E_HTTPS_REQUIRED", "The API URL must use HTTPS.");
            return;
        }
        if (model == null || model.trim().isEmpty() || apiKey.trim().isEmpty()) {
            promise.reject("E_MODEL_AND_KEY", "Model and API key are required.");
            return;
        }

        TranslationProtocol.Request translationRequest = new TranslationProtocol.Request(
                normalized(request, "sourceLanguage",
                        AppSettings.TRANSLATION_LANGUAGES, "zh"),
                normalized(request, "targetLanguage",
                        AppSettings.TRANSLATION_LANGUAGES, "ja"),
                AppSettings.uiLanguage(getReactApplicationContext()),
                source,
                AppSettings.relationId(string(request, "relation", "friend")),
                AppSettings.sceneId(string(request, "scene", "auto")),
                level(request, "politeness"),
                level(request, "warmth"),
                level(request, "directness"));
        int requestGeneration = generation.incrementAndGet();
        executor.execute(() -> {
            try {
                TranslationProtocol.Result result = new OpenAiClient()
                        .translate(provider, endpoint, model, apiKey, translationRequest);
                if (requestGeneration != generation.get()) {
                    promise.reject("E_CANCELLED", "Translation was cancelled.");
                    return;
                }
                promise.resolve(toMap(result));
            } catch (Exception exception) {
                if (requestGeneration == generation.get()) {
                    promise.reject("E_TRANSLATION", exception);
                } else {
                    promise.reject("E_CANCELLED", "Translation was cancelled.");
                }
            }
        });
    }

    @ReactMethod
    public void cancelTranslation() {
        generation.incrementAndGet();
    }

    @ReactMethod
    public void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    @ReactMethod
    public void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getReactApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ToneIME translation", text));
    }

    @ReactMethod
    public void shareText(String text) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, null));
    }

    @ReactMethod
    public void returnToApp(String text, Promise promise) {
        Activity activity = getCurrentActivity();
        Intent incoming = activity == null ? null : activity.getIntent();
        boolean processText = incoming != null
                && Intent.ACTION_PROCESS_TEXT.equals(incoming.getAction());
        boolean readOnly = incoming != null
                && incoming.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);
        if (activity == null || !processText || readOnly) {
            promise.reject("E_RETURN_UNAVAILABLE", "The calling app cannot accept replacement.");
            return;
        }
        activity.setResult(
                Activity.RESULT_OK,
                new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text));
        activity.finish();
        promise.resolve(null);
    }

    @Override
    public void invalidate() {
        generation.incrementAndGet();
        executor.shutdownNow();
        super.invalidate();
    }

    private void persistSettings(
            ReadableMap settings,
            @Nullable String changedApiKey) throws Exception {
        String source = normalized(settings, "sourceLanguage",
                AppSettings.TRANSLATION_LANGUAGES, "zh");
        String target = normalized(settings, "targetLanguage",
                AppSettings.TRANSLATION_LANGUAGES, "ja");
        if (source.equals(target)) {
            target = "zh".equals(source) ? "ja" : "zh";
        }
        String provider = ApiProvider.normalize(
                string(settings, "provider", AppSettings.DEFAULT_PROVIDER));
        String baseUrl = string(settings, "baseUrl", ApiProvider.defaultBaseUrl(provider)).trim();
        String model = string(settings, "model", ApiProvider.defaultModel(provider)).trim();
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("The API URL must use HTTPS.");
        }
        if (model.isEmpty()) {
            throw new IllegalArgumentException("Model is required.");
        }

        SharedPreferences preferences = AppSettings.preferences(getReactApplicationContext());
        int overlayOpacity = overlayOpacity(
                settings,
                preferences.getInt(
                        AppSettings.OVERLAY_OPACITY,
                        AppSettings.OVERLAY_OPACITY_DEFAULT));
        preferences
                .edit()
                .putString("source_language", source)
                .putString("target_language", target)
                .putString("relation_id",
                        AppSettings.relationId(string(settings, "relation", "friend")))
                .putString("scene_id",
                        AppSettings.sceneId(string(settings, "scene", "auto")))
                .putInt("politeness", level(settings, "politeness"))
                .putInt("warmth", level(settings, "warmth"))
                .putInt("directness", level(settings, "directness"))
                .putInt(AppSettings.OVERLAY_OPACITY, overlayOpacity)
                .putString("provider", provider)
                .putString("base_url", baseUrl)
                .putString("model", model)
                .apply();
        if (changedApiKey != null) {
            SecurePrefs.saveApiKey(getReactApplicationContext(), provider, changedApiKey);
        }
    }

    private void addIncomingText(WritableMap state) {
        Activity activity = getCurrentActivity();
        Intent intent = activity == null ? null : activity.getIntent();
        String action = intent == null ? null : intent.getAction();
        boolean processText = Intent.ACTION_PROCESS_TEXT.equals(action);
        CharSequence incoming = null;
        if (processText) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (Intent.ACTION_SEND.equals(action)
                && "text/plain".equals(intent.getType())) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }
        String text = incoming == null ? "" : incoming.toString();
        state.putString("incomingText",
                text.length() <= 800 ? text : text.substring(0, 800));
        state.putBoolean("canReturnToApp",
                processText
                        && intent != null
                        && !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false));
    }

    private boolean isAccessibilityEnabled() {
        Context context = getReactApplicationContext();
        if (Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0) != 1) {
            return false;
        }
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        TextUtils.SimpleStringSplitter services = new TextUtils.SimpleStringSplitter(':');
        services.setString(enabled == null ? "" : enabled);
        String expected = new ComponentName(
                context,
                AccessibilityOverlayService.class).flattenToString();
        while (services.hasNext()) {
            if (expected.equalsIgnoreCase(services.next())) {
                return true;
            }
        }
        return false;
    }

    private void notifyOverlay() {
        getReactApplicationContext().sendBroadcast(
                new Intent(AppSettings.ACTION_UI_LANGUAGE_CHANGED)
                        .setPackage(getReactApplicationContext().getPackageName()));
    }

    private void startActivity(Intent intent) {
        Activity activity = getCurrentActivity();
        if (activity != null) {
            activity.startActivity(intent);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getReactApplicationContext().startActivity(intent);
        }
    }

    private static WritableMap toMap(TranslationProtocol.Result result) {
        WritableMap map = Arguments.createMap();
        WritableArray candidates = Arguments.createArray();
        for (TranslationProtocol.Candidate candidate : result.candidates) {
            WritableMap item = Arguments.createMap();
            item.putString("label", candidate.label);
            item.putString("text", candidate.text);
            candidates.pushMap(item);
        }
        WritableArray warnings = Arguments.createArray();
        for (String warning : result.warnings) {
            warnings.pushString(warning);
        }
        map.putArray("candidates", candidates);
        map.putString("backTranslation", result.backTranslation);
        map.putArray("warnings", warnings);
        return map;
    }

    private static String normalized(
            ReadableMap map,
            String key,
            String[] allowed,
            String fallback) {
        return AppSettings.normalized(string(map, key, fallback), allowed, fallback);
    }

    private static int level(ReadableMap map, String key) {
        if (!map.hasKey(key) || map.isNull(key)) {
            return 3;
        }
        return AppSettings.level((int) Math.round(map.getDouble(key)));
    }

    private static int overlayOpacity(ReadableMap map, int fallback) {
        if (!map.hasKey("overlayOpacity") || map.isNull("overlayOpacity")) {
            return AppSettings.overlayOpacity(fallback);
        }
        return AppSettings.overlayOpacity((int) Math.round(map.getDouble("overlayOpacity")));
    }

    private static String string(ReadableMap map, String key, String fallback) {
        if (!map.hasKey(key) || map.isNull(key)) {
            return fallback;
        }
        String value = map.getString(key);
        return value == null ? fallback : value;
    }
}
