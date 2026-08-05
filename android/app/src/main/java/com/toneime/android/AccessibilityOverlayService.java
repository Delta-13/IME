package com.toneime.android;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccessibilityOverlayService extends AccessibilityService {
    private static final int MIN_KEYBOARD_HEIGHT_DP = 120;
    private static final long DEBOUNCE_MS = 900;
    private static final String COMPACT_LAYOUT_VERSION = "overlay_compact_layout_version";
    private static final int COMPACT_LAYOUT_VERSION_CURRENT = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private TextView serviceTitle;
    private TextView direction;
    private TextView settingsSummary;
    private TextView candidate;
    private int politeness = 3;
    private int warmth = 3;
    private int directness = 3;
    private int overlayOpacity = AppSettings.OVERLAY_OPACITY_DEFAULT;
    private Runnable pendingTranslation;
    private String observedSource = "";
    private String translatedSource = "";
    private String latestTranslation = "";
    private int generation;
    private long suppressEventsUntil;
    private boolean receiverRegistered;
    private String sourceLanguageCode = "zh";
    private String targetLanguageCode = "ja";
    private String relationId = "friend";
    private String sceneId = "auto";
    private boolean compactOverlay;
    private int manualOverlayY;
    private int lastKeyboardHeight = Integer.MIN_VALUE;
    private final ViewTreeObserver.OnGlobalLayoutListener overlayImeAvoidanceListener =
            this::adjustOverlayForIme;
    private final BroadcastReceiver languageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AppSettings.ACTION_OVERLAY_HIDE.equals(action)) {
                hideOverlayForSettings();
                return;
            }
            if (AppSettings.ACTION_OVERLAY_SHOW.equals(action)) {
                if (overlay == null && windowParams != null) {
                    showOverlay();
                }
                return;
            }
            refreshOverlayLanguage();
        }
    };

    static int windowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    @Override
    @SuppressLint("InflateParams")
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (overlay != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        SharedPreferences preferences = AppSettings.preferences(this);
        migrateCompactLayoutHeight(preferences);
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(
                dp(AppSettings.overlayWidthDp(preferences.getInt(
                        AppSettings.OVERLAY_WIDTH_DP,
                        AppSettings.OVERLAY_WIDTH_DEFAULT_DP))),
                Math.max(dp(220), displayWidth - dp(16)));
        int savedHeightDp = AppSettings.overlayHeightDp(
                preferences.getInt(AppSettings.OVERLAY_HEIGHT_DP, 0));
        int height = savedHeightDp == 0
                ? WindowManager.LayoutParams.WRAP_CONTENT
                : Math.min(dp(savedHeightDp), Math.max(dp(150), displayHeight - dp(32)));
        windowParams = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                windowFlags(),
                PixelFormat.TRANSLUCENT);
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = dp(10);
        windowParams.y = dp(80);
        manualOverlayY = windowParams.y;
        registerLanguageReceiver();
        if (!preferences.getBoolean(AppSettings.APP_VISIBLE, false)) {
            showOverlay();
        }
    }

    private void migrateCompactLayoutHeight(SharedPreferences preferences) {
        if (preferences.getInt(COMPACT_LAYOUT_VERSION, 0)
                >= COMPACT_LAYOUT_VERSION_CURRENT) {
            return;
        }
        preferences.edit()
                .remove(AppSettings.OVERLAY_HEIGHT_DP)
                .putInt(COMPACT_LAYOUT_VERSION, COMPACT_LAYOUT_VERSION_CURRENT)
                .apply();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || SystemClock.uptimeMillis() < suppressEventsUntil) {
            return;
        }

        if (getPackageName().equals(text(event.getPackageName()))) {
            return;
        }
        if (overlay == null) {
            showOverlay();
        }

        AccessibilityNodeInfo node = event.getSource();
        if (node == null) {
            return;
        }
        try {
            if (!node.isFocused()) {
                return;
            }
            String value = text(node.getText());
            if (!InputMonitorPolicy.canMonitor(
                    text(node.getPackageName()),
                    getPackageName(),
                    node.isEditable(),
                    node.isPassword() || event.isPassword(),
                    node.isShowingHintText(),
                    value)) {
                clearObservation();
                return;
            }
            observe(value);
        } finally {
            node.recycle();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        removeOverlayImeAvoidanceListener();
        if (overlay != null && windowManager != null) {
            windowManager.removeView(overlay);
        }
        if (receiverRegistered) {
            unregisterReceiver(languageReceiver);
            receiverRegistered = false;
        }
        overlay = null;
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLanguageReceiver() {
        IntentFilter filter = new IntentFilter(AppSettings.ACTION_UI_LANGUAGE_CHANGED);
        filter.addAction(AppSettings.ACTION_OVERLAY_HIDE);
        filter.addAction(AppSettings.ACTION_OVERLAY_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(languageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(languageReceiver, filter);
        }
        receiverRegistered = true;
    }

    @SuppressLint("InflateParams")
    private void showOverlay() {
        if (AppSettings.preferences(this).getBoolean(AppSettings.APP_VISIBLE, false)) {
            return;
        }
        Context localized = AppSettings.localizedContext(this);
        overlay = LayoutInflater.from(localized).inflate(R.layout.overlay_window, null);
        bindViews();
        loadSettings();
        setupListeners();
        if (manualOverlayY <= 0) {
            manualOverlayY = windowParams.y;
        }
        windowManager.addView(overlay, windowParams);
        overlay.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateResponsiveLayout(right - left));
        overlay.getViewTreeObserver().addOnGlobalLayoutListener(overlayImeAvoidanceListener);
        adjustOverlayForIme();
    }

    private void refreshOverlayLanguage() {
        if (overlay == null || windowManager == null) {
            return;
        }
        generation++;
        handler.removeCallbacksAndMessages(null);
        observedSource = "";
        translatedSource = "";
        latestTranslation = "";
        removeOverlayImeAvoidanceListener();
        windowManager.removeView(overlay);
        overlay = null;
        showOverlay();
    }

    private void bindViews() {
        serviceTitle = overlay.findViewById(R.id.overlay_service_title);
        direction = overlay.findViewById(R.id.overlay_direction);
        settingsSummary = overlay.findViewById(R.id.overlay_settings_summary);
        candidate = overlay.findViewById(R.id.overlay_candidate);
    }

    private void loadSettings() {
        SharedPreferences preferences = AppSettings.preferences(this);
        AppSettings.migrate(preferences);
        sourceLanguageCode = AppSettings.normalized(
                preferences.getString("source_language", "zh"),
                AppSettings.TRANSLATION_LANGUAGES,
                "zh");
        targetLanguageCode = AppSettings.normalized(
                preferences.getString("target_language", "ja"),
                AppSettings.TRANSLATION_LANGUAGES,
                "ja");
        relationId = AppSettings.relationId(
                preferences.getString("relation_id", "friend"));
        sceneId = AppSettings.sceneId(
                preferences.getString("scene_id", "auto"));
        politeness = clamp(preferences.getInt("politeness", 3), 1, 5);
        warmth = clamp(preferences.getInt("warmth", 3), 1, 5);
        directness = clamp(preferences.getInt("directness", 3), 1, 5);
        overlayOpacity = AppSettings.overlayOpacity(preferences.getInt(
                AppSettings.OVERLAY_OPACITY,
                AppSettings.OVERLAY_OPACITY_DEFAULT));
        applyOverlayOpacity();
        updateSettingsSummary();
    }

    private void setupListeners() {
        candidate.setOnClickListener(view -> replaceCurrentInput());
        overlay.findViewById(R.id.overlay_close).setOnClickListener(view -> disableSelf());
        overlay.findViewById(R.id.overlay_open_app).setOnClickListener(
                view -> openMainAppSettings());
        setupDragging(overlay.findViewById(R.id.overlay_drag_handle));
        setupResizing(overlay.findViewById(R.id.overlay_resize_handle));
    }

    private void openMainAppSettings() {
        hideOverlayForSettings();
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void hideOverlayForSettings() {
        if (overlay == null || windowManager == null) {
            return;
        }
        generation++;
        handler.removeCallbacksAndMessages(null);
        pendingTranslation = null;
        observedSource = "";
        translatedSource = "";
        latestTranslation = "";
        removeOverlayImeAvoidanceListener();
        windowManager.removeView(overlay);
        overlay = null;
    }

    private void updateSettingsSummary() {
        String source = localizedLabel(
                R.array.translation_language_names,
                AppSettings.TRANSLATION_LANGUAGES,
                sourceLanguageCode,
                "zh");
        String target = localizedLabel(
                R.array.translation_language_names,
                AppSettings.TRANSLATION_LANGUAGES,
                targetLanguageCode,
                "ja");
        String currentRelation = localizedLabel(
                R.array.relation_names,
                AppSettings.RELATIONS,
                relationId,
                "friend");
        String currentScene = localizedLabel(
                R.array.scene_names,
                AppSettings.SCENES,
                sceneId,
                "auto");
        direction.setText(direction.getContext().getString(
                R.string.overlay_direction,
                source,
                target));
        settingsSummary.setText(settingsSummary.getContext().getString(
                R.string.overlay_settings_summary,
                currentRelation,
                currentScene,
                politeness,
                warmth,
                directness));
        settingsSummary.setContentDescription(settingsSummary.getContext().getString(
                R.string.overlay_settings_summary_description,
                currentRelation,
                currentScene,
                politeness,
                warmth,
                directness));
    }

    private String localizedLabel(
            int namesResource,
            String[] ids,
            String id,
            String fallback) {
        String[] labels = overlay.getResources().getStringArray(namesResource);
        return labels[AppSettings.indexOf(ids, AppSettings.normalized(id, ids, fallback))];
    }

    private void updateResponsiveLayout(int width) {
        if (width <= 0) {
            return;
        }
        boolean nextCompact = width < dp(300);
        if (nextCompact == compactOverlay) {
            return;
        }
        compactOverlay = nextCompact;
        serviceTitle.setVisibility(nextCompact ? View.GONE : View.VISIBLE);
        settingsSummary.setMaxLines(nextCompact ? 2 : 1);
        candidate.setMaxLines(nextCompact ? 2 : 3);
    }

    private void setupDragging(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = windowParams.x;
                        startY = windowParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int maxX = Math.max(0,
                                getResources().getDisplayMetrics().widthPixels
                                        - overlay.getWidth());
                        int maxY = Math.max(0,
                                getResources().getDisplayMetrics().heightPixels
                                        - overlay.getHeight());
                        windowParams.x = clamp(
                                startX + Math.round(event.getRawX() - downX), 0, maxX);
                        windowParams.y = clamp(
                                startY + Math.round(event.getRawY() - downY), 0, maxY);
                        windowManager.updateViewLayout(overlay, windowParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        manualOverlayY = windowParams.y;
                        adjustOverlayForIme();
                        view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        manualOverlayY = windowParams.y;
                        adjustOverlayForIme();
                        return false;
                    default:
                        return false;
                }
            }
        });
    }

    private void setupResizing(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startWidth;
            private int startHeight;
            private boolean resized;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startWidth = overlay.getWidth();
                        startHeight = overlay.getHeight();
                        resized = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        resized = true;
                        int minimumWidth = dp(AppSettings.OVERLAY_WIDTH_MIN_DP);
                        int minimumHeight = dp(AppSettings.OVERLAY_HEIGHT_MIN_DP);
                        int maximumWidth = Math.max(
                                minimumWidth,
                                getResources().getDisplayMetrics().widthPixels
                                        - windowParams.x
                                        - dp(8));
                        int maximumHeight = Math.max(
                                minimumHeight,
                                getResources().getDisplayMetrics().heightPixels
                                        - windowParams.y
                                        - dp(8));
                        windowParams.width = clamp(
                                startWidth + Math.round(event.getRawX() - downX),
                                minimumWidth,
                                maximumWidth);
                        windowParams.height = clamp(
                                startHeight + Math.round(event.getRawY() - downY),
                                minimumHeight,
                                maximumHeight);
                        windowManager.updateViewLayout(overlay, windowParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (resized) {
                            persistOverlaySize();
                        }
                        adjustOverlayForIme();
                        view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (resized) {
                            persistOverlaySize();
                        }
                        adjustOverlayForIme();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void persistOverlaySize() {
        float density = getResources().getDisplayMetrics().density;
        int width = windowParams.width > 0 ? windowParams.width : overlay.getWidth();
        int height = windowParams.height > 0 ? windowParams.height : overlay.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        AppSettings.preferences(this)
                .edit()
                .putInt(
                        AppSettings.OVERLAY_WIDTH_DP,
                        Math.round(width / density))
                .putInt(
                        AppSettings.OVERLAY_HEIGHT_DP,
                        Math.round(height / density))
                .apply();
    }

    private void applyOverlayOpacity() {
        if (overlay == null) {
            return;
        }
        overlay.setAlpha(overlayOpacity / 100f);
    }

    private void adjustOverlayForIme() {
        if (overlay == null || windowManager == null) {
            return;
        }

        int overlayHeight = overlay.getHeight();
        if (overlayHeight <= 0) {
            return;
        }

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int keyboardHeight = estimateKeyboardHeight(screenHeight);
        boolean imeVisible = keyboardHeight > dp(MIN_KEYBOARD_HEIGHT_DP);

        if (!imeVisible) {
            if (lastKeyboardHeight <= dp(MIN_KEYBOARD_HEIGHT_DP)) {
                return;
            }
            setOverlayY(manualOverlayY);
            lastKeyboardHeight = keyboardHeight;
            return;
        }

        lastKeyboardHeight = keyboardHeight;
        int safeBottom = Math.max(0, screenHeight - keyboardHeight);
        int maxY = Math.max(0, safeBottom - overlayHeight - dp(8));
        int targetY = Math.min(manualOverlayY, maxY);
        setOverlayY(targetY);
    }

    private int estimateKeyboardHeight(int screenHeight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsets insets = overlay.getRootWindowInsets();
            if (insets != null) {
                return insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            }
        }
        Rect visibleFrame = new Rect();
        overlay.getRootView().getWindowVisibleDisplayFrame(visibleFrame);
        return Math.max(0, screenHeight - visibleFrame.bottom);
    }

    private void setOverlayY(int y) {
        int maxY = Math.max(
                0,
                getResources().getDisplayMetrics().heightPixels - overlay.getHeight());
        int clampedY = clamp(y, 0, maxY);
        if (windowParams.y == clampedY) {
            return;
        }
        windowParams.y = clampedY;
        windowManager.updateViewLayout(overlay, windowParams);
    }

    private void removeOverlayImeAvoidanceListener() {
        if (overlay == null) {
            return;
        }
        overlay.getViewTreeObserver().removeOnGlobalLayoutListener(overlayImeAvoidanceListener);
    }

    private void observe(String source) {
        if (source.equals(observedSource)) {
            return;
        }
        observedSource = source;
        translatedSource = "";
        latestTranslation = "";
        candidate.setText(R.string.overlay_status_typing);
        candidate.setEnabled(false);
        scheduleTranslation(source);
    }

    private void scheduleTranslation(String source) {
        if (pendingTranslation != null) {
            handler.removeCallbacks(pendingTranslation);
        }
        int requestGeneration = ++generation;
        pendingTranslation = () -> startTranslation(requestGeneration, source);
        handler.postDelayed(pendingTranslation, DEBOUNCE_MS);
    }

    private void startTranslation(int requestGeneration, String source) {
        if (requestGeneration != generation || !source.equals(observedSource)) {
            return;
        }

        AccessibilityNodeInfo input = findFocusedInput();
        if (input == null) {
            clearObservation();
            return;
        }
        try {
            String current = text(input.getText());
            if (!InputMonitorPolicy.canMonitor(
                    text(input.getPackageName()),
                    getPackageName(),
                    input.isEditable(),
                    input.isPassword(),
                    input.isShowingHintText(),
                    current)
                    || !source.equals(current)) {
                clearObservation();
                return;
            }
        } finally {
            input.recycle();
        }

        SharedPreferences preferences = AppSettings.preferences(this);
        String provider = ApiProvider.normalize(
                preferences.getString("provider", AppSettings.DEFAULT_PROVIDER));
        String endpoint = preferences.getString("base_url", AppSettings.DEFAULT_BASE_URL);
        String model = preferences.getString("model", AppSettings.DEFAULT_MODEL);
        String apiKey = SecurePrefs.loadApiKey(this, provider);
        if (endpoint == null
                || !endpoint.startsWith("https://")
                || model == null
                || model.trim().isEmpty()
                || apiKey.trim().isEmpty()) {
            candidate.setText(R.string.overlay_status_missing_api);
            candidate.setEnabled(false);
            return;
        }

        TranslationProtocol.Request request = new TranslationProtocol.Request(
                sourceLanguageCode,
                targetLanguageCode,
                AppSettings.uiLanguage(this),
                source,
                relationId,
                sceneId,
                politeness,
                warmth,
                directness);
        candidate.setText(R.string.overlay_status_translating);
        candidate.setEnabled(false);
        executor.execute(() -> {
            try {
                TranslationProtocol.Result result = new OpenAiClient()
                        .translate(provider, endpoint, model, apiKey, request);
                String primary = result.candidates.get(0).text;
                handler.post(() -> showTranslation(requestGeneration, source, primary));
            } catch (Exception exception) {
                handler.post(() -> showTranslationError(requestGeneration, source));
            }
        });
    }

    private void showTranslation(int requestGeneration, String source, String translation) {
        if (overlay == null
                || requestGeneration != generation
                || !source.equals(observedSource)) {
            return;
        }
        translatedSource = source;
        latestTranslation = translation;
        candidate.setText(translation);
        candidate.setEnabled(true);
    }

    private void showTranslationError(
            int requestGeneration,
            String source) {
        if (overlay == null
                || requestGeneration != generation
                || !source.equals(observedSource)) {
            return;
        }
        candidate.setText(R.string.error_translation_failed);
        candidate.setEnabled(false);
    }

    private void replaceCurrentInput() {
        AccessibilityNodeInfo input = findFocusedInput();
        if (input == null) {
            candidate.setText(R.string.overlay_status_changed);
            candidate.setEnabled(false);
            return;
        }
        try {
            String current = text(input.getText());
            if (!InputMonitorPolicy.canMonitor(
                    text(input.getPackageName()),
                    getPackageName(),
                    input.isEditable(),
                    input.isPassword(),
                    input.isShowingHintText(),
                    current)
                    || !InputMonitorPolicy.canReplace(
                    translatedSource,
                    current,
                    latestTranslation)) {
                candidate.setText(R.string.overlay_status_changed);
                candidate.setEnabled(false);
                return;
            }

            String replacement = latestTranslation;
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    replacement);
            suppressEventsUntil = SystemClock.uptimeMillis() + 1200;
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                suppressEventsUntil = 0;
                candidate.setText(R.string.overlay_status_replace_failed);
                candidate.setEnabled(false);
                return;
            }

            generation++;
            if (pendingTranslation != null) {
                handler.removeCallbacks(pendingTranslation);
            }
            observedSource = replacement;
            translatedSource = "";
            latestTranslation = "";
            candidate.setText(R.string.overlay_status_replaced);
            candidate.setEnabled(false);
        } finally {
            input.recycle();
        }
    }

    private AccessibilityNodeInfo findFocusedInput() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !root.equals(focused)) {
            root.recycle();
        }
        return focused;
    }

    private void clearObservation() {
        generation++;
        if (pendingTranslation != null) {
            handler.removeCallbacks(pendingTranslation);
        }
        observedSource = "";
        translatedSource = "";
        latestTranslation = "";
        candidate.setText(R.string.overlay_status_waiting);
        candidate.setEnabled(false);
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
