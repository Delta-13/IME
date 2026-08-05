package com.toneime.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecurePrefs {
    static final String PREFS = "toneime";
    private static final String KEY_ALIAS = "ToneIME.ApiKey";
    private static final String LEGACY_SECRET = "api_key_ciphertext";
    private static final String LEGACY_IV = "api_key_iv";
    private static final String SECRET_PREFIX = "api_key_ciphertext.";
    private static final String IV_PREFIX = "api_key_iv.";

    private SecurePrefs() {
    }

    static void saveApiKey(Context context, String provider, String value) throws Exception {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String normalizedProvider = ApiProvider.normalize(provider);
        String secret = secretKey(normalizedProvider);
        String iv = ivKey(normalizedProvider);
        if (value.trim().isEmpty()) {
            SharedPreferences.Editor editor = preferences.edit().remove(secret).remove(iv);
            if (ApiProvider.OPENAI.equals(normalizedProvider)) {
                editor.remove(LEGACY_SECRET).remove(LEGACY_IV);
            }
            editor.apply();
            return;
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor editor = preferences.edit()
                .putString(secret, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(iv, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        if (ApiProvider.OPENAI.equals(normalizedProvider)) {
            editor.remove(LEGACY_SECRET).remove(LEGACY_IV);
        }
        editor.apply();
    }

    static String loadApiKey(Context context, String provider) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String normalizedProvider = ApiProvider.normalize(provider);
        String apiKey = decrypt(
                preferences,
                secretKey(normalizedProvider),
                ivKey(normalizedProvider));
        if (!apiKey.trim().isEmpty() || !ApiProvider.OPENAI.equals(normalizedProvider)) {
            return apiKey;
        }
        return decrypt(preferences, LEGACY_SECRET, LEGACY_IV);
    }

    private static String decrypt(
            SharedPreferences preferences,
            String secret,
            String iv) {
        String encrypted = preferences.getString(secret, "");
        String initializationVector = preferences.getString(iv, "");
        if (encrypted.trim().isEmpty() || initializationVector.trim().isEmpty()) {
            return "";
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(
                            128,
                            Base64.decode(initializationVector, Base64.NO_WRAP)));
            return new String(
                    cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            preferences.edit().remove(secret).remove(iv).apply();
            return "";
        }
    }

    private static String secretKey(String provider) {
        return SECRET_PREFIX + ApiProvider.normalize(provider);
    }

    private static String ivKey(String provider) {
        return IV_PREFIX + ApiProvider.normalize(provider);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
