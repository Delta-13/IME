package com.toneime.android;

import static org.junit.Assert.assertEquals;

import android.view.WindowManager;

import org.junit.Test;

public final class AppSettingsTest {
    @Test
    public void legacySettingsMapToStableIds() {
        AppSettings.LanguagePair chineseToJapanese =
                AppSettings.legacyDirection("中→日");
        AppSettings.LanguagePair japaneseToChinese =
                AppSettings.legacyDirection("日→中");

        assertEquals("zh", chineseToJapanese.source);
        assertEquals("ja", chineseToJapanese.target);
        assertEquals("ja", japaneseToChinese.source);
        assertEquals("zh", japaneseToChinese.target);
        assertEquals("boss", AppSettings.relationId("领导"));
        assertEquals("close_friend", AppSettings.relationId("好朋友"));
        assertEquals("apology", AppSettings.sceneId("道歉"));
        assertEquals("auto", AppSettings.sceneId("自动"));
    }

    @Test
    public void selectingTheOtherLanguageSwapsThePair() {
        AppSettings.LanguagePair sourceChanged =
                AppSettings.afterLanguageChange("zh", "ja", "ja", true);
        AppSettings.LanguagePair targetChanged =
                AppSettings.afterLanguageChange("zh", "ja", "zh", false);

        assertEquals("ja", sourceChanged.source);
        assertEquals("zh", sourceChanged.target);
        assertEquals("ja", targetChanged.source);
        assertEquals("zh", targetChanged.target);
    }

    @Test
    public void overlayDisplaySettingsStayWithinUsableBounds() {
        assertEquals(35, AppSettings.overlayOpacity(0));
        assertEquals(90, AppSettings.overlayOpacity(90));
        assertEquals(100, AppSettings.overlayOpacity(140));
        assertEquals(260, AppSettings.overlayWidthDp(120));
        assertEquals(420, AppSettings.overlayWidthDp(420));
        assertEquals(600, AppSettings.overlayWidthDp(900));
        assertEquals(0, AppSettings.overlayHeightDp(0));
        assertEquals(150, AppSettings.overlayHeightDp(80));
        assertEquals(800, AppSettings.overlayHeightDp(900));
    }

    @Test
    public void overlayDoesNotBlurOrBlockTouchesOutsideItsBounds() {
        int flags = AccessibilityOverlayService.windowFlags();

        assertEquals(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        assertEquals(0, flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    }
}
