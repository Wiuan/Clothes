package com.cloth.wardrobe.ui.screens

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.MatchEntity
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.data.WearLogEntity
import com.cloth.wardrobe.data.WearLogUtils
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.WearStats
import com.cloth.wardrobe.ui.components.AppAlertDialog
import com.cloth.wardrobe.ui.components.AppTopBar
import com.cloth.wardrobe.ui.components.ClothGridCard
import com.cloth.wardrobe.ui.components.ClothPickFilterBar
import com.cloth.wardrobe.ui.components.ClothPickGrid
import com.cloth.wardrobe.ui.components.CompactInput
import com.cloth.wardrobe.ui.components.FilterChipDropdown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.cloth.wardrobe.ui.filterClothes
import com.cloth.wardrobe.ui.filterDiscardedClothes
import kotlinx.coroutines.launch

@Composable
fun CheckinScreen(
    repository: WardrobeRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val todayStr = WearLogUtils.todayDateStr()
    var mode by remember { mutableStateOf("single") }
    var allClothes by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var matches by remember { mutableStateOf(emptyList<MatchEntity>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selectedMatch by remember { mutableStateOf<String?>(null) }
    var todayLogs by remember { mutableStateOf(emptyList<WearLogEntity>()) }
    var filterSeason by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }
    var pendingRemoveLogId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            allClothes = repository.listClothes().filter { it.status != "discarded" }
            matches = repository.listMatches().filter {
                JsonHelpers.jsonToStringList(it.clothIdsJson).isNotEmpty()
            }
            todayLogs = WearLogUtils.logsForDate(repository.listWearLogs(), todayStr)
        }
    }
    LaunchedEffect(Unit) { reload() }

    val filteredClothes = remember(allClothes, filterSeason, filterType) {
        filterClothes(allClothes, filterSeason, filterType, "")
    }
    val clothMap = remember(allClothes) { allClothes.associateBy { it.id } }
    val canSubmit = if (mode == "single") selected.isNotEmpty() else selectedMatch != null

    fun logMeta(log: WearLogEntity): String {
        val typeLabel = if (log.type == "match") "搭配" else "单件"
        val count = JsonHelpers.jsonToStringList(log.clothIdsJson).size
        val matchName = if (log.type == "match" && !log.matchId.isNullOrBlank()) {
            matches.find { it.id == log.matchId }?.name.orEmpty()
        } else ""
        return if (matchName.isNotBlank()) "$typeLabel · $count 件 · $matchName"
        else "$typeLabel · $count 件"
    }

    Scaffold(
        topBar = { AppTopBar("今日穿着", onBack = onBack) },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(10.dp, 10.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("今日", fontSize = 12.sp, color = WardrobeConstants.TextSecondary)
                    Text(todayStr, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF222222))
                }
            }
            item { CheckinModeTabs(mode) { mode = it } }
            if (todayLogs.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "今日已记录（${todayLogs.size}）",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        todayLogs.forEachIndexed { index, log ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CheckinLogThumbs(
                                    clothIds = JsonHelpers.jsonToStringList(log.clothIdsJson),
                                    clothMap = clothMap
                                )
                                Text(
                                    logMeta(log),
                                    fontSize = 11.sp,
                                    color = Color(0xFF555555),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "撤销",
                                    fontSize = 11.sp,
                                    color = WardrobeConstants.Accent,
                                    modifier = Modifier.clickable { pendingRemoveLogId = log.id }
                                )
                            }
                        }
                    }
                }
            }
            if (mode == "single") {
                item {
                    ClothPickFilterBar(
                        filterSeason = filterSeason,
                        filterType = filterType,
                        filterColor = "",
                        filteredCount = filteredClothes.size,
                        totalCount = allClothes.size,
                        onSeason = { filterSeason = if (it == WardrobeConstants.ALL) "" else it },
                        onType = { filterType = if (it == WardrobeConstants.ALL) "" else it },
                        onColor = {},
                        onReset = {
                            filterSeason = ""
                            filterType = ""
                        },
                        showColorFilter = false,
                        selectedCount = selected.size,
                        compactInline = true
                    )
                }
                item {
                    CheckinSubmitButton(
                        enabled = canSubmit,
                        onClick = {
                            scope.launch {
                                repository.addWearLog(selected.toList(), "single", null, todayStr)
                                selected = emptySet()
                                reload()
                            }
                        }
                    )
                }
                item {
                    ClothPickGrid(
                        clothes = filteredClothes,
                        selectedIds = selected,
                        onToggle = { id ->
                            selected = if (selected.contains(id)) selected - id else selected + id
                        }
                    )
                }
            } else {
                item {
                    Text(
                        "选一套搭配，其中每件衣服各计 1 次",
                        fontSize = 11.sp,
                        color = WardrobeConstants.TextMuted
                    )
                }
                item {
                    CheckinSubmitButton(
                        enabled = canSubmit,
                        onClick = {
                            scope.launch {
                                val m = matches.find { it.id == selectedMatch } ?: return@launch
                                repository.addWearLog(
                                    JsonHelpers.jsonToStringList(m.clothIdsJson),
                                    "match",
                                    m.id,
                                    todayStr
                                )
                                selectedMatch = null
                                reload()
                            }
                        }
                    )
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            "还没有搭配，请先在「搭配」页创建",
                            fontSize = 12.sp,
                            color = WardrobeConstants.TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    items(matches, key = { it.id }) { m ->
                        CheckinMatchRow(
                            match = m,
                            clothes = allClothes,
                            selected = selectedMatch == m.id,
                            onClick = { selectedMatch = m.id }
                        )
                    }
                }
            }
        }
    }

    pendingRemoveLogId?.let { logId ->
        AppAlertDialog(
            onDismissRequest = { pendingRemoveLogId = null },
            title = "撤销记录",
            text = "删除这条今日穿着记录？对应次数会减回。",
            confirmText = "撤销",
            onConfirm = {
                pendingRemoveLogId = null
                scope.launch {
                    repository.removeWearLog(logId)
                    reload()
                }
            }
        )
    }
}

@Composable
fun WearStatsScreen(
    repository: WardrobeRepository,
    onBack: () -> Unit,
    onClothClick: (String) -> Unit
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("365d") }
    var filterSeason by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var clothes by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var logs by remember { mutableStateOf(emptyList<WearLogEntity>()) }

    LaunchedEffect(Unit) {
        clothes = repository.listClothes()
        logs = repository.listWearLogs()
    }

    val (startMs, endMs, periodLabel) = WearLogUtils.resolvePeriod(period)
    val rankList = remember(clothes, logs, startMs, endMs, filterSeason, sortAsc) {
        WearStats.buildRankList(clothes, logs, startMs, endMs, filterSeason, sortAsc)
    }
    val unwornCount = rankList.count { it.count == 0 }
    val sortTip = if (sortAsc) {
        "按穿着次数从少到多，方便找出「压箱底」的衣服"
    } else {
        "按穿着次数从多到少，查看最常穿衣物"
    }

    Scaffold(
        topBar = { AppTopBar("穿着统计", onBack = onBack) },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        WearStats.PERIOD_OPTS.forEach { (key, label) ->
                            val active = period == key
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (active) WardrobeConstants.Accent else Color(0xFF666666),
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (active) WardrobeConstants.ChipActiveBg else WardrobeConstants.ChipBg
                                    )
                                    .border(
                                        1.dp,
                                        if (active) WardrobeConstants.ChipActiveBorder else Color.Transparent,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { period = key }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "衣物季节",
                            fontSize = 10.sp,
                            color = WardrobeConstants.TextSecondary
                        )
                        FilterChipDropdown(
                            label = null,
                            value = filterSeason.ifEmpty { WardrobeConstants.ALL },
                            options = listOf(WardrobeConstants.ALL) + WardrobeConstants.SEASONS,
                            onSelect = { filterSeason = if (it == WardrobeConstants.ALL) "" else it },
                            modifier = Modifier.wrapContentWidth()
                        )
                        Text(
                            "$periodLabel·${rankList.size}件·未穿$unwornCount",
                            fontSize = 10.sp,
                            color = WardrobeConstants.TextMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(true to "少→多", false to "多→少").forEach { (asc, label) ->
                            val active = sortAsc == asc
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (active) WardrobeConstants.Accent else Color(0xFF666666),
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (active) WardrobeConstants.ChipActiveBg else WardrobeConstants.ChipBg
                                    )
                                    .border(
                                        1.dp,
                                        if (active) WardrobeConstants.ChipActiveBorder else Color.Transparent,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { sortAsc = asc }
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        sortTip,
                        fontSize = 10.sp,
                        color = WardrobeConstants.TextSecondary,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (rankList.isEmpty()) {
                item {
                    Text(
                        "没有符合条件的衣服",
                        fontSize = 12.sp,
                        color = WardrobeConstants.TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(
                    rankList,
                    key = { it.cloth.id },
                    contentType = { "rank" }
                ) { row ->
                    WearStatsRankRow(
                        row = row,
                        onClick = { onClothClick(row.cloth.id) },
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckinSubmitButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = WardrobeConstants.Accent,
            disabledContainerColor = WardrobeConstants.Accent.copy(alpha = 0.45f)
        )
    ) {
        Text("记录今日穿着", fontSize = 13.sp)
    }
}

@Composable
private fun CheckinModeTabs(mode: String, onMode: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(3.dp)
    ) {
        listOf("single" to "单件", "match" to "搭配").forEach { (key, label) ->
            val active = mode == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) WardrobeConstants.Accent else Color.Transparent)
                    .clickable { onMode(key) }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (active) Color.White else Color(0xFF666666),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun CheckinLogThumbs(
    clothIds: List<String>,
    clothMap: Map<String, ClothEntity>
) {
    val context = LocalContext.current
    val ids = clothIds.take(4)
    if (ids.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ids.forEach { id ->
            val cloth = clothMap[id]
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WardrobeConstants.ImagePlaceholder)
            ) {
                if (cloth != null) {
                    val img = ImageStore.fileForRef(context, cloth.imageRef)
                    if (img.isFile) {
                        AsyncImage(
                            ImageRequest.Builder(context).data(img).size(72).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        if (clothIds.size > 4) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+${clothIds.size - 4}",
                    fontSize = 10.sp,
                    color = WardrobeConstants.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CheckinMatchRow(
    match: MatchEntity,
    clothes: List<ClothEntity>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val clothMap = remember(clothes) { clothes.associateBy { it.id } }
    val ids = JsonHelpers.jsonToStringList(match.clothIdsJson).take(3)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) WardrobeConstants.Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ids.forEach { id ->
                val cloth = clothMap[id]
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(WardrobeConstants.ImagePlaceholder)
                ) {
                    if (cloth != null) {
                        val img = ImageStore.fileForRef(context, cloth.imageRef)
                        if (img.isFile) {
                            AsyncImage(
                                ImageRequest.Builder(context).data(img).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(match.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF222222))
            Text(
                "${JsonHelpers.jsonToStringList(match.clothIdsJson).size} 件",
                fontSize = 10.sp,
                color = WardrobeConstants.TextMuted
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(WardrobeConstants.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WearStatsRankRow(
    row: com.cloth.wardrobe.ui.WearRankRow,
    onClick: () -> Unit,
    context: android.content.Context
) {
    val unworn = row.count == 0
    val img = ImageStore.fileForRef(context, row.cloth.imageRef)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (unworn) Color(0xFFFFFAFA) else Color.White)
            .border(
                width = if (unworn) 0.5.dp else 0.dp,
                color = if (unworn) Color(0xFFFFE0E6) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(WardrobeConstants.ImagePlaceholder)
        ) {
            if (img.isFile) {
                AsyncImage(
                    ImageRequest.Builder(context).data(img).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("无图", fontSize = 10.sp, color = WardrobeConstants.TextHint, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(
                row.cloth.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Text(
                "${row.cloth.season} · ${row.cloth.type}",
                fontSize = 11.sp,
                color = WardrobeConstants.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
            Text(
                "上次：${WearLogUtils.formatWearDate(row.lastDate)}",
                fontSize = 10.sp,
                color = if (unworn) WardrobeConstants.Accent else Color(0xFFBBBBBB),
                lineHeight = 12.sp
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 6.dp)
        ) {
            Text(
                "${row.count}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (unworn || row.count > 0) WardrobeConstants.Accent else Color(0xFFCCCCCC),
                lineHeight = 16.sp
            )
            Text("次", fontSize = 10.sp, color = WardrobeConstants.TextMuted, lineHeight = 10.sp)
        }
    }
}

@Composable
fun DiscardedScreen(
    repository: WardrobeRepository,
    onBack: () -> Unit,
    onClothClick: (String) -> Unit
) {
    var list by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var filterSeason by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        list = repository.listDiscarded()
    }

    val displayList = remember(list, filterSeason, filterType) {
        filterDiscardedClothes(list, filterSeason, filterType)
    }
    val hasFilter = filterSeason.isNotEmpty() || filterType.isNotEmpty()

    Scaffold(
        topBar = { AppTopBar("已扔掉", onBack = onBack) },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "已扔掉的衣物不在主衣柜显示，可在此查看尺寸等信息",
                fontSize = 11.sp,
                color = WardrobeConstants.DiscardBrown,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF8E6))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipDropdown(
                    label = null,
                    value = filterSeason.ifEmpty { WardrobeConstants.ALL },
                    options = listOf(WardrobeConstants.ALL) + WardrobeConstants.SEASONS,
                    onSelect = { filterSeason = if (it == WardrobeConstants.ALL) "" else it },
                    modifier = Modifier.wrapContentWidth()
                )
                FilterChipDropdown(
                    label = null,
                    value = filterType.ifEmpty { WardrobeConstants.ALL },
                    options = listOf(WardrobeConstants.ALL) + WardrobeConstants.TYPES,
                    onSelect = { filterType = if (it == WardrobeConstants.ALL) "" else it },
                    modifier = Modifier.wrapContentWidth()
                )
                Spacer(Modifier.weight(1f))
                if (hasFilter) {
                    Text(
                        "重置",
                        fontSize = 11.sp,
                        color = WardrobeConstants.Accent,
                        modifier = Modifier.clickable {
                            filterSeason = ""
                            filterType = ""
                        }
                    )
                }
            }
            Text(
                "共 ${displayList.size} 件已扔掉",
                fontSize = 10.sp,
                color = WardrobeConstants.TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无已扔掉的衣物",
                        fontSize = 12.sp,
                        color = WardrobeConstants.TextMuted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayList, key = { it.id }) { item ->
                        ClothGridCard(item, false, false, onClick = { onClothClick(item.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchEditScreen(
    repository: WardrobeRepository,
    ids: Set<String>,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var applySeason by remember { mutableStateOf(false) }
    var applyType by remember { mutableStateOf(false) }
    var applyColor by remember { mutableStateOf(false) }
    var applyTemp by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf("夏") }
    var type by remember { mutableStateOf("上衣") }
    var colorForm by remember { mutableStateOf(com.cloth.wardrobe.ui.ColorFormState(selectedColors = setOf("白"))) }
    var tempMin by remember { mutableStateOf("") }
    var tempMax by remember { mutableStateOf("") }

    Scaffold(
        topBar = { AppTopBar("批量编辑", onBack = onDone) },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "已选 ${ids.size} 件",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222)
                )
                Text(
                    "只修改勾选字段，未勾选不变",
                    fontSize = 11.sp,
                    color = WardrobeConstants.TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                BatchFieldBlock("季节", applySeason, { applySeason = it }) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.SEASONS.forEach { s ->
                            BatchChip(s, season == s) { season = s }
                        }
                    }
                }
                BatchFieldBlock("类型", applyType, { applyType = it }) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.TYPES.forEach { t ->
                            BatchChip(t, type == t) { type = t }
                        }
                    }
                }
                BatchFieldBlock("颜色", applyColor, { applyColor = it }, showDivider = false) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WardrobeConstants.PRESET_COLORS.forEach { c ->
                            val on = if (c == "其他") colorForm.otherSelected
                            else colorForm.selectedColors.contains(c)
                            BatchColorChip(c, on) {
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
                        CompactInput(
                            colorForm.colorCustom,
                            { colorForm = colorForm.copy(colorCustom = it) },
                            placeholder = "自定义颜色名",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                    Text(
                        "可多选，将覆盖原颜色",
                        fontSize = 11.sp,
                        color = WardrobeConstants.TextHint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                BatchFieldBlock("适宜温度", applyTemp, { applyTemp = it }, showDivider = false) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactInput(
                            tempMin,
                            { tempMin = it },
                            placeholder = "最低℃",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Text("—", fontSize = 12.sp, color = Color(0xFF999999))
                        CompactInput(
                            tempMax,
                            { tempMax = it },
                            placeholder = "最高℃",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "留空表示清除该温度值",
                        fontSize = 11.sp,
                        color = WardrobeConstants.TextHint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (!applySeason && !applyType && !applyColor && !applyTemp) return@Button
                    scope.launch {
                        val colorPayload = if (applyColor) com.cloth.wardrobe.ui.buildColorsPayload(colorForm) else null
                        if (applyColor && colorPayload == null) return@launch
                        var tMin = tempMin.trim().toIntOrNull()
                        var tMax = tempMax.trim().toIntOrNull()
                        if (tMin != null && tMax != null && tMin > tMax) {
                            val s = tMin; tMin = tMax; tMax = s
                        }
                        repository.batchUpdateClothes(ids) { item ->
                            var next = item
                            if (applySeason) next = next.copy(season = season)
                            if (applyType) next = next.copy(type = type)
                            if (applyColor && colorPayload != null) {
                                next = next.copy(
                                    colorsJson = colorPayload.colorsJson,
                                    colorHexMapJson = colorPayload.colorHexMapJson
                                )
                            }
                            if (applyTemp) {
                                next = next.copy(
                                    tempMin = if (tempMin.isBlank()) null else tMin,
                                    tempMax = if (tempMax.isBlank()) null else tMax
                                )
                            }
                            next
                        }
                        onDone()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = WardrobeConstants.Accent)
            ) {
                Text("应用修改", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BatchFieldBlock(
    label: String,
    apply: Boolean,
    onApply: (Boolean) -> Unit,
    showDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        BatchToggleRow(label, apply, onApply)
        if (apply) {
            Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                content()
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
        if (showDivider) {
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
        }
    }
}

@Composable
private fun BatchToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun BatchChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFFFFF5F6) else Color.White)
            .border(
                1.dp,
                if (active) Color(0xFFFFCDD2) else Color(0xFFEEEEEE),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (active) WardrobeConstants.Accent else Color(0xFF555555)
    )
}

@Composable
private fun BatchColorChip(name: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFFFFF5F6) else Color.White)
            .border(
                1.dp,
                if (active) Color(0xFFFFCDD2) else Color(0xFFEEEEEE),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(WardrobeConstants.COLOR_HEX[name] ?: Color.Gray)
                .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(50))
        )
        Text(
            name,
            fontSize = 12.sp,
            color = if (active) WardrobeConstants.Accent else Color(0xFF555555)
        )
    }
}
