package com.toneime.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class MainActivity extends Activity {
    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-5.6-luna";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Spinner uiLanguage;
    private Spinner sourceLanguage;
    private Spinner targetLanguage;
    private Spinner relation;
    private Spinner scene;
    private SeekBar politeness;
    private SeekBar warmth;
    private SeekBar directness;
    private TextView politenessLabel;
    private TextView warmthLabel;
    private TextView directnessLabel;
    private EditText source;
    private EditText baseUrl;
    private EditText model;
    private EditText apiKey;
    private Button translate;
    private Button copy;
    private Button share;
    private Button returnToApp;
    private ProgressBar progress;
    private TextView status;
    private RadioGroup candidates;
    private TextView backTranslation;
    private TextView warnings;
    private boolean processTextMode;
    private boolean updatingLanguages;
    private String previousSourceLanguage;
    private String previousTargetLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.localizedContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupChoices();
        loadSettings();
        setupListeners();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        uiLanguage = findViewById(R.id.ui_language);
        sourceLanguage = findViewById(R.id.source_language);
        targetLanguage = findViewById(R.id.target_language);
        relation = findViewById(R.id.relation);
        scene = findViewById(R.id.scene);
        politeness = findViewById(R.id.politeness);
        warmth = findViewById(R.id.warmth);
        directness = findViewById(R.id.directness);
        politenessLabel = findViewById(R.id.politeness_label);
        warmthLabel = findViewById(R.id.warmth_label);
        directnessLabel = findViewById(R.id.directness_label);
        source = findViewById(R.id.source);
        baseUrl = findViewById(R.id.base_url);
        model = findViewById(R.id.model);
        apiKey = findViewById(R.id.api_key);
        translate = findViewById(R.id.translate);
        copy = findViewById(R.id.copy);
        share = findViewById(R.id.share);
        returnToApp = findViewById(R.id.return_to_app);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        candidates = findViewById(R.id.candidates);
        backTranslation = findViewById(R.id.back_translation);
        warnings = findViewById(R.id.warnings);
    }

    private void setupChoices() {
        setupSpinner(uiLanguage, R.array.ui_language_names);
        setupSpinner(sourceLanguage, R.array.translation_language_names);
        setupSpinner(targetLanguage, R.array.translation_language_names);
        setupSpinner(relation, R.array.relation_names);
        setupSpinner(scene, R.array.scene_names);
    }

    private void setupSpinner(Spinner spinner, int valuesResource) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                getResources().getStringArray(valuesResource));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void loadSettings() {
        SharedPreferences preferences = AppSettings.preferences(this);
        AppSettings.migrate(preferences);
        select(uiLanguage, AppSettings.UI_LANGUAGES,
                preferences.getString("ui_language", "zh"));
        select(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES,
                preferences.getString("source_language", "zh"));
        select(targetLanguage, AppSettings.TRANSLATION_LANGUAGES,
                preferences.getString("target_language", "ja"));
        select(relation, AppSettings.RELATIONS,
                preferences.getString("relation_id", "friend"));
        select(scene, AppSettings.SCENES,
                preferences.getString("scene_id", "auto"));
        politeness.setProgress(preferences.getInt("politeness", 3) - 1);
        warmth.setProgress(preferences.getInt("warmth", 3) - 1);
        directness.setProgress(preferences.getInt("directness", 3) - 1);
        baseUrl.setText(preferences.getString("base_url", DEFAULT_BASE_URL));
        model.setText(preferences.getString("model", DEFAULT_MODEL));
        apiKey.setText(SecurePrefs.loadApiKey(this));
        previousSourceLanguage = selected(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES, "zh");
        previousTargetLanguage = selected(targetLanguage, AppSettings.TRANSLATION_LANGUAGES, "ja");
        updateStyleLabels();
    }

    private static void select(Spinner spinner, String[] values, String selected) {
        spinner.setSelection(AppSettings.indexOf(values, selected));
    }

    private void setupListeners() {
        uiLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {
                String selected = AppSettings.at(
                        AppSettings.UI_LANGUAGES,
                        position,
                        "zh");
                if (selected.equals(AppSettings.uiLanguage(MainActivity.this))) {
                    return;
                }
                persistOrdinarySettings();
                AppSettings.preferences(MainActivity.this)
                        .edit()
                        .putString("ui_language", selected)
                        .apply();
                sendBroadcast(new Intent(AppSettings.ACTION_UI_LANGUAGE_CHANGED)
                        .setPackage(getPackageName()));
                recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        sourceLanguage.setOnItemSelectedListener(languageListener(true));
        targetLanguage.setOnItemSelectedListener(languageListener(false));

        SeekBar.OnSeekBarChangeListener styleListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateStyleLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        politeness.setOnSeekBarChangeListener(styleListener);
        warmth.setOnSeekBarChangeListener(styleListener);
        directness.setOnSeekBarChangeListener(styleListener);
        findViewById(R.id.open_overlay_settings).setOnClickListener(view -> {
            saveSettings(false);
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        findViewById(R.id.save_settings).setOnClickListener(view -> saveSettings(true));
        translate.setOnClickListener(view -> translate());
        copy.setOnClickListener(view -> copySelected());
        share.setOnClickListener(view -> shareSelected());
        returnToApp.setOnClickListener(view -> returnSelected());
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
                AppSettings.preferences(MainActivity.this)
                        .edit()
                        .putString("source_language", pair.source)
                        .putString("target_language", pair.target)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    private void updateStyleLabels() {
        politenessLabel.setText(getString(R.string.politeness_value, value(politeness)));
        warmthLabel.setText(getString(R.string.warmth_value, value(warmth)));
        directnessLabel.setText(getString(R.string.directness_value, value(directness)));
    }

    private static int value(SeekBar seekBar) {
        return seekBar.getProgress() + 1;
    }

    private void handleIncomingIntent(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        CharSequence incoming = null;
        processTextMode = Intent.ACTION_PROCESS_TEXT.equals(action);
        if (processTextMode) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }

        if (incoming != null) {
            String text = incoming.toString();
            source.setText(text.length() <= 800 ? text : text.substring(0, 800));
        }

        boolean readOnly = intent != null &&
                intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);
        returnToApp.setVisibility(processTextMode && !readOnly ? View.VISIBLE : View.GONE);
    }

    private void saveSettings(boolean notify) {
        try {
            persistOrdinarySettings();
            SecurePrefs.saveApiKey(this, apiKey.getText().toString());
            if (notify) {
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception exception) {
            showError(getString(R.string.error_api_key_save));
        }
    }

    private void persistOrdinarySettings() {
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
                .putInt("politeness", value(politeness))
                .putInt("warmth", value(warmth))
                .putInt("directness", value(directness))
                .putString("base_url", baseUrl.getText().toString().trim())
                .putString("model", model.getText().toString().trim())
                .apply();
    }

    private void translate() {
        String input = source.getText().toString().trim();
        String endpoint = baseUrl.getText().toString().trim();
        String modelId = model.getText().toString().trim();
        String key = apiKey.getText().toString();
        if (input.length() < 2) {
            showError(getString(R.string.error_minimum_input));
            return;
        }
        if (!endpoint.startsWith("https://")) {
            showError(getString(R.string.error_https_required));
            return;
        }
        if (modelId.trim().isEmpty() || key.trim().isEmpty()) {
            showError(getString(R.string.error_model_and_key_required));
            return;
        }

        saveSettings(false);
        setBusy(true);
        status.setText(R.string.status_translating);
        TranslationProtocol.Request request = new TranslationProtocol.Request(
                selected(sourceLanguage, AppSettings.TRANSLATION_LANGUAGES, "zh"),
                selected(targetLanguage, AppSettings.TRANSLATION_LANGUAGES, "ja"),
                AppSettings.uiLanguage(this),
                input,
                selected(relation, AppSettings.RELATIONS, "friend"),
                selected(scene, AppSettings.SCENES, "auto"),
                value(politeness),
                value(warmth),
                value(directness));

        executor.execute(() -> {
            try {
                TranslationProtocol.Result result = new OpenAiClient()
                        .translate(endpoint, modelId, key, request);
                runOnUiThread(() -> renderResult(result));
            } catch (Exception exception) {
                runOnUiThread(() -> showError(getString(R.string.error_translation_failed)));
            }
        });
    }

    private void renderResult(TranslationProtocol.Result result) {
        candidates.removeAllViews();
        for (int i = 0; i < result.candidates.size(); i++) {
            TranslationProtocol.Candidate candidate = result.candidates.get(i);
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setTag(candidate.text);
            String label = i == 0
                    ? getString(R.string.recommended)
                    : candidate.label.trim().isEmpty()
                    ? getString(R.string.alternative)
                    : candidate.label;
            button.setText(getString(R.string.candidate_value, label, candidate.text));
            button.setTextIsSelectable(true);
            button.setPadding(0, 10, 0, 10);
            candidates.addView(button);
            if (i == 0) {
                button.setChecked(true);
            }
        }

        backTranslation.setText(result.backTranslation.trim().isEmpty()
                ? getString(R.string.not_available)
                : result.backTranslation);
        warnings.setText(result.warnings.isEmpty()
                ? getString(R.string.not_available)
                : result.warnings.stream().collect(Collectors.joining("\n")));
        status.setText(R.string.status_complete);
        copy.setEnabled(true);
        share.setEnabled(true);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        translate.setEnabled(!busy);
    }

    private void showError(String message) {
        status.setText(message);
        setBusy(false);
    }

    private String selectedCandidate() {
        int id = candidates.getCheckedRadioButtonId();
        if (id == -1) {
            return "";
        }
        View selected = candidates.findViewById(id);
        return selected == null || selected.getTag() == null
                ? ""
                : selected.getTag().toString();
    }

    private void copySelected() {
        String text = selectedCandidate();
        if (text.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ToneIME translation", text));
        Toast.makeText(this, R.string.translation_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareSelected() {
        String text = selectedCandidate();
        if (text.trim().isEmpty()) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_translation)));
    }

    private void returnSelected() {
        String text = selectedCandidate();
        if (text.trim().isEmpty() || !processTextMode) {
            return;
        }
        Intent result = new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        setResult(RESULT_OK, result);
        finish();
    }

    private static String selected(
            Spinner spinner,
            String[] values,
            String fallback) {
        return AppSettings.at(values, spinner.getSelectedItemPosition(), fallback);
    }
}
