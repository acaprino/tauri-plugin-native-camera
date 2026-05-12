// Copyright 2026 Kushal Das
// SPDX-License-Identifier: MIT

package `in`.kushaldas.plugin.nativecamera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.Permission
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

private const val TAG = "NativeCameraPlugin"
private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
private const val CAMERA_REQUEST_CODE = 12345

@InvokeArg
class TakePictureArgs

@TauriPlugin(
    permissions = [
        Permission(strings = [Manifest.permission.CAMERA], alias = "camera")
    ]
)
class NativeCameraPlugin(private val activity: Activity) : Plugin(activity) {

    private var currentPhotoFile: File? = null
    private var pendingInvoke: Invoke? = null

    @Command
    fun takePicture(invoke: Invoke) {
        Log.d(TAG, "takePicture called")

        // Check camera permission
        if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting camera permission")
            pendingInvoke = invoke
            requestPermissionForAlias("camera", invoke, "handleCameraPermissionResult")
            return
        }

        launchCamera(invoke)
    }

    @PermissionCallback
    private fun handleCameraPermissionResult(invoke: Invoke) {
        Log.d(TAG, "handleCameraPermissionResult called")

        if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            launchCamera(invoke)
        } else {
            Log.e(TAG, "Camera permission denied")
            invoke.reject("Camera permission denied")
        }
    }

    private fun launchCamera(invoke: Invoke) {
        Log.d(TAG, "Launching camera")

        try {
            // Create temp file for photo
            val photoFile = createTempPhotoFile()
            currentPhotoFile = photoFile

            // Get content URI via the app's existing FileProvider
            val photoUri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile
            )

            // Create camera intent.
            //
            // The camera app receives a writable URI to which it writes the captured
            // JPEG. It never needs to READ from that URI -- the file is empty until
            // the camera writes into it. FLAG_GRANT_READ_URI_PERMISSION would only
            // expand the granted scope (any adjacent files the consumer app's
            // FileProvider exposes via the same authority become readable from
            // inside the camera app's process), which is an over-grant relative to
            // the operation.
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // Launch camera directly (don't use resolveActivity - unreliable on Android 11+)
            Log.d(TAG, "Starting camera activity")
            startActivityForResult(invoke, cameraIntent, "handleCameraResult")

        } catch (e: Exception) {
            Log.e(TAG, "Error launching camera", e)
            invoke.reject("Failed to launch camera: ${e.message}")
        }
    }

    @ActivityCallback
    private fun handleCameraResult(invoke: Invoke, result: ActivityResult) {
        Log.d(TAG, "handleCameraResult: resultCode=${result.resultCode}")

        if (result.resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Camera cancelled by user")
            currentPhotoFile?.delete()
            currentPhotoFile = null
            invoke.reject("Camera operation was cancelled by user")
            return
        }

        val photoFile = currentPhotoFile
        if (photoFile == null || !photoFile.exists()) {
            Log.e(TAG, "Photo file not found")
            invoke.reject("Failed to read captured photo: File not found")
            return
        }

        try {
            Log.d(TAG, "Reading photo from ${photoFile.absolutePath}")

            // Read the image and apply EXIF rotation if needed
            val bitmap = readAndRotateBitmap(photoFile)
            if (bitmap == null) {
                invoke.reject("Failed to decode captured photo")
                return
            }

            // Convert to base64 JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // Build result
            val resultObj = JSObject().apply {
                put("imageData", base64Image)
                put("width", bitmap.width)
                put("height", bitmap.height)
            }

            Log.d(TAG, "Photo captured: ${bitmap.width}x${bitmap.height}")

            // Clean up
            bitmap.recycle()
            photoFile.delete()
            currentPhotoFile = null

            invoke.resolve(resultObj)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing photo", e)
            photoFile.delete()
            currentPhotoFile = null
            invoke.reject("Failed to read captured photo: ${e.message}")
        }
    }

    private fun createTempPhotoFile(): File {
        val cacheDir = File(activity.cacheDir, "native_camera")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        return File(cacheDir, "photo_$timestamp.jpg")
    }

    private fun readAndRotateBitmap(file: File): Bitmap? {
        // First, decode bounds to check size
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Calculate sample size for large images (max 2048px)
        val maxDim = 2048
        var sampleSize = 1
        if (options.outWidth > maxDim || options.outHeight > maxDim) {
            val widthRatio = options.outWidth / maxDim
            val heightRatio = options.outHeight / maxDim
            sampleSize = maxOf(widthRatio, heightRatio)
        }

        // Decode with sample size
        options.apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        // Read EXIF orientation and rotate if needed
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation != 0f) {
                val matrix = android.graphics.Matrix().apply {
                    postRotate(rotation)
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                return rotated
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading EXIF", e)
        }

        return bitmap
    }
}
