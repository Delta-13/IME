package com.toneime.android;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccessibilityOverlayService extends AccessibilityService {
    private static final long DEBOUNCE_MS = 900;
    private static final String[] LEVELS = {"①", "②", "③", "④", "⑤"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private Spinner sourceLanguage;
    private Spinner targetLanguage;
    private Spinner relation;
    private Spinner scene;
    private TextView politenessControl;
    private TextView warmthControl;
    private TextView directnessControl;
    private TextView candidate;
    private int politeness = 3;
    private int warmth = 3;
    private int directness = 3;
    private Runnable pendingTranslation;
    private String observedSource = "";
    private String translatedSource = "";
    private String latestTranslation = "";
    private int generation;
    private long suppressEventsUntil;
    private boolean updatingLanguages;
    private boolean receiverRegistered;
    private String previousSourceLanguage;
    private String previousTargetLanguage;
    private final BroadcastReceiver languageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshOverlayLanguage();
        }
    };

    @Override
    @SuppressLint("InflateParams")
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (overlay != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowParams = new WindowManager.LayoutParams(
                dp(320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = dp(10);
        windowParams.y = dp(80);
        registerLanguageReceiver();
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null
                || overlay == null
                || SystemClock.uptimeMillis() < suppressEventsUntil
                || getPackageName().equals(text(event.getPackageName()))) {
            return;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(languageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(languageReceiver, filter);
        }
        receiverRegistered = true;
    }

    @SuppressLint("InflateParams")
    private void showOverlay() {
        Context localized = AppSettings.localizedContext(this);
        overlay = LayoutInflater.from(localized).inflate(R.layout.overlay_window, null);
        bindViews();
        setupChoices();
        loadSettings();
        setupListeners();
        windowManager.addView(overlay, windowParams);
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
        windowManager.removeView(overlay);
        overlay = null;
        showOverlay();
    }

    private void bindViews() {
        sourceLanguage = overlay.findViewById(R.id.overlay_source_language_spinner);
        targetLanguage = overlay.findViewById(R.id.overlay_target_language_spinner);
        relation = overlay.findViewById(R.id.overlay_relation_spinner);
        scene = overlay.findViewById(R.id.overlay_scene_spinner);
        politenessControl = overlay.findViewById(R.id.overlay_politeness_control);
        warmthControl = overlay.findViewById(R.id.overlay_warmth_control);
        directnessControl = overlay.findViewById(R.id.overlay_directness_control);
        candidate = overlay.findViewById(R.id.overlay_candidate);
    }

    private void setupChoices() {
        setupSpinner(sourceLanguage, R.array.translation_language_names);
        setupSpinner(targetLanguage, R.array.translation_language_names);
        setupSpinner(relation, R.array.relation_names);
        setupSpinner(scene, R.array.scene_names);
    }

    private void setupSpinner(Spinner spinner, int valuesResource) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                spinner.getContext(),
                android.R.layout.simple_spinner_item,
                spinner.getResources().getStringArray(valuesResource));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void loadSettings() {
        SharedPreferences preferences = AppSettings.preferences(this);
        AppSettings.migrate(preferences);
        select(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES,
                preferences.getString("source_language", "zh"));
        select(targetLanguage, AppSettings.TRANSLATION_LANGUAGES,
                preferences.getString("target_language", "ja"));
        select(relation, AppSettings.RELATIONS,
                preferences.getString("relation_id", "friend"));
        select(scene, AppSettings.SCENES,
                preferences.getString("scene_id", "auto"));
        politeness = clamp(preferences.getInt("politeness", 3), 1, 5);
        warmth = clamp(preferences.getInt("warmth", 3), 1, 5);
        directness = clamp(preferences.getInt("directness", 3), 1, 5);
        previousSourceLanguage =
                selected(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES, "zh");
        previousTargetLanguage =
                selected(targetLanguage, AppSettings.TRANSLATION_LANGUAGES, "ja");
        updateStyleControls();
    }

    private void setupListeners() {
        sourceLanguage.setOnItemSelectedListener(languageListener(true));
        targetLanguage.setOnItemSelectedListener(languageListener(false));
        AdapterView.OnItemSelectedListener choiceListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        settingsChanged();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };
        relation.setOnItemSelectedListener(choiceListener);
        scene.setOnItemSelectedListener(choiceListener);

        politenessControl.setOnClickListener(view -> {
            politeness = nextLevel(politeness);
            styleChanged();
        });
        warmthControl.setOnClickListener(view -> {
            warmth = nextLevel(warmth);
            styleChanged();
        });
        directnessControl.setOnClickListener(view -> {
            directness = nextLevel(directness);
            styleChanged();
        });

        candidate.setOnClickListener(view -> replaceCurrentInput());
        overlay.findViewById(R.id.overlay_close).setOnClickListener(view -> disableSelf());
        setupDragging(overlay.findViewById(R.id.overlay_drag_handle));
    }

    private AdapterView.OnItemSelectedListener languageListener(boolean sourceChanged) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {
                if (updatingLanguages) {
                    return;
                }
                String selected = AppSettings.at(
                        AppSettings.TRANSLATION_LANGUAGES,
                        position,
                        sourceChanged ? previousSourceLanguage : previousTargetLanguage);
                AppSettings.LanguagePair pair = AppSettings.afterLanguageChange(
                        previousSourceLanguage,
                        previousTargetLanguage,
                        selected,
                        sourceChanged);
                updatingLanguages = true;
                sourceLanguage.setSelection(
                        AppSettings.indexOf(AppSettings.TRANSLATION_LANGUAGES, pair.source));
                targetLanguage.setSelection(
                        AppSettings.indexOf(AppSettings.TRANSLATION_LANGUAGES, pair.target));
                updatingLanguages = false;
                previousSourceLanguage = pair.source;
                previousTargetLanguage = pair.target;
                settingsChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
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
                        view.performClick();
                        return true;
                    default:
                        return false;
                }
            }
        });
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
        String endpoint = preferences.getString("base_url", MainActivity.DEFAULT_BASE_URL);
        String model = preferences.getString("model", MainActivity.DEFAULT_MODEL);
        String apiKey = SecurePrefs.loadApiKey(this);
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
                selected(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES, "zh"),
                selected(targetLanguage, AppSettings.TRANSLATION_LANGUAGES, "ja"),
                AppSettings.uiLanguage(this),
                source,
                selected(relation, AppSettings.RELATIONS, "friend"),
                selected(scene, AppSettings.SCENES, "auto"),
                politeness,
                warmth,
                directness);
        candidate.setText(R.string.overlay_status_translating);
        candidate.setEnabled(false);
        executor.execute(() -> {
            try {
                TranslationProtocol.Result result = new OpenAiClient()
                        .translate(endpoint, model, apiKey, request);
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

    private void settingsChanged() {
        AppSettings.preferences(this)
                .edit()
                .putString("source_language", selected(
                        sourceLanguage,
                        AppSettings.TRANSLATION_LANGUAGES,
                        "zh"))
                .putString("target_language", selected(
                        targetLanguage,
                        AppSettings.TRANSLATION_LANGUAGES,
                        "ja"))
                .putString("relation_id", selected(
                        relation,
                        AppSettings.RELATIONS,
                        "friend"))
                .putString("scene_id", selected(
                        scene,
                        AppSettings.SCENES,
                        "auto"))
                .putInt("politeness", politeness)
                .putInt("warmth", warmth)
                .putInt("directness", directness)
                .apply();
        if (!observedSource.isEmpty()) {
            translatedSource = "";
            latestTranslation = "";
            candidate.setText(R.string.overlay_status_typing);
            candidate.setEnabled(false);
            scheduleTranslation(observedSource);
        }
    }

    private void styleChanged() {
        updateStyleControls();
        settingsChanged();
    }

    private void updateStyleControls() {
        updateStyleControl(
                politenessControl,
                politeness,
                R.string.overlay_politeness);
        updateStyleControl(
                warmthControl,
                warmth,
                R.string.overlay_warmth);
        updateStyleControl(
                directnessControl,
                directness,
                R.string.overlay_directness);
    }

    private void updateStyleControl(TextView control, int value, int description) {
        control.setText(LEVELS[value - 1]);
        control.setContentDescription(control.getContext().getString(description, value));
    }

    private static void select(Spinner spinner, String[] values, String selected) {
        spinner.setSelection(AppSettings.indexOf(values, selected));
    }

    private static int nextLevel(int value) {
        return value == 5 ? 1 : value + 1;
    }

    private static String selected(
            Spinner spinner,
            String[] values,
            String fallback) {
        return AppSettings.at(values, spinner.getSelectedItemPosition(), fallback);
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
