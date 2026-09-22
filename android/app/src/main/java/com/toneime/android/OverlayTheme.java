package com.toneime.android;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/** Recolors the existing view tree without resetting text, requests, or timing. */
final class OverlayTheme {
    private OverlayTheme() {}

    static void apply(View root, Context context, int opacity) {
        // Accessibility windows do not always inherit the Activity's Force Dark
        // setting, especially when the system changes theme after inflation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) root.setForceDarkAllowed(false);
        root.setBackground(context.getDrawable(R.drawable.overlay_panel));
        for (int id : new int[] {R.id.overlay_header, R.id.overlay_connection_panel,
                R.id.overlay_settings_panel, R.id.overlay_candidate, R.id.overlay_open_app,
                R.id.overlay_close, R.id.overlay_connection_timing, R.id.overlay_resize_handle}) {
            root.findViewById(id).setBackground(context.getDrawable(R.drawable.bg_overlay_control));
        }
        root.findViewById(R.id.overlay_drag_handle)
                .setBackground(context.getDrawable(R.drawable.bg_brand_mark));
        textColor(root, context, R.color.tone_ink, R.id.overlay_service_title,
                R.id.overlay_direction, R.id.overlay_candidate);
        textColor(root, context, R.color.tone_muted, R.id.overlay_close,
                R.id.overlay_connection_status, R.id.overlay_connection_timing,
                R.id.overlay_settings_summary);
        textColor(root, context, R.color.tone_accent, R.id.overlay_open_app,
                R.id.overlay_status_dot, R.id.overlay_resize_handle);
        textColor(root, context, R.color.tone_on_accent, R.id.overlay_drag_handle);
        applyOpacity(root, opacity);
    }

    static void applyOpacity(View root, int opacity) {
        // Fade only the outer panel. Every text region has an opaque backing,
        // keeping contrast independent of the underlying app and this setting.
        root.setAlpha(1f);
        root.getBackground().mutate().setAlpha(Math.round(AppSettings.overlayOpacity(opacity) * 2.55f));
    }

    private static void textColor(View root, Context context, int color, int... ids) {
        for (int id : ids) ((TextView) root.findViewById(id)).setTextColor(context.getColor(color));
    }
}
