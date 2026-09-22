package com.toneime.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppTheme.applyWindow(this)
    }

    override fun onResume() {
        super.onResume()
        AppSettings.preferences(this).edit().putBoolean(AppSettings.APP_VISIBLE, true).apply()
        sendBroadcast(Intent(AppSettings.ACTION_OVERLAY_HIDE).setPackage(packageName))
    }

    override fun onStop() {
        super.onStop()
        AppSettings.preferences(this).edit().putBoolean(AppSettings.APP_VISIBLE, false).apply()
        sendBroadcast(Intent(AppSettings.ACTION_OVERLAY_SHOW).setPackage(packageName))
    }

    override fun getMainComponentName(): String = "ToneIME"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
