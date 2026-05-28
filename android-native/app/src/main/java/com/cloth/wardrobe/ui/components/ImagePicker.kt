package com.cloth.wardrobe.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cloth.wardrobe.data.ImageCompressor
import com.cloth.wardrobe.ui.WardrobeConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 选图后已压缩到应用缓存，保存时可直接复制到 imageRef 对应文件。 */
data class PickedImage(
    val file: File,
    val displayUri: Uri
)

/**
 * 与 uni-app 录入页一致：弹出「从相册选择 / 拍照」。
 * 返回前在后台压缩，避免大图预览 OOM、保存时 content Uri 失效。
 */
@Composable
fun rememberImagePicker(
    onPicked: (PickedImage?) -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun deliverPicked(uri: Uri?) {
        if (uri == null) {
            onPicked(null)
            return
        }
        scope.launch {
            try {
                val picked = withContext(Dispatchers.IO) {
                    compressUriToCache(context, uri)
                }
                onPicked(picked)
            } catch (e: Exception) {
                onError(
                    when (e) {
                        is IllegalArgumentException -> e.message ?: "图片处理失败，请换一张"
                        is IOException -> "无法读取图片，请换一张或重试"
                        else -> e.message ?: "图片处理失败，请换一张"
                    }
                )
                onPicked(null)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        deliverPicked(uri)
    }

    val legacyGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            deliverPicked(result.data?.data)
        } else {
            onPicked(null)
        }
    }

    fun openGallery() {
        if (PickVisualMedia.isPhotoPickerAvailable(context)) {
            photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        } else {
            legacyGalleryLauncher.launch(legacyGalleryPickIntent())
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = if (success) pendingCameraUri else null
        pendingCameraUri = null
        deliverPicked(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val uri = createCameraUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                onError("无法启动相机")
            }
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                val uri = createCameraUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                onError("无法启动相机")
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showSheet) {
        AlertDialog(
            onDismissRequest = { showSheet = false },
            containerColor = WardrobeConstants.CardBg,
            titleContentColor = Color(0xFF222222),
            shape = RoundedCornerShape(12.dp),
            title = { Text("添加照片", fontSize = 15.sp) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showSheet = false
                            openGallery()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("从相册选择") }
                    TextButton(
                        onClick = {
                            showSheet = false
                            openCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("拍照") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSheet = false }) { Text("取消") }
            }
        )
    }

    return { showSheet = true }
}

private fun compressUriToCache(context: Context, uri: Uri): PickedImage {
    val file = File(context.cacheDir, "pick_${System.currentTimeMillis()}.jpg")
    ImageCompressor.compressUriToJpegFile(context, uri, file)
    val displayUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return PickedImage(file, displayUri)
}

/** 旧系统无 Photo Picker 时，走系统相册（Pictures），而非文件管理器 Download。 */
private fun legacyGalleryPickIntent(): Intent =
    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
        type = "image/*"
    }

private fun createCameraUri(context: Context): Uri {
    context.cacheDir.mkdirs()
    val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    if (!file.exists() && !file.createNewFile()) {
        throw IOException("无法创建拍照文件")
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
