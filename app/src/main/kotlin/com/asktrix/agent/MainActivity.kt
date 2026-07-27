package com.asktrix.agent

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity host.
 *
 * `FLAG_SECURE` is applied before `setContent` and is never removed for the lifetime of the process.
 * It blocks screenshots, screen recording, and appearance in the recents thumbnail (§14-§20).
 *
 * It is applied here, at the only window that exists, rather than per-screen: a per-screen approach
 * fails the moment someone adds a screen and forgets. Note the documented limit - this cannot stop a
 * second phone photographing the display, which is exactly why customer contact data is masked at the
 * server and never sent to the device (docs/adr/0003-server-side-pii-masking.md).
 *
 * `BuildConfig.ALLOW_SCREENSHOTS` exists only so QA and screenshot tests can capture debug builds.
 * It is hardcoded false in the release build type, so a shipped APK cannot have it enabled.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        enableEdgeToEdge()

        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        setContent {
            AsktrixTheme {
                AsktrixApp(onReady = { keepSplash = false })
            }
        }
    }
}
