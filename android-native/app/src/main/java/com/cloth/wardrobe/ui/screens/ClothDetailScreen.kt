package com.cloth.wardrobe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.data.WearLogUtils
import com.cloth.wardrobe.ui.ClothFields
import com.cloth.wardrobe.ui.ColorRecommendResult
import com.cloth.wardrobe.ui.DetailRow
import com.cloth.wardrobe.ui.BrowseOrderHolder
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.buildColorRecommendations
import com.cloth.wardrobe.ui.colorHexFor
import com.cloth.wardrobe.ui.components.AppTopBar
import com.cloth.wardrobe.ui.components.DetailAction
import com.cloth.wardrobe.ui.components.DetailActionBar
import com.cloth.wardrobe.ui.components.DetailActionStyle
import com.cloth.wardrobe.ui.components.DetailSwipeHost
import com.cloth.wardrobe.ui.isInspirationLinkedToCloth
import com.cloth.wardrobe.ui.hasClothColorsForRecommend
import com.cloth.wardrobe.ui.parseItemColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ClothDetailScreen(
    repository: WardrobeRepository,
    clothId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onWearStats: () -> Unit,
    onInspirationClick: (String) -> Unit = {},
    onClothClick: (String) -> Unit = {}
) {
    var browseIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(clothId) {
        browseIds = BrowseOrderHolder.clothIdsForSwipe(clothId)
            ?: repository.browseClothIds(clothId)
    }
    val ids = browseIds
    when {
        ids == null -> {
            Scaffold(
                topBar = { AppTopBar("衣服详情", onBack = onBack) },
                containerColor = WardrobeConstants.PageBg
            ) { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = WardrobeConstants.Accent)
                }
            }
        }
        ids.size <= 1 -> {
            ClothDetailPageContent(
                repository, clothId, onBack, onEdit, onWearStats,
                onInspirationClick, onClothClick, showTopBar = true
            )
        }
        else -> {
            DetailSwipeHost(ids, clothId, "衣服详情", onBack) { id, padding, isActive ->
                ClothDetailPageContent(
                    repository, id, onBack, onEdit, onWearStats,
                    onInspirationClick, onClothClick,
                    showTopBar = false,
                    outerPadding = padding,
                    isActive = isActive
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClothDetailPageContent(
    repository: WardrobeRepository,
    clothId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onWearStats: () -> Unit,
    onInspirationClick: (String) -> Unit,
    onClothClick: (String) -> Unit,
    showTopBar: Boolean,
    outerPadding: PaddingValues = PaddingValues(),
    isActive: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var cloth by remember(clothId) { mutableStateOf<ClothEntity?>(null) }
    var wearLogs by remember { mutableStateOf(emptyList<com.cloth.wardrobe.data.WearLogEntity>()) }
    var colorRec by remember { mutableStateOf<ColorRecommendResult?>(null) }

    LaunchedEffect(clothId, isActive) {
        if (!isActive) return@LaunchedEffect
        cloth = null
        colorRec = null
        try {
            val item = repository.getCloth(clothId) ?: return@LaunchedEffect
            cloth = item
            wearLogs = repository.listWearLogs()
            val inspirations = repository.listInspirations()
            val allClothes = repository.listClothes()
            colorRec = withContext(Dispatchers.Default) {
                buildColorRecommendations(item, inspirations, allClothes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val item = cloth
            colorRec = ColorRecommendResult(
                ready = item != null && hasClothColorsForRecommend(item),
                matchedInspirations = emptyList(),
                companions = emptyList(),
                palette = emptyList(),
                matchedTotal = 0,
                companionTotal = 0
            )
        }
    }

    val pageBody: @Composable (PaddingValues) -> Unit = pageBody@ { padding ->
        val contentPadding = if (showTopBar) padding else outerPadding
        val item = cloth
        if (item == null) {
            Box(
                Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WardrobeConstants.Accent)
            }
            return@pageBody
        }

        val isDiscarded = item.status == "discarded"
        val img = ImageStore.fileForRef(context, item.imageRef)
        val wornToday = WearLogUtils.wornToday(wearLogs, clothId)
        val c30 = WearLogUtils.countWears(
            clothId, wearLogs,
            System.currentTimeMillis() - 30L * 86400000,
            System.currentTimeMillis()
        )
        val c365 = WearLogUtils.countWears(
            clothId, wearLogs,
            System.currentTimeMillis() - 365L * 86400000,
            System.currentTimeMillis()
        )
        val cAll = WearLogUtils.countWears(clothId, wearLogs, null, System.currentTimeMillis())
        val rec = colorRec
        val sections = ClothFields.buildDetailSections(item)
        val tempText = ClothFields.formatTempRange(item)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (img.isFile) {
                AsyncImage(
                    ImageRequest.Builder(context)
                        .data(img)
                        .size(Size(1080, 1920))
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无照片", color = WardrobeConstants.TextHint, fontSize = 14.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        item.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222222),
                        modifier = Modifier.weight(1f)
                    )
                    if (isDiscarded) {
                        Text(
                            "已扔掉",
                            fontSize = 11.sp,
                            color = WardrobeConstants.DiscardBrown,
                            modifier = Modifier
                                .background(Color(0xFFFFF8E6), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parseItemColors(item.colorsJson).forEach { c ->
                        TagChip {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(colorHexFor(item, c))
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
                            )
                            Text(c, fontSize = 11.sp, color = WardrobeConstants.TextSecondary)
                        }
                    }
                    TagChip { Text(item.season, fontSize = 11.sp, color = WardrobeConstants.TextSecondary) }
                    TagChip { Text(item.type, fontSize = 11.sp, color = WardrobeConstants.TextSecondary) }
                    TagChip(bg = Color(0xFFFFF0F3)) {
                        Text(tempText, fontSize = 11.sp, color = WardrobeConstants.Accent)
                    }
                }

                if (!isDiscarded) {
                    SectionDivider()
                    SectionTitle("穿着统计")
                    WearStatsGrid(c30, c365, cAll)
                    Text(
                        "上次穿着：${WearLogUtils.formatWearDate(WearLogUtils.lastWearDate(clothId, wearLogs))}",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (wornToday) Color(0xFFF0FAF0) else WardrobeConstants.Accent
                            )
                            .clickable {
                                scope.launch {
                                    if (wornToday) {
                                        WearLogUtils.todayWearLogId(wearLogs, clothId)?.let {
                                            repository.removeWearLog(it)
                                        }
                                    } else {
                                        repository.addWearLog(listOf(clothId))
                                    }
                                    wearLogs = repository.listWearLogs()
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (wornToday) {
                            Text(
                                "✓",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("今日已记录", color = Color(0xFF2E7D32), fontSize = 13.sp)
                                Text("再次点击取消", color = Color(0xFF66BB6A), fontSize = 10.sp)
                            }
                        } else {
                            Text("今天穿了这件", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Text(
                        "查看穿着排行 →",
                        color = WardrobeConstants.LinkBlue,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onWearStats() }
                    )
                }

                if (!isDiscarded) {
                    SectionDivider()
                    SectionTitle("颜色搭配参考")
                    when {
                        rec == null -> Unit
                        !rec.ready -> {
                            Text(
                                "请先为这件衣服填写颜色，才能匹配灵感库",
                                fontSize = 12.sp,
                                color = WardrobeConstants.TextHint,
                                lineHeight = 18.sp
                            )
                        }
                        else -> {
                            Text(
                                "已关联灵感优先；其余主色相近的灵感按本件季节优先排序，左右滑动查看全部",
                                fontSize = 11.sp,
                                color = WardrobeConstants.TextMuted,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            RecSubtitle("相关灵感")
                            if (rec.matchedInspirations.isEmpty()) {
                                Text(
                                    "暂无匹配灵感，请为灵感填写主色/辅色/点缀颜色标签",
                                    fontSize = 12.sp,
                                    color = WardrobeConstants.TextHint
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(
                                        items = rec.matchedInspirations,
                                        key = { it.id }
                                    ) { insp ->
                                        InspirationRecCard(
                                            insp,
                                            context,
                                            isInspirationLinkedToCloth(insp, clothId),
                                            onClick = { onInspirationClick(insp.id) }
                                        )
                                    }
                                }
                            }
                            RecSubtitle("可搭配单品", topGap = true)
                            if (rec.palette.isNotEmpty()) {
                                Text(
                                    "搭配色：${rec.palette.joinToString(" · ")}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            if (rec.companions.isEmpty()) {
                                Text(
                                    if (rec.matchedInspirations.isNotEmpty()) {
                                        "相关灵感暂无辅色/点缀，或衣柜里还没有同搭配色的${item.season}单品"
                                    } else if (rec.palette.isNotEmpty()) {
                                        "衣柜里暂无其他${item.season}同色系单品可搭配"
                                    } else {
                                        "暂无同色系单品"
                                    },
                                    fontSize = 12.sp,
                                    color = WardrobeConstants.TextHint,
                                    lineHeight = 18.sp
                                )
                            } else {
                                Text(
                                    "共 ${rec.companions.size} 件，左右滑动查看",
                                    fontSize = 11.sp,
                                    color = WardrobeConstants.TextMuted,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(
                                        items = rec.companions,
                                        key = { it.id }
                                    ) { c ->
                                        CompanionRecCard(c, context) {
                                            if (c.id != clothId) onClothClick(c.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                sections.forEach { section ->
                    SectionDivider()
                    SectionTitle(section.title)
                    DetailSizeGrid(section.rows)
                }
            }

            DetailActionBar(
                actions = listOf(
                    DetailAction(
                        label = "编辑",
                        onClick = { onEdit(clothId) },
                        style = DetailActionStyle.Primary
                    ),
                    if (isDiscarded) {
                        DetailAction(
                            label = "恢复",
                            onClick = {
                                scope.launch {
                                    repository.setClothStatus(clothId, false)
                                    onBack()
                                }
                            },
                            contentColor = Color(0xFF43A047),
                            borderColor = Color(0xFF43A047)
                        )
                    } else {
                        DetailAction(
                            label = "扔掉",
                            onClick = {
                                scope.launch {
                                    repository.setClothStatus(clothId, true)
                                    onBack()
                                }
                            },
                            contentColor = WardrobeConstants.DiscardBrown,
                            borderColor = Color(0xFFE6C200)
                        )
                    },
                    DetailAction(
                        label = "删除",
                        onClick = {
                            scope.launch {
                                repository.removeCloth(clothId)
                                onBack()
                            }
                        }
                    )
                )
            )
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = { AppTopBar("衣服详情", onBack = onBack) },
            containerColor = WardrobeConstants.PageBg
        ) { padding -> pageBody(padding) }
    } else {
        pageBody(PaddingValues())
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun RecSubtitle(text: String, topGap: Boolean = false) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF444444),
        modifier = Modifier.padding(top = if (topGap) 8.dp else 0.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFFF0F0F0))
        )
    }
}

@Composable
private fun TagChip(
    bg: Color = Color(0xFFF5F5F5),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

@Composable
private fun WearStatsGrid(c30: Int, c365: Int, cAll: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WearStatCell("近30天", c30, Modifier.weight(1f))
        WearStatCell("近一年", c365, Modifier.weight(1f))
        WearStatCell("累计", cAll, Modifier.weight(1f))
    }
}

@Composable
private fun WearStatCell(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF9F9F9))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = WardrobeConstants.TextSecondary)
        Text(
            "$count 次",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = WardrobeConstants.Accent,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun DetailSizeGrid(rows: List<DetailRow>) {
    val normal = rows.filter { !it.full }
    val fullRows = rows.filter { it.full }
    normal.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pair.forEach { row ->
                DetailSizeCell(row, Modifier.weight(1f))
            }
            if (pair.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
    fullRows.forEach { row ->
        DetailSizeCell(row, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DetailSizeCell(row: DetailRow, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF9F9F9))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(row.label, fontSize = 11.sp, color = WardrobeConstants.TextMuted)
        Text(
            row.value.ifBlank { "-" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF222222),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun InspirationRecCard(
    insp: InspirationEntity,
    context: android.content.Context,
    linked: Boolean,
    onClick: () -> Unit
) {
    val displayName = insp.name.ifBlank { insp.style.ifBlank { "灵感" } }
    Box(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(WardrobeConstants.ImagePlaceholder)
            ) {
                val img = ImageStore.fileForRef(context, insp.imageRef)
                if (img.isFile) {
                    AsyncImage(
                        ImageRequest.Builder(context)
                            .data(img)
                            .size(Size(176, 176))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无图", fontSize = 10.sp, color = WardrobeConstants.TextHint)
                    }
                }
                if (linked) {
                    RecBadge("已关联", Modifier.align(Alignment.TopStart).padding(4.dp))
                }
                if (insp.season.isBlank()) {
                    RecBadge(
                        "未填季节",
                        Modifier.align(Alignment.BottomStart).padding(4.dp),
                        warn = true
                    )
                }
            }
            Text(
                displayName,
                fontSize = 12.sp,
                color = Color(0xFF333333),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CompanionRecCard(
    cloth: ClothEntity,
    context: android.content.Context,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(WardrobeConstants.ImagePlaceholder)
            ) {
                val img = ImageStore.fileForRef(context, cloth.imageRef)
                if (img.isFile) {
                    AsyncImage(
                        ImageRequest.Builder(context)
                            .data(img)
                            .size(Size(176, 176))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Text(
                cloth.name,
                fontSize = 12.sp,
                color = Color(0xFF333333),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
            )
            Text(
                cloth.type,
                fontSize = 10.sp,
                color = WardrobeConstants.TextMuted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun RecBadge(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Text(
        text,
        fontSize = 10.sp,
        color = Color.White,
        modifier = modifier
            .background(
                if (warn) Color.Black.copy(alpha = 0.55f) else WardrobeConstants.Accent.copy(alpha = 0.9f),
                RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
