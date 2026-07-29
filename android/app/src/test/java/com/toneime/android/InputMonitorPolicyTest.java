package com.toneime.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class InputMonitorPolicyTest {
    @Test
    public void onlyCurrentNonSensitiveTextCanBeReplaced() {
        assertTrue(InputMonitorPolicy.canMonitor(
                "jp.naver.line.android", "com.toneime.android", true, false, false, "你好"));
        assertFalse(InputMonitorPolicy.canMonitor(
                "jp.naver.line.android", "com.toneime.android", true, true, false, "secret"));
        assertFalse(InputMonitorPolicy.canMonitor(
                "com.android.settings", "com.toneime.android",
                true, false, true, "Search settings"));
        assertFalse(InputMonitorPolicy.canMonitor(
                "com.toneime.android", "com.toneime.android", true, false, false, "你好"));
        assertFalse(InputMonitorPolicy.canMonitor(
                "jp.naver.line.android", "com.toneime.android", true, false, false, " "));

        assertTrue(InputMonitorPolicy.canReplace("你好", "你好", "こんにちは"));
        assertFalse(InputMonitorPolicy.canReplace("你好", "你好！", "こんにちは"));
    }
}
