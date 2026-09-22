package com.toneime.android;

import static org.junit.Assert.*;

import android.content.res.Configuration;
import org.junit.Test;

public final class AppThemeTest {
    @Test public void missingOrUnknownPreferenceFollowsSystem() {
        assertEquals("system", AppTheme.normalize(null));
        assertEquals("system", AppTheme.normalize("unknown"));
        assertEquals("light", AppTheme.normalize("light"));
        assertEquals("dark", AppTheme.normalize("dark"));
    }

    @Test public void explicitChoiceOverridesSystem() {
        assertTrue(AppTheme.isDark("dark", Configuration.UI_MODE_NIGHT_NO));
        assertFalse(AppTheme.isDark("light", Configuration.UI_MODE_NIGHT_YES));
    }

    @Test public void systemModeMasksOtherConfigurationBits() {
        assertTrue(AppTheme.isDark("system",
                Configuration.UI_MODE_NIGHT_YES | Configuration.UI_MODE_TYPE_CAR));
        assertFalse(AppTheme.isDark("system",
                Configuration.UI_MODE_NIGHT_NO | Configuration.UI_MODE_TYPE_CAR));
        assertFalse(AppTheme.isDark("system", Configuration.UI_MODE_NIGHT_UNDEFINED));
    }
}
