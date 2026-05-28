package com.cloth.wardrobe

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.export.ImportResult
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.components.AppAlertDialog
import com.cloth.wardrobe.ui.navigation.WardrobeNavHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ClothApplication
        val repository = WardrobeRepository(this, app.store)

        setContent {
            val scope = rememberCoroutineScope()
            var refreshKey by remember { mutableIntStateOf(0) }
            var importSuccess by remember { mutableStateOf<ImportResult?>(null) }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        importSuccess = repository.importZip(uri)
                        refreshKey++
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "导入失败：${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            importSuccess?.let { result ->
                AppAlertDialog(
                    onDismissRequest = { importSuccess = null },
                    title = "导入成功",
                    text = "已写入：${result.summaryText()}",
                    confirmText = "知道了",
                    onConfirm = { importSuccess = null }
                )
            }

            MaterialTheme(
                colorScheme = lightColorScheme(primary = WardrobeConstants.Accent)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WardrobeNavHost(
                        repository = repository,
                        onImportZip = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                        importTick = refreshKey
                    )
                }
            }
        }
    }
}
