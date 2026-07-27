package com.asktrix.agent.feature.attendance

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * In-app photo capture for attendance (§11).
 *
 * CameraX rather than an `ACTION_IMAGE_CAPTURE` intent, for two reasons that both matter on this
 * fleet:
 *
 *  1. On a Device-Owner-managed handset the Play Store and third-party apps are restricted, so there
 *     may be no camera app to hand off to. An intent-based capture would simply fail on a correctly
 *     locked-down device.
 *  2. Handing off to another app means the photo lands in that app's storage first. Capturing
 *     in-process keeps the image in memory until it is encrypted and queued.
 *
 * The JPEG is captured at low resolution and quality on purpose. This proves an employee was
 * somewhere at a time; it is not a portrait. A smaller image also uploads on a weak rural connection
 * and costs the employee less data.
 */
class AttendanceCamera(private val context: Context) {

    private var imageCapture: ImageCapture? = null

    suspend fun bind(lifecycleOwner: LifecycleOwner, preview: Preview): Boolean =
        suspendCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(JPEG_QUALITY)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        capture,
                    )
                    imageCapture = capture
                }.fold(
                    onSuccess = { continuation.resume(true) },
                    onFailure = { continuation.resume(false) },
                )
            }, ContextCompat.getMainExecutor(context))
        }

    /** Captures a JPEG. Returns null rather than throwing - a failed photo must not block check-in. */
    suspend fun capture(): ByteArray? = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.use { it.toJpegBytes() }
                    if (continuation.isActive) continuation.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

    fun release() {
        imageCapture = null
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return ByteArrayOutputStream(bytes.size).use { it.write(bytes); it.toByteArray() }
    }

    private companion object {
        const val JPEG_QUALITY = 70
    }
}
