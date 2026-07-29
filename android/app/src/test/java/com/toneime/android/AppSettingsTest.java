package com.toneime.android;

import static org.junit.Assert.assertEquals;

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
}
