package com.cloth.wardrobe.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.ui.ClothFields
import com.cloth.wardrobe.ui.ColorFormState
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.buildColorsPayload
import com.cloth.wardrobe.ui.colorsToForm
import com.cloth.wardrobe.ui.components.AppTopBar
import com.cloth.wardrobe.ui.components.CompactDatePickerField
import com.cloth.wardrobe.ui.components.CompactInput
import com.cloth.wardrobe.ui.components.CompactSizeRow
import com.cloth.wardrobe.ui.components.CompactTextArea
import com.cloth.wardrobe.ui.components.EditFormField
import com.cloth.wardrobe.ui.components.PickedImage
import com.cloth.wardrobe.ui.components.rememberImagePicker
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClothEditScreen(
    repository: WardrobeRepository,
    clothId: String?,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEdit = !clothId.isNullOrBlank()
    var name by remember { mutableStateOf("") }
    var season by remember { mutableStateOf("夏") }
    var type by remember { mutableStateOf("上衣") }
    var originalType by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tempMin by remember { mutableStateOf("") }
    var tempMax by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("active") }
    var colorForm by remember { mutableStateOf(ColorFormState()) }
    var sizes by remember(clothId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sizesLoadTick by remember(clothId) { mutableIntStateOf(0) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var pickedImageFile by remember { mutableStateOf<File?>(null) }
    var hasImage by remember { mutableStateOf(false) }
    var existingRef by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(isEdit) }

    val choosePhoto = rememberImagePicker(
        onPicked = { picked: PickedImage? ->
            if (picked != null) {
                pickedImageFile = picked.file
                imageUri = picked.displayUri
                hasImage = true
            }
        },
        onError = { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    )

    LaunchedEffect(clothId) {
        if (clothId.isNullOrBlank()) {
            loading = false
            hasImage = false
            sizes = emptyMap()
            sizesLoadTick++
            return@LaunchedEffect
        }
        loading = true
        val c = repository.getCloth(clothId)
        if (c != null) {
            name = c.name
            season = c.season
            type = c.type
            originalType = c.type
            note = c.note
            tempMin = c.tempMin?.toString() ?: ""
            tempMax = c.tempMax?.toString() ?: ""
            purchaseDate = c.purchaseDate
            purchasePrice = c.purchasePrice
            material = c.material
            status = c.status
            colorForm = colorsToForm(c)
            sizes = JsonHelpers.parseSizes(c.sizesJson)
            sizesLoadTick++
            existingRef = c.imageRef
            hasImage = ImageStore.fileForRef(context, c.imageRef).isFile
        }
        loading = false
    }

    val sizeFields = remember(type, sizes, sizesLoadTick) {
        ClothFields.getEditSizeFields(type, sizes)
    }
    val showMaterial = ClothFields.typeHasMaterial(type)

    Scaffold(
        topBar = { AppTopBar(if (isEdit) "编辑衣服" else "录入衣服", onBack = onDone) },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        if (loading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WardrobeConstants.PageBg)
                    .clickable { choosePhoto() },
                contentAlignment = Alignment.Center
            ) {
                val imgFile = if (existingRef.isNotBlank()) ImageStore.fileForRef(context, existingRef) else null
                when {
                    imageUri != null -> AsyncImage(
                        ImageRequest.Builder(context)
                            .data(imageUri)
                            .size(Size(480, 480))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    imgFile?.isFile == true -> AsyncImage(
                        ImageRequest.Builder(context)
                            .data(imgFile)
                            .size(Size(480, 480))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text("拍照 / 从相册选择", fontSize = 14.sp, color = Color(0xFF999999))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                EditFormField("名称") {
                    CompactInput(name, { name = it }, placeholder = "例如：白色衬衫")
                }
                EditFormField("颜色", inlineHint = "可多选") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.PRESET_COLORS.forEach { c ->
                            val on = if (c == "其他") colorForm.otherSelected
                            else colorForm.selectedColors.contains(c)
                            ColorChip(c, on) {
                                colorForm = if (c == "其他") {
                                    colorForm.copy(otherSelected = !colorForm.otherSelected)
                                } else {
                                    val set = colorForm.selectedColors.toMutableSet()
                                    if (set.contains(c)) set.remove(c) else set.add(c)
                                    colorForm.copy(selectedColors = set)
                                }
                            }
                        }
                    }
                    if (colorForm.otherSelected) {
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CompactInput(
                                colorForm.colorCustom,
                                { colorForm = colorForm.copy(colorCustom = it) },
                                placeholder = "自定义颜色名，如：藏青"
                            )
                            CompactInput(
                                colorForm.colorHex,
                                { colorForm = colorForm.copy(colorHex = it) },
                                placeholder = "#色值 选填 如 #2c5282"
                            )
                        }
                    }
                }
                EditFormField("季节") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.SEASONS.forEach { s -> ChipText(s, season == s) { season = s } }
                    }
                }
                EditFormField("类型") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.TYPES.forEach { t ->
                            ChipText(t, type == t) {
                                val prev = type
                                type = t
                                if (!isEdit || prev != t) {
                                    if (!isEdit || originalType != t) {
                                        sizes = emptyMap()
                                        sizesLoadTick++
                                    }
                                }
                            }
                        }
                    }
                }
                EditFormField("适宜温度 (℃)") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactInput(
                            tempMin,
                            { tempMin = it },
                            placeholder = "最低",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Text("—", fontSize = 14.sp, color = Color(0xFF999999))
                        CompactInput(
                            tempMax,
                            { tempMax = it },
                            placeholder = "最高",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "选填；留空表示未设置",
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (sizeFields.isNotEmpty()) {
                    EditFormField("尺寸") {
                        sizeFields.forEach { f ->
                            CompactSizeRow(
                                label = f.label,
                                value = sizes[f.key].orEmpty(),
                                onValueChange = { newVal -> sizes = sizes + (f.key to newVal) }
                            )
                        }
                    }
                }
                EditFormField("买入时间") {
                    CompactDatePickerField(purchaseDate, { purchaseDate = it })
                    if (purchaseDate.isNotBlank()) {
                        Text(
                            "清除",
                            fontSize = 12.sp,
                            color = WardrobeConstants.Accent,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { purchaseDate = "" }
                        )
                    }
                }
                EditFormField("买入价钱") {
                    CompactInput(
                        purchasePrice,
                        { purchasePrice = it },
                        placeholder = "选填，如 199",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                if (showMaterial) {
                    EditFormField("材质") {
                        CompactInput(material, { material = it }, placeholder = "选填，如：棉、涤纶")
                    }
                }
                EditFormField("备注", showDivider = isEdit) {
                    CompactTextArea(note, { note = it })
                }
                if (isEdit) {
                    EditFormField("状态", showDivider = false) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChipText("在穿", status == "active") { status = "active" }
                            ChipText("已扔掉", status == "discarded") { status = "discarded" }
                        }
                        if (status == "discarded") {
                            Text(
                                "标记为已扔掉后，主衣柜不再显示，可在「已扔掉」中查看",
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请填写名称", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!hasImage && imageUri == null) {
                        Toast.makeText(context, "请添加照片", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        try {
                        val colorPayload = buildColorsPayload(colorForm)
                        if (colorPayload == null) {
                            Toast.makeText(context, "请至少选择一种颜色", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        var tMin = tempMin.trim().toIntOrNull()
                        var tMax = tempMax.trim().toIntOrNull()
                        if (tMin != null && tMax != null && tMin > tMax) {
                            val s = tMin; tMin = tMax; tMax = s
                        }
                        val existing = if (clothId != null) repository.getCloth(clothId) else null
                        val id = clothId ?: UUID.randomUUID().toString()
                        val ref = existing?.imageRef?.takeIf { it.isNotBlank() }
                            ?: ImageStore.refForClothId(id)
                        when {
                            pickedImageFile != null ->
                                repository.saveImageFromFile(ref, pickedImageFile!!)
                            imageUri != null ->
                                repository.saveImageFromUri(ref, imageUri!!)
                        }
                        hasImage = true
                        val discarded = status == "discarded"
                        val entity = ClothEntity(
                            id = id,
                            name = name.trim(),
                            colorsJson = colorPayload.colorsJson,
                            colorHexMapJson = colorPayload.colorHexMapJson,
                            season = season,
                            type = ClothFields.migrateType(type),
                            imageRef = ref,
                            note = note.trim(),
                            tempMin = tMin,
                            tempMax = tMax,
                            sizesJson = JsonHelpers.sizesToJson(
                                sizes.filter { it.value.isNotBlank() }
                            ),
                            purchaseDate = purchaseDate.trim(),
                            purchasePrice = purchasePrice.trim(),
                            material = if (showMaterial) material.trim() else "",
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            status = if (discarded) "discarded" else "active",
                            discardedAt = if (discarded) existing?.discardedAt ?: System.currentTimeMillis() else null
                        )
                        repository.saveCloth(entity)
                        onDone()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "保存失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = WardrobeConstants.Accent),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Text(if (isEdit) "保存修改" else "保存", fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorChip(name: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFFFFF0F3) else Color(0xFFF7F7F8))
            .border(
                1.dp,
                if (active) WardrobeConstants.Accent else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(WardrobeConstants.COLOR_HEX[name] ?: Color.Gray)
                .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(50))
        )
        Text(
            name,
            fontSize = 13.sp,
            color = if (active) WardrobeConstants.Accent else Color(0xFF555555)
        )
    }
}

@Composable
private fun ChipText(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFFFFF0F3) else Color(0xFFF7F7F8))
            .border(
                1.dp,
                if (active) WardrobeConstants.Accent else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (active) WardrobeConstants.Accent else Color(0xFF555555)
    )
}
