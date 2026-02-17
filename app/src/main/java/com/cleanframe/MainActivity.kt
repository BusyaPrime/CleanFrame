package com.cleanframe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanframe.ui.theme.CleanFrameTheme
import com.cleanframe.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest
import org.opencv.android.OpenCVLoader
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = res.data?.data
                if (uri != null) {
                    viewModel.loadImageFromUri(this, uri)
                } else {
                    toast("Не удалось получить URI изображения")
                }
            }
        }

    private val requestReadPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val ok = granted.values.all { it }
            if (ok) {
                launchPickImage()
            } else {
                toast("Нужны разрешения на чтение изображений")
            }
        }

    private val requestWritePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) {
                viewModel.saveCurrentToGallery(this)
            } else {
                toast("Нужно разрешение на запись (для Android 9 и ниже)")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openCvOk = OpenCVLoader.initLocal()
        if (!openCvOk) {
            toast("OpenCV не инициализировался. Проверь модуль :opencv.")
        }

        setContent {
            CleanFrameTheme {
                val ctx = LocalContext.current


                LaunchedEffect(Unit) {
                    viewModel.messages.collectLatest { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel,
                        onPickImage = { checkReadPermissionAndPick() },
                        onSave = { checkWritePermissionAndSave() }
                    )
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun checkReadPermissionAndPick() {
        val perms = PermissionUtils.requiredReadPermissions()
        if (PermissionUtils.hasAll(this, perms)) {
            launchPickImage()
        } else {
            requestReadPermLauncher.launch(perms)
        }
    }

    private fun launchPickImage() {
        val intent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        pickImageLauncher.launch(intent)
    }

    private fun checkWritePermissionAndSave() {
        if (!PermissionUtils.needsWritePermissionForLegacySave()) {
            viewModel.saveCurrentToGallery(this)
            return
        }

        val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(this, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.saveCurrentToGallery(this)
        } else {
            requestWritePermLauncher.launch(perm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onPickImage: () -> Unit,
    onSave: () -> Unit
) {
    val bitmap = viewModel.currentImage
    val isLoading = viewModel.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CleanFrame") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.processInpainting() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("REMOVE")
            }
        },
        bottomBar = {
            BottomBar(
                onPickImage = onPickImage,
                onClearMask = { viewModel.clearMask() },
                onSave = onSave,
                enabled = !isLoading
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ImageCanvas(
                bitmap = bitmap,
                maskPath = viewModel.maskPath,
                isLoading = isLoading,
                onStrokeWidthComputed = { viewModel.setStrokeWidthBitmapPx(it) },
                onStrokeStart = { x, y -> viewModel.startStroke(x, y) },
                onStrokeMove = { x, y -> viewModel.extendStroke(x, y) }
            )
        }
    }
}

@Composable
private fun BottomBar(
    onPickImage: () -> Unit,
    onClearMask: () -> Unit,
    onSave: () -> Unit,
    enabled: Boolean
) {
    Surface(shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPickImage,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Pick Image")
            }
            OutlinedButton(
                onClick = onClearMask,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear Mask")
            }
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}

private data class DisplayTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val drawnW: Float,
    val drawnH: Float
)

@Composable
private fun ImageCanvas(
    bitmap: android.graphics.Bitmap?,
    maskPath: android.graphics.Path,
    isLoading: Boolean,
    onStrokeWidthComputed: (strokeWidthBitmapPx: Float) -> Unit,
    onStrokeStart: (xBitmap: Float, yBitmap: Float) -> Unit,
    onStrokeMove: (xBitmap: Float, yBitmap: Float) -> Unit
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val transform = remember(bitmap, viewport) {
        computeTransform(bitmap, viewport)
    }


    LaunchedEffect(transform) {
        if (transform != null) {
            val desiredScreenStrokePx = 28f
            val strokeBitmap = (desiredScreenStrokePx / transform.scale).coerceAtLeast(1f)
            onStrokeWidthComputed(strokeBitmap)
        }
    }

    fun screenToBitmap(pos: Offset): Offset? {
        val bmp = bitmap ?: return null
        val t = transform ?: return null

        val x = (pos.x - t.offsetX) / t.scale
        val y = (pos.y - t.offsetY) / t.scale

        if (x < 0f || y < 0f || x > bmp.width.toFloat() || y > bmp.height.toFloat()) return null
        return Offset(x, y)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(bitmap, transform) {
                    if (bitmap == null || transform == null) return@pointerInput

                    detectDragGestures(
                        onDragStart = { start ->
                            val bmpPos = screenToBitmap(start)
                            if (bmpPos != null) onStrokeStart(bmpPos.x, bmpPos.y)
                        },
                        onDrag = { change, _ ->
                            val bmpPos = screenToBitmap(change.position)
                            if (bmpPos != null) onStrokeMove(bmpPos.x, bmpPos.y)

                        }
                    )
                }
        ) {
            val bmp = bitmap
            val t = transform
            if (bmp == null || t == null) {

                drawContext.canvas.nativeCanvas.apply {

                }
            } else {

                val img = bmp.asImageBitmap()
                drawImage(
                    image = img,
                    dstOffset = IntOffset(t.offsetX.toInt(), t.offsetY.toInt()),
                    dstSize = IntSize(t.drawnW.toInt(), t.drawnH.toInt())
                )


                val composePath: ComposePath = maskPath.asComposePath()
                withTransform({
                    translate(t.offsetX, t.offsetY)
                    scale(t.scale, t.scale)
                }) {
                    drawPath(
                        path = composePath,
                        color = Color.Red.copy(alpha = 0.5f),
                        style = Stroke(
                            width = 18f, // реальное значение ширины задано в bitmap space в ViewModel; здесь достаточно "визуальной" ширины
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        if (bitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нажми Pick Image, выбери фото и обведи вотермарк красным",
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun computeTransform(
    bitmap: android.graphics.Bitmap?,
    viewport: IntSize
): DisplayTransform? {
    if (bitmap == null) return null
    if (viewport.width <= 0 || viewport.height <= 0) return null

    val vw = viewport.width.toFloat()
    val vh = viewport.height.toFloat()
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()

    val scale = min(vw / bw, vh / bh)
    val drawnW = bw * scale
    val drawnH = bh * scale
    val offsetX = (vw - drawnW) / 2f
    val offsetY = (vh - drawnH) / 2f

    return DisplayTransform(scale, offsetX, offsetY, drawnW, drawnH)
}
