package com.toneime.android;

final class InputMonitorPolicy {
    private InputMonitorPolicy() {
    }

    static boolean canMonitor(
            String packageName,
            String ownPackage,
            boolean editable,
            boolean password,
            boolean showingHint,
            String text) {
        if (!editable
                || password
                || showingHint
                || ownPackage.equals(packageName)
                || text == null) {
            return false;
        }
        int trimmedLength = text.trim().length();
        return trimmedLength >= 2 && text.length() <= 800;
    }

    static boolean canReplace(String translatedSource, String currentSource, String translation) {
        return translatedSource != null
                && translatedSource.equals(currentSource)
                && translation != null
                && !translation.trim().isEmpty();
    }
}
