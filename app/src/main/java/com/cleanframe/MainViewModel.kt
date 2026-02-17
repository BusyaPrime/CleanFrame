package com.cleanframe

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cleanframe.cv.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    var currentImage: Bitmap? by mutableStateOf(null)
        private set

    var maskPath: Path by mutableStateOf(Path())
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    private var strokeWidthBitmap: Float by mutableFloatStateOf(18f)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun setStrokeWidthBitmapPx(value: Float) {
        // Avoid insane values on tiny scales
        strokeWidthBitmap = value.coerceIn(1f, 200f)
    }

    fun clearMask() {
        maskPath = Path()
    }

    fun setImage(bitmap: Bitmap) {
        currentImage = bitmap
        clearMask()
    }

    fun startStroke(x: Float, y: Float) {
        val updated = Path(maskPath)
        updated.moveTo(x, y)
        maskPath = updated
    }

    fun extendStroke(x: Float, y: Float) {
        val updated = Path(maskPath)
        updated.lineTo(x, y)
        maskPath = updated
    }

    fun loadImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                isLoading = true
                val bmp = withContext(Dispatchers.IO) { loadBitmapWithCoil(context, uri) }
                if (bmp != null) {
                    setImage(bmp)
                } else {
                    _messages.tryEmit("Не удалось загрузить изображение")
                }
            } catch (t: Throwable) {
                _messages.tryEmit("Ошибка загрузки: ${t.message ?: "unknown"}")
            } finally {
                isLoading = false
            }
        }
    }

    /**

     */
    fun processInpainting() {
        val src = currentImage ?: run {
            _messages.tryEmit("Сначала выбери изображение")
            return
        }


        if (isPathEmpty(maskPath)) {
            _messages.tryEmit("Нарисуй маску поверх вотермарки")
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    OpenCVUtils.inpaintImage(
                        original = src,
                        maskPath = maskPath,
                        strokeWidth = strokeWidthBitmap
                    )
                }
                currentImage = result
                clearMask()
                _messages.tryEmit("Готово ✅")
            } catch (t: Throwable) {
                _messages.tryEmit("Ошибка inpaint: ${t.message ?: "unknown"}")
            } finally {
                isLoading = false
            }
        }
    }

    fun saveCurrentToGallery(context: Context) {
        val bmp = currentImage ?: run {
            _messages.tryEmit("Нечего сохранять")
            return
        }
        viewModelScope.launch {
            isLoading = true
            try {
                val ok = withContext(Dispatchers.IO) { saveBitmapToGallery(context, bmp) }
                if (ok) _messages.tryEmit("Сохранено в галерею ✅")
                else _messages.tryEmit("Не удалось сохранить (проверь разрешения)")
            } catch (t: Throwable) {
                _messages.tryEmit("Ошибка сохранения: ${t.message ?: "unknown"}")
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun loadBitmapWithCoil(context: Context, uri: Uri): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // важно: нужен software bitmap для обработки
            .build()

        val result = loader.execute(request)
        return (result as? SuccessResult)?.drawable?.let { drawable ->

            drawable.toBitmap()
        }
    }

    private fun isPathEmpty(path: Path): Boolean {

        val r = android.graphics.RectF()
        path.computeBounds(r, true)
        return r.width() <= 0f && r.height() <= 0f
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        val fileName = "cleanframe_${System.currentTimeMillis()}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/CleanFrame"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                ok
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
        }
    }
}
