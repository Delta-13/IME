package com.toneime.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Appearance preferences never read or write translation or API settings. */
final class AppTheme {
    static final String PREFERENCE = "theme_mode";
    static final String ACTION_CHANGED = "com.toneime.android.THEME_CHANGED";
    static final String SYSTEM = "system";

    private AppTheme() {}

    static String normalize(String value) {
        return "light".equals(value) || "dark".equals(value) ? value : SYSTEM;
    }

    static String mode(Context context) {
        return normalize(AppSettings.preferences(context).getString(PREFERENCE, SYSTEM));
    }

    static boolean isDark(String mode, int uiMode) {
        return "dark".equals(mode) || (!"light".equals(mode)
                && (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    static boolean isDark(Context context) {
        return isDark(mode(context), context.getResources().getConfiguration().uiMode);
    }

    static void configure(Context context, Configuration configuration) {
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (isDark(context) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
    }

    static void applySavedMode(Context context) {
        String mode = mode(context);
        AppCompatDelegate.setDefaultNightMode("dark".equals(mode)
                ? AppCompatDelegate.MODE_NIGHT_YES : "light".equals(mode)
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @SuppressWarnings("deprecation")
    static void applyWindow(Activity activity) {
        Context themed = AppSettings.localizedContext(activity);
        int background = themed.getColor(R.color.tone_background);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(background));
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!isDark(themed));
        bars.setAppearanceLightNavigationBars(!isDark(themed));
    }
}
