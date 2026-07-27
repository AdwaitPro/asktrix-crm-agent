package com.asktrix.agent.core.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.asktrix.agent.core.common.log.AsktrixLog

/**
 * The in-call screen (§5, §6).
 *
 * Hosts the WebRTC call page in a WebView rather than embedding a native WebRTC stack. That is a
 * deliberate trade: the native library adds tens of megabytes and a large surface for a call flow
 * that is fundamentally a few seconds of setup and an audio stream. Android's WebView has shipped
 * full WebRTC for years, and the same page serves the customer, so there is exactly one call
 * implementation to reason about and keep correct.
 *
 * **On the microphone permission.** This app declares `RECORD_AUDIO`, which earlier iterations
 * deliberately avoided. The reason it is correct here and was wrong before: recording a call carried
 * over the public telephone network is blocked by Android and unlawful for us to route in India.
 * This call never touches that network. It is voice over data between two endpoints we control, so
 * the microphone is simply the microphone, and the recording is ours to make. That is what makes §6
 * deliverable as originally written.
 *
 * The customer's phone number is still never present on this device. The call is addressed by
 * client id, and the far end joins through a one-time link.
 */
class CallActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) loadCall() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls display the client's name, so the screen is covered by the same rule as the rest of
        // the app (§14-§20).
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        // Keep the screen on for the duration of the call.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            /**
             * Grants the page microphone access.
             *
             * Only ever granted to our own origin, and only for audio. A page cannot obtain the
             * camera through this path even if it asks.
             */
            override fun onPermissionRequest(request: PermissionRequest) {
                val audioOnly = request.resources
                    .filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    .toTypedArray()

                if (audioOnly.isEmpty()) {
                    request.deny()
                    return
                }
                runOnUiThread { request.grant(audioOnly) }
            }
        }

        // Let the page tell us when the call is over so the screen closes itself.
        webView.addJavascriptInterface(CallBridge(::finish), "AsktrixHost")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadCall()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun loadCall() {
        val url = intent.getStringExtra(EXTRA_CALL_URL)
        if (url.isNullOrBlank()) {
            AsktrixLog.e(TAG, "CallActivity started without a call URL")
            finish()
            return
        }
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }

    /** Bridge the page uses to close the call screen when the call ends. */
    class CallBridge(private val onEnded: () -> Unit) {
        @android.webkit.JavascriptInterface
        fun onCallEnded() {
            onEnded()
        }
    }

    companion object {
        private const val TAG = "CallActivity"
        private const val EXTRA_CALL_URL = "call_url"

        fun intent(context: Context, callUrl: String): Intent =
            Intent(context, CallActivity::class.java).putExtra(EXTRA_CALL_URL, callUrl)
    }
}
