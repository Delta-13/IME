package com.toneime.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

final class AppSettings {
    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-5.6-luna";
    static final String ACTION_UI_LANGUAGE_CHANGED =
            "com.toneime.android.UI_LANGUAGE_CHANGED";
    static final String ACTION_OVERLAY_HIDE = "com.toneime.android.OVERLAY_HIDE";
    static final String ACTION_OVERLAY_SHOW = "com.toneime.android.OVERLAY_SHOW";
    static final String APP_VISIBLE = "app_visible";
    static final String OVERLAY_OPACITY = "overlay_opacity";
    static final String OVERLAY_WIDTH_DP = "overlay_width_dp";
    static final String OVERLAY_HEIGHT_DP = "overlay_height_dp";
    static final int OVERLAY_OPACITY_MIN = 35;
    static final int OVERLAY_OPACITY_DEFAULT = 90;
    static final int OVERLAY_WIDTH_MIN_DP = 260;
    static final int OVERLAY_WIDTH_DEFAULT_DP = 320;
    static final int OVERLAY_HEIGHT_MIN_DP = 150;
    static final String[] UI_LANGUAGES = {"zh", "ja", "en"};
    static final String[] TRANSLATION_LANGUAGES = {"zh", "ja", "en", "ko", "de"};
    static final String[] RELATIONS = {
            "elder", "boss", "peer", "friend", "close_friend", "partner"
    };
    static final String[] SCENES = {
            "auto", "chat", "request", "apology", "thanks", "decline", "care"
    };

    private AppSettings() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(SecurePrefs.PREFS, Context.MODE_PRIVATE);
    }

    static String uiLanguage(Context context) {
        return normalized(
                preferences(context).getString("ui_language", "zh"),
                UI_LANGUAGES,
                "zh");
    }

    static Context localizedContext(Context context) {
        String language = uiLanguage(context);
        Locale locale = Locale.forLanguageTag(language);
        Configuration configuration =
                new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    static void migrate(SharedPreferences preferences) {
        LanguagePair pair;
        if (preferences.contains("source_language")
                && preferences.contains("target_language")) {
            pair = new LanguagePair(
                    normalized(
                            preferences.getString("source_language", "zh"),
                            TRANSLATION_LANGUAGES,
                            "zh"),
                    normalized(
                            preferences.getString("target_language", "ja"),
                            TRANSLATION_LANGUAGES,
                            "ja"));
            if (pair.source.equals(pair.target)) {
                pair = new LanguagePair(pair.source, "zh".equals(pair.source) ? "ja" : "zh");
            }
        } else {
            pair = legacyDirection(preferences.getString("direction", "中→日"));
        }

        preferences.edit()
                .putString("ui_language", normalized(
                        preferences.getString("ui_language", "zh"),
                        UI_LANGUAGES,
                        "zh"))
                .putString("source_language", pair.source)
                .putString("target_language", pair.target)
                .putString("relation_id", relationId(
                        preferences.getString(
                                "relation_id",
                                preferences.getString("relation", "朋友"))))
                .putString("scene_id", sceneId(
                        preferences.getString(
                                "scene_id",
                                preferences.getString("scene", "自动"))))
                .remove("direction")
                .remove("relation")
                .remove("scene")
                .apply();
    }

    static LanguagePair legacyDirection(String direction) {
        return "日→中".equals(direction)
                ? new LanguagePair("ja", "zh")
                : new LanguagePair("zh", "ja");
    }

    static LanguagePair afterLanguageChange(
            String oldSource,
            String oldTarget,
            String selected,
            boolean sourceChanged) {
        if (!contains(TRANSLATION_LANGUAGES, selected)) {
            return new LanguagePair(oldSource, oldTarget);
        }
        if (sourceChanged) {
            return new LanguagePair(
                    selected,
                    selected.equals(oldTarget) ? oldSource : oldTarget);
        }
        return new LanguagePair(
                selected.equals(oldSource) ? oldTarget : oldSource,
                selected);
    }

    static String relationId(String value) {
        if (contains(RELATIONS, value)) {
            return value;
        }
        return switch (value == null ? "" : value) {
            case "长辈" -> "elder";
            case "领导" -> "boss";
            case "平辈" -> "peer";
            case "好朋友" -> "close_friend";
            case "情侣" -> "partner";
            default -> "friend";
        };
    }

    static String sceneId(String value) {
        if (contains(SCENES, value)) {
            return value;
        }
        return switch (value == null ? "" : value) {
            case "闲聊" -> "chat";
            case "请求" -> "request";
            case "道歉" -> "apology";
            case "感谢" -> "thanks";
            case "拒绝" -> "decline";
            case "关心" -> "care";
            default -> "auto";
        };
    }

    static int overlayOpacity(int value) {
        return clamp(value, OVERLAY_OPACITY_MIN, 100);
    }

    static int overlayWidthDp(int value) {
        return clamp(value, OVERLAY_WIDTH_MIN_DP, 600);
    }

    static int overlayHeightDp(int value) {
        return value <= 0 ? 0 : clamp(value, OVERLAY_HEIGHT_MIN_DP, 800);
    }

    static int indexOf(String[] values, String selected) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) {
                return i;
            }
        }
        return 0;
    }

    static String at(String[] values, int position, String fallback) {
        return position >= 0 && position < values.length ? values[position] : fallback;
    }

    static boolean isAllowed(String value, String[] allowed) {
        return contains(allowed, value);
    }

    static int level(int value) {
        return clamp(value, 1, 5);
    }

    static String normalized(String value, String[] allowed, String fallback) {
        return contains(allowed, value) ? value : fallback;
    }

    private static boolean contains(String[] values, String selected) {
        for (String value : values) {
            if (value.equals(selected)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class LanguagePair {
        final String source;
        final String target;

        LanguagePair(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }
}
