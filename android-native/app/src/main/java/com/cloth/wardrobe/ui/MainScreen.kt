package com.cloth.wardrobe.ui

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.data.WardrobeUiPrefs
import com.cloth.wardrobe.export.ExportResult
import com.cloth.wardrobe.ui.components.AppAlertDialog
import com.cloth.wardrobe.ui.components.ClothGridCard
import com.cloth.wardrobe.ui.components.FilterChipDropdown
import com.cloth.wardrobe.ui.components.MainTab
import com.cloth.wardrobe.ui.components.WardrobeEmptyState
import com.cloth.wardrobe.ui.components.WardrobeFab
import com.cloth.wardrobe.ui.components.WardrobePageHeader
import com.cloth.wardrobe.ui.components.SortToggleChip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: WardrobeRepository,
    refreshKey: Int = 0,
    onImportZip: () -> Unit = {},
    onNavigateMatch: () -> Unit = {},
    onNavigateInspiration: () -> Unit = {},
    onClothClick: (String) -> Unit = {},
    onAddCloth: () -> Unit = {},
    onToday: () -> Unit = {},
    onWearStats: () -> Unit = {},
    onDiscarded: () -> Unit = {},
    onBatchEdit: (Set<String>) -> Unit = {}
) {
    val context = LocalContext.current
    val uiPrefs = remember { WardrobeUiPrefs(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var clothes by remember { mutableStateOf<List<ClothEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    var filterSeason by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }
    var filterColor by remember { mutableStateOf("") }
    var filterTemp by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf("created") }
    var sortAsc by remember { mutableStateOf(false) }
    var prefsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = uiPrefs.load()
        filterSeason = saved.season
        filterType = saved.type
        filterColor = saved.color
        filterTemp = saved.temp
        sortField = saved.sortField
        sortAsc = saved.sortAsc
        prefsLoaded = true
    }

    LaunchedEffect(filterSeason, filterType, filterColor, filterTemp, sortField, sortAsc, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        uiPrefs.save(
            WardrobeUiPrefs.WardrobeFilterState(
                season = filterSeason,
                type = filterType,
                color = filterColor,
                temp = filterTemp,
                sortField = sortField,
                sortAsc = sortAsc
            )
        )
    }
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var exportSuccess by remember { mutableStateOf<ExportResult?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                repository.seedDemoIfEmpty()
                clothes = repository.listClothes()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(refreshKey) { refresh() }

    val displayList = remember(clothes, filterSeason, filterType, filterColor, filterTemp, sortField, sortAsc) {
        sortClothes(
            filterClothes(clothes, filterSeason, filterType, filterColor, filterTemp),
            sortField,
            sortAsc
        )
    }

    val listLayoutKey = remember(
        sortField,
        sortAsc,
        filterSeason,
        filterType,
        filterColor,
        filterTemp
    ) {
        listOf(sortField, sortAsc, filterSeason, filterType, filterColor, filterTemp).joinToString("|")
    }

    fun resetFilter() {
        filterSeason = ""
        filterType = ""
        filterColor = ""
        filterTemp = ""
        sortField = "created"
        sortAsc = false
        uiPrefs.clear()
    }

    fun stub(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun confirmBatchDelete() {
        if (selectedIds.isEmpty()) return
        scope.launch {
            repository.batchRemoveClothes(selectedIds)
            selectedIds = emptySet()
            batchMode = false
            clothes = repository.listClothes()
            snackbar.showSnackbar("已删除")
        }
    }

    Scaffold(
        containerColor = WardrobeConstants.PageBg,
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                WardrobePageHeader(
                    title = "我的衣柜",
                    currentTab = MainTab.Wardrobe,
                    compact = true,
                    onTabClick = { tab ->
                        when (tab) {
                            MainTab.Wardrobe -> Unit
                            MainTab.Match -> onNavigateMatch()
                            MainTab.Inspiration -> onNavigateInspiration()
                        }
                    }
                )
                if (!loading) {
                    FilterPanel(
                        filterSeason = filterSeason,
                        filterType = filterType,
                        filterColor = filterColor,
                        filterTemp = filterTemp,
                        sortField = sortField,
                        sortAsc = sortAsc,
                        onSeason = { filterSeason = it },
                        onType = { filterType = it },
                        onColor = { filterColor = it },
                        onTemp = { filterTemp = it },
                        onSelectSort = { field ->
                            if (sortField != field) {
                                sortField = field
                                sortAsc = field != "created"
                            }
                        },
                        onToggleSortDir = { field ->
                            if (sortField != field) {
                                sortField = field
                                sortAsc = field != "created"
                            } else {
                                sortAsc = !sortAsc
                            }
                        },
                        onReset = { resetFilter() },
                        matchCount = displayList.size
                    )
                    IoBar(
                        count = displayList.size,
                        batchMode = batchMode,
                        onToday = onToday,
                        onStats = onWearStats,
                        onExport = {
                            scope.launch {
                                busy = true
                                try {
                                    exportSuccess = repository.exportZip()
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("导出失败：${e.message}")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onImport = onImportZip,
                        onBatch = {
                            batchMode = !batchMode
                            selectedIds = emptySet()
                        },
                        onDiscarded = onDiscarded,
                        exportEnabled = !busy
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (batchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选 ${selectedIds.size}", fontSize = 13.sp)
                    androidx.compose.material3.Button(
                        onClick = { onBatchEdit(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("编辑") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { confirmBatchDelete() },
                        enabled = selectedIds.isNotEmpty()
                    ) { Text("删除", color = WardrobeConstants.Accent) }
                }
            }
        },
        floatingActionButton = {
            if (!batchMode) {
                WardrobeFab(onClick = onAddCloth)
            }
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WardrobeConstants.Accent)
            }
            return@Scaffold
        }

        key(listLayoutKey) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = if (batchMode) 120.dp else 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (displayList.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        WardrobeEmptyState(
                            primary = if (clothes.isEmpty()) "还没有衣服" else "没有符合条件的衣服",
                            hint = "点击右下角 + 添加"
                        )
                    }
                } else {
                    items(
                        items = displayList,
                        key = { it.id },
                        contentType = { "cloth" }
                    ) { item ->
                        ClothGridCard(
                            item = item,
                            selected = selectedIds.contains(item.id),
                            selectable = batchMode,
                            onClick = {
                                if (batchMode) {
                                    selectedIds = if (selectedIds.contains(item.id)) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                } else {
                                    BrowseOrderHolder.clothIds = displayList.map { it.id }
                                    onClothClick(item.id)
                                }
                            },
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }

    exportSuccess?.let { result ->
        val sizeKb = (result.sizeBytes / 1024).coerceAtLeast(1)
        AppAlertDialog(
            onDismissRequest = { exportSuccess = null },
            title = "导出成功",
            text = "文件已保存至：\n${result.path}\n\n大小：约 $sizeKb KB\n\n格式：ZIP（推荐，导入更快）",
            confirmText = "知道了",
            onConfirm = { exportSuccess = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    filterSeason: String,
    filterType: String,
    filterColor: String,
    filterTemp: String,
    sortField: String,
    sortAsc: Boolean,
    onSeason: (String) -> Unit,
    onType: (String) -> Unit,
    onColor: (String) -> Unit,
    onTemp: (String) -> Unit,
    onSelectSort: (String) -> Unit,
    onToggleSortDir: (String) -> Unit,
    onReset: () -> Unit,
    matchCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipDropdown(
                label = "季节",
                value = filterSeason,
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.SEASONS,
                onSelect = onSeason,
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "类型",
                value = filterType,
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.TYPES,
                onSelect = onType,
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "颜色",
                value = filterColor,
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.PRESET_COLORS,
                onSelect = onColor,
                modifier = Modifier.weight(1f)
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("created", "length", "color").forEach { field ->
                SortToggleChip(
                    label = WardrobeConstants.SORT_FIELD_LABELS[field] ?: field,
                    ascending = if (sortField == field) sortAsc else field != "created",
                    selected = sortField == field,
                    onSelect = { onSelectSort(field) },
                    onToggleDirection = { onToggleSortDir(field) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("温度", fontSize = 11.sp, color = Color(0xFF666666))
                BasicTextField(
                    value = filterTemp,
                    onValueChange = onTemp,
                    singleLine = true,
                    maxLines = 1,
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = WardrobeConstants.TextPrimary,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(WardrobeConstants.Accent),
                    modifier = Modifier
                        .width(52.dp)
                        .height(26.dp)
                        .background(WardrobeConstants.CardBg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, WardrobeConstants.Divider, RoundedCornerShape(4.dp)),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (filterTemp.isEmpty()) {
                                Text(
                                    "℃",
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = WardrobeConstants.TextHint,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                inner()
                            }
                        }
                    }
                )
                if (filterTemp.isNotEmpty()) {
                    Text(
                        "适合${matchCount}件",
                        fontSize = 10.sp,
                        color = WardrobeConstants.Accent,
                        maxLines = 1
                    )
                }
            }
            Text(
                "重置",
                fontSize = 11.sp,
                color = WardrobeConstants.Accent,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clickable(onClick = onReset)
            )
        }
    }
}

@Composable
private fun IoBar(
    count: Int,
    batchMode: Boolean,
    onToday: () -> Unit,
    onStats: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBatch: () -> Unit,
    onDiscarded: () -> Unit,
    exportEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WardrobeConstants.PageBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IoLink("今日", accent = true, onClick = onToday)
        IoDivider()
        IoLink("统计", accent = true, onClick = onStats)
        IoDivider()
        IoLink("导出", enabled = exportEnabled, onClick = onExport)
        IoDivider()
        IoLink("导入", onClick = onImport)
        IoDivider()
        IoLink(if (batchMode) "取消" else "批量", onClick = onBatch)
        IoDivider()
        IoLink("已扔掉", color = WardrobeConstants.DiscardBrown, onClick = onDiscarded)
        Text(
            "共 $count 件",
            fontSize = 11.sp,
            color = Color(0xFF666666),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun IoLink(
    text: String,
    accent: Boolean = false,
    color: Color = WardrobeConstants.LinkBlue,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = when {
            !enabled -> WardrobeConstants.TextHint
            accent -> WardrobeConstants.Accent
            else -> color
        },
        fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .padding(horizontal = 0.dp)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun IoDivider() {
    Text(" | ", fontSize = 11.sp, color = WardrobeConstants.Divider)
}
