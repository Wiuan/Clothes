package com.cloth.wardrobe.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.InspirationUiPrefs
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.ui.BrowseOrderHolder
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.allPrimaryColorsFromInspirations
import com.cloth.wardrobe.ui.filterInspirations
import com.cloth.wardrobe.ui.sortInspirations
import com.cloth.wardrobe.ui.components.SortToggleChip
import com.cloth.wardrobe.ui.filterClothes
import com.cloth.wardrobe.ui.parseColorTags
import com.cloth.wardrobe.ui.parseLinks
import com.cloth.wardrobe.ui.components.AppTopBar
import com.cloth.wardrobe.ui.components.DetailAction
import com.cloth.wardrobe.ui.components.DetailActionBar
import com.cloth.wardrobe.ui.components.DetailActionStyle
import com.cloth.wardrobe.ui.components.AppAlertDialog
import com.cloth.wardrobe.ui.components.ClothPickFilterBar
import com.cloth.wardrobe.ui.components.ClothPickGrid
import com.cloth.wardrobe.ui.components.CompactInput
import com.cloth.wardrobe.ui.components.CompactTextArea
import com.cloth.wardrobe.ui.components.EditFormField
import com.cloth.wardrobe.ui.components.PickedImage
import com.cloth.wardrobe.ui.components.rememberImagePicker
import coil.size.Size
import java.io.File
import com.cloth.wardrobe.ui.components.FilterChipDropdown
import com.cloth.wardrobe.ui.components.InspirationGridCard
import com.cloth.wardrobe.ui.components.MainTab
import com.cloth.wardrobe.ui.components.WardrobeEmptyState
import com.cloth.wardrobe.ui.components.WardrobeFab
import com.cloth.wardrobe.ui.components.WardrobePageHeader
import com.cloth.wardrobe.ui.components.DetailSwipeHost
import kotlinx.coroutines.launch
import java.util.UUID

private val INSPIRATION_STYLES = WardrobeConstants.INSPIRATION_STYLES
private val TAG_COLORS = WardrobeConstants.PRESET_COLORS.filter { it != "其他" }

@Composable
fun InspirationListScreen(
    repository: WardrobeRepository,
    refreshKey: Int,
    onNavigateWardrobe: () -> Unit,
    onNavigateMatch: () -> Unit,
    onInspirationClick: (String) -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val uiPrefs = remember { InspirationUiPrefs(context) }
    var list by remember { mutableStateOf<List<InspirationEntity>>(emptyList()) }
    var filterStyle by remember { mutableStateOf("") }
    var filterSeason by remember { mutableStateOf("") }
    var filterColor by remember { mutableStateOf("") }
    var wantToBuyOnly by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf("created") }
    var sortAsc by remember { mutableStateOf(false) }
    var prefsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = uiPrefs.load()
        filterStyle = saved.style
        filterSeason = saved.season
        filterColor = saved.color
        wantToBuyOnly = saved.wantToBuyOnly
        sortField = saved.sortField
        sortAsc = saved.sortAsc
        prefsLoaded = true
    }

    LaunchedEffect(filterStyle, filterSeason, filterColor, wantToBuyOnly, sortField, sortAsc, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        uiPrefs.save(
            InspirationUiPrefs.InspirationFilterState(
                style = filterStyle,
                season = filterSeason,
                color = filterColor,
                wantToBuyOnly = wantToBuyOnly,
                sortField = sortField,
                sortAsc = sortAsc
            )
        )
    }

    LaunchedEffect(refreshKey) { list = repository.listInspirations() }

    val colorOpts = remember(list) {
        listOf(WardrobeConstants.ALL) + allPrimaryColorsFromInspirations(list).ifEmpty {
            WardrobeConstants.PRESET_COLORS
        }
    }
    val display = remember(list, filterStyle, filterSeason, filterColor, wantToBuyOnly, sortField, sortAsc) {
        sortInspirations(
            filterInspirations(list, filterStyle, filterSeason, filterColor, wantToBuyOnly),
            sortField,
            sortAsc
        )
    }

    val listLayoutKey = remember(sortField, sortAsc, filterStyle, filterSeason, filterColor, wantToBuyOnly) {
        listOf(sortField, sortAsc, filterStyle, filterSeason, filterColor, wantToBuyOnly).joinToString("|")
    }

    Scaffold(
        containerColor = WardrobeConstants.PageBg,
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                WardrobePageHeader(
                    title = "灵感库",
                    currentTab = MainTab.Inspiration,
                    compact = true,
                    onTabClick = {
                        when (it) {
                            MainTab.Wardrobe -> onNavigateWardrobe()
                            MainTab.Match -> onNavigateMatch()
                            MainTab.Inspiration -> Unit
                        }
                    }
                )
                InspirationFilterPanel(
                    filterStyle, filterSeason, filterColor, wantToBuyOnly,
                    sortField, sortAsc, display.size,
                    colorOpts,
                    onStyle = { filterStyle = it },
                    onSeason = { filterSeason = it },
                    onColor = { filterColor = it },
                    onWantToBuy = { wantToBuyOnly = !wantToBuyOnly },
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
                    onReset = {
                        filterStyle = ""
                        filterSeason = ""
                        filterColor = ""
                        wantToBuyOnly = false
                        sortField = "created"
                        sortAsc = false
                        uiPrefs.clear()
                    }
                )
            }
        },
        floatingActionButton = { WardrobeFab(onClick = onCreate) }
    ) { padding ->
        if (display.isEmpty()) {
            WardrobeEmptyState(
                primary = if (list.isEmpty()) "还没有灵感" else "没有符合条件的灵感",
                hint = "收藏穿搭参考图，标记颜色与想买单品",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
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
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = display,
                        key = { it.id },
                        contentType = { "inspiration" }
                    ) { item ->
                        InspirationGridCard(
                            item = item,
                            onClick = {
                                BrowseOrderHolder.inspirationIds = display.map { it.id }
                                onInspirationClick(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspirationFilterPanel(
    filterStyle: String,
    filterSeason: String,
    filterColor: String,
    wantToBuyOnly: Boolean,
    sortField: String,
    sortAsc: Boolean,
    count: Int,
    colorOpts: List<String>,
    onStyle: (String) -> Unit,
    onSeason: (String) -> Unit,
    onColor: (String) -> Unit,
    onWantToBuy: () -> Unit,
    onSelectSort: (String) -> Unit,
    onToggleSortDir: (String) -> Unit,
    onReset: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipDropdown(
                label = "风格",
                value = filterStyle,
                options = listOf(WardrobeConstants.ALL) + INSPIRATION_STYLES,
                onSelect = onStyle,
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "季节",
                value = filterSeason,
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.SEASONS,
                onSelect = onSeason,
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "颜色",
                value = filterColor,
                options = colorOpts,
                onSelect = onColor,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("created", "color").forEach { field ->
                SortToggleChip(
                    label = WardrobeConstants.INSPIRATION_SORT_FIELD_LABELS[field] ?: field,
                    ascending = if (sortField == field) sortAsc else field != "created",
                    selected = sortField == field,
                    onSelect = { onSelectSort(field) },
                    onToggleDirection = { onToggleSortDir(field) }
                )
            }
            val toggleShape = RoundedCornerShape(16.dp)
            Text(
                "含「想买」",
                modifier = Modifier
                    .background(
                        if (wantToBuyOnly) WardrobeConstants.ChipActiveBg else WardrobeConstants.CardBg,
                        toggleShape
                    )
                    .then(
                        if (wantToBuyOnly) Modifier.border(1.dp, WardrobeConstants.ChipActiveBorder, toggleShape)
                        else Modifier.border(0.5.dp, WardrobeConstants.Divider, toggleShape)
                    )
                    .clickable(onClick = onWantToBuy)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (wantToBuyOnly) WardrobeConstants.Accent else WardrobeConstants.TextPrimary,
                fontSize = 11.sp
            )
            Text(
                "重置",
                color = WardrobeConstants.Accent,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp).clickable(onClick = onReset)
            )
            Text(
                "共 $count 条",
                fontSize = 10.sp,
                color = Color(0xFF666666),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspirationDetailScreen(
    repository: WardrobeRepository,
    mode: String,
    inspirationId: String?,
    onDone: () -> Unit,
    onClothClick: (String) -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isCreate = mode == "create"
    val isEdit = isCreate || mode == "edit"
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var season by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var occasion by remember { mutableStateOf("") }
    var primaryColors by remember { mutableStateOf(listOf<String>()) }
    var secondaryColors by remember { mutableStateOf(listOf<String>()) }
    var accentColors by remember { mutableStateOf(listOf<String>()) }
    var linkMap by remember { mutableStateOf(mapOf<String, String>()) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var pickedImageFile by remember { mutableStateOf<File?>(null) }
    var imageRef by remember { mutableStateOf("") }
    var createdAt by remember { mutableStateOf(0L) }
    var allClothes by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var linkedClothes by remember { mutableStateOf(emptyList<Pair<ClothEntity, String>>()) }
    var filterSeason by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }
    var filterColor by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val choosePhoto = rememberImagePicker(
        onPicked = { picked: PickedImage? ->
            if (picked != null) {
                pickedImageFile = picked.file
                imageUri = picked.displayUri
            }
        },
        onError = { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    )

    LaunchedEffect(inspirationId, mode) {
        allClothes = repository.listClothes().filter { it.status != "discarded" }
        if (!inspirationId.isNullOrBlank()) {
            repository.getInspiration(inspirationId)?.let { item ->
                name = item.name
                note = item.note
                season = item.season
                style = item.style
                occasion = item.occasion
                imageRef = item.imageRef
                createdAt = item.createdAt
                val tags = parseColorTags(item.colorTagsJson)
                primaryColors = tags.primary
                secondaryColors = tags.secondary
                accentColors = tags.accent
                linkMap = parseLinks(item.linksJson).associate { it.clothId to it.relation }
                linkedClothes = parseLinks(item.linksJson).mapNotNull { link ->
                    repository.getCloth(link.clothId)?.let { it to link.relation }
                }
            }
        }
        loaded = true
    }

    val filteredClothes = remember(allClothes, filterSeason, filterType, filterColor) {
        filterClothes(allClothes, filterSeason, filterType, filterColor)
    }

    val displayTitle = name.ifBlank { style.ifBlank { "灵感" } }
    val tagRows = remember(primaryColors, secondaryColors, accentColors) {
        buildList {
            if (primaryColors.isNotEmpty()) add("主色" to primaryColors)
            if (secondaryColors.isNotEmpty()) add("辅色" to secondaryColors)
            if (accentColors.isNotEmpty()) add("点缀" to accentColors)
        }
    }

    var browseIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(inspirationId, mode) {
        if (!isEdit && !inspirationId.isNullOrBlank()) {
            browseIds = BrowseOrderHolder.inspirationIdsForSwipe(inspirationId!!)
                ?: repository.browseInspirationIds()
        }
    }

    val swipeIds = browseIds
    if (!isEdit && !inspirationId.isNullOrBlank() && swipeIds != null && swipeIds.size > 1) {
        var deleteTarget by remember { mutableStateOf<String?>(null) }
        DetailSwipeHost(swipeIds, inspirationId!!, "灵感详情", onDone) { id, pad ->
            InspirationDetailViewPage(
                repository = repository,
                inspirationId = id,
                padding = pad,
                onClothClick = onClothClick,
                onEdit = onEdit,
                onDelete = { deleteTarget = id }
            )
        }
        deleteTarget?.let { targetId ->
            AppAlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = "删除灵感",
                text = "确定删除这条灵感？",
                confirmText = "删除",
                onConfirm = {
                    deleteTarget = null
                    scope.launch {
                        repository.removeInspiration(targetId)
                        onDone()
                    }
                }
            )
        }
        return
    }

    fun saveInspiration() {
        scope.launch {
            try {
            val id = inspirationId?.takeIf { it.isNotBlank() } ?: "insp_${System.currentTimeMillis()}"
            val ref = imageRef.ifBlank { ImageStore.refForInspirationId(id) }
            when {
                pickedImageFile != null -> repository.saveImageFromFile(ref, pickedImageFile!!)
                imageUri != null -> repository.saveImageFromUri(ref, imageUri!!)
            }
            repository.saveInspiration(
                InspirationEntity(
                    id = id,
                    name = name.trim(),
                    imageRef = ref,
                    note = note.trim(),
                    season = season,
                    style = style,
                    occasion = occasion.trim(),
                    colorTagsJson = JsonHelpers.colorTagsToJson(primaryColors, secondaryColors, accentColors),
                    linksJson = JsonHelpers.linksToJson(linkMap),
                    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis()
                )
            )
            onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    e.message ?: "保存失败",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                when {
                    isCreate -> "添加灵感"
                    isEdit -> "编辑灵感"
                    else -> "灵感详情"
                },
                onBack = onDone
            )
        },
        containerColor = WardrobeConstants.PageBg
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("加载中...", color = WardrobeConstants.TextMuted, fontSize = 14.sp)
            }
            return@Scaffold
        }

        if (isEdit) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
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
                        imageRef.isNotBlank() && ImageStore.fileForRef(context, imageRef).isFile -> AsyncImage(
                            ImageRequest.Builder(context)
                                .data(ImageStore.fileForRef(context, imageRef))
                                .size(Size(480, 480))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 28.sp)
                            Text(
                                if (isCreate) "从相册选择穿搭图" else "点击更换参考图",
                                fontSize = 13.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    EditFormField("标题") {
                        CompactInput(name, { name = it }, placeholder = "选填，如：通勤参考")
                    }
                    EditFormField("主色", inlineHint = "可多选") {
                        InspColorChips(TAG_COLORS, primaryColors) { c ->
                            primaryColors = toggleColorList(primaryColors, c)
                        }
                    }
                    EditFormField("辅色") {
                        InspColorChips(TAG_COLORS, secondaryColors) { c ->
                            secondaryColors = toggleColorList(secondaryColors, c)
                        }
                    }
                    EditFormField("点缀色") {
                        InspColorChips(TAG_COLORS, accentColors) { c ->
                            accentColors = toggleColorList(accentColors, c)
                        }
                    }
                    EditFormField("风格") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            INSPIRATION_STYLES.forEach { s ->
                                InspTextChip(s, style == s) { style = if (style == s) "" else s }
                            }
                        }
                    }
                    EditFormField("季节") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WardrobeConstants.SEASONS.forEach { s ->
                                InspTextChip(s, season == s) { season = if (season == s) "" else s }
                            }
                        }
                    }
                    EditFormField("场合") {
                        CompactInput(occasion, { occasion = it }, placeholder = "选填，如：上班、周末出游")
                    }
                    EditFormField("备注", showDivider = false) {
                        CompactTextArea(note, { note = it })
                    }
                }

                Button(
                    onClick = {
                        if (isCreate && pickedImageFile == null && imageUri == null && imageRef.isBlank()) {
                            return@Button
                        }
                        saveInspiration()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = WardrobeConstants.Accent),
                    enabled = !isCreate || pickedImageFile != null || imageUri != null || imageRef.isNotBlank()
                ) {
                    Text(if (isCreate) "保存灵感" else "保存修改", fontSize = 14.sp)
                }

                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        "关联衣柜（${linkMap.size} 件）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        "选中后点击标签切换「我有类似」/「想买」",
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                    ClothPickFilterBar(
                        filterSeason = filterSeason,
                        filterType = filterType,
                        filterColor = filterColor,
                        filteredCount = filteredClothes.size,
                        totalCount = allClothes.size,
                        onSeason = { filterSeason = if (it == WardrobeConstants.ALL) "" else it },
                        onType = { filterType = if (it == WardrobeConstants.ALL) "" else it },
                        onColor = { filterColor = if (it == WardrobeConstants.ALL) "" else it },
                        onReset = {
                            filterSeason = ""
                            filterType = ""
                            filterColor = ""
                        },
                        selectedCount = linkMap.size,
                        compactInline = true
                    )
                    ClothPickGrid(
                        clothes = filteredClothes,
                        selectedIds = linkMap.keys,
                        linkMap = linkMap,
                        onToggle = { id ->
                            linkMap = if (linkMap.containsKey(id)) linkMap - id
                            else linkMap + (id to "have_similar")
                        },
                        onToggleRelation = { id ->
                            val r = linkMap[id] ?: return@ClothPickGrid
                            linkMap = linkMap + (id to if (r == "want_to_buy") "have_similar" else "want_to_buy")
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else if (!inspirationId.isNullOrBlank()) {
            InspirationDetailViewPage(
                repository = repository,
                inspirationId = inspirationId,
                padding = padding,
                onClothClick = onClothClick,
                onEdit = onEdit,
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog && !inspirationId.isNullOrBlank()) {
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "删除灵感",
            text = "确定删除这条灵感？",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    repository.removeInspiration(inspirationId)
                    onDone()
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspirationDetailViewPage(
    repository: WardrobeRepository,
    inspirationId: String,
    padding: PaddingValues,
    onClothClick: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var loaded by remember(inspirationId) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var season by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var occasion by remember { mutableStateOf("") }
    var imageRef by remember { mutableStateOf("") }
    var primaryColors by remember { mutableStateOf(listOf<String>()) }
    var secondaryColors by remember { mutableStateOf(listOf<String>()) }
    var accentColors by remember { mutableStateOf(listOf<String>()) }
    var linkedClothes by remember { mutableStateOf(emptyList<Pair<ClothEntity, String>>()) }

    LaunchedEffect(inspirationId) {
        loaded = false
        repository.getInspiration(inspirationId)?.let { item ->
            name = item.name
            note = item.note
            season = item.season
            style = item.style
            occasion = item.occasion
            imageRef = item.imageRef
            val tags = parseColorTags(item.colorTagsJson)
            primaryColors = tags.primary
            secondaryColors = tags.secondary
            accentColors = tags.accent
            linkedClothes = parseLinks(item.linksJson).mapNotNull { link ->
                repository.getCloth(link.clothId)?.let { it to link.relation }
            }
        }
        loaded = true
    }

    val displayTitle = name.ifBlank { style.ifBlank { "灵感" } }
    val tagRows = remember(primaryColors, secondaryColors, accentColors) {
        buildList {
            if (primaryColors.isNotEmpty()) add("主色" to primaryColors)
            if (secondaryColors.isNotEmpty()) add("辅色" to secondaryColors)
            if (accentColors.isNotEmpty()) add("点缀" to accentColors)
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("加载中...", color = WardrobeConstants.TextMuted, fontSize = 14.sp)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            when {
                imageRef.isNotBlank() && ImageStore.fileForRef(context, imageRef).isFile -> AsyncImage(
                    ImageRequest.Builder(context).data(ImageStore.fileForRef(context, imageRef)).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                else -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(WardrobeConstants.ImagePlaceholder),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无图", color = WardrobeConstants.TextHint, fontSize = 12.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                displayTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
                lineHeight = 22.sp
            )
            if (tagRows.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp)) {
                    tagRows.forEach { (label, colors) ->
                        InspDetailTagRow(label, colors)
                    }
                }
            }
            if (style.isNotBlank()) {
                Text(
                    "风格：$style",
                    fontSize = 13.sp,
                    color = Color(0xFF555555),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (season.isNotBlank()) {
                Text(
                    "季节：$season",
                    fontSize = 13.sp,
                    color = Color(0xFF555555),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (occasion.isNotBlank()) {
                Text(
                    "场合：$occasion",
                    fontSize = 13.sp,
                    color = Color(0xFF555555),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (note.isNotBlank()) {
                Text(
                    note,
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 6.dp),
                    lineHeight = 18.sp
                )
            }
        }

        if (linkedClothes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "关联单品",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                linkedClothes.forEachIndexed { index, (cloth, relation) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClothClick(cloth.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val img = ImageStore.fileForRef(context, cloth.imageRef)
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(4.dp))
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
                                Text(
                                    "无图",
                                    fontSize = 10.sp,
                                    color = WardrobeConstants.TextHint,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(
                                cloth.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF222222),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                WardrobeConstants.LINK_RELATION_LABEL[relation] ?: "",
                                fontSize = 11.sp,
                                color = if (relation == "want_to_buy") WardrobeConstants.Accent else Color(0xFF43A047),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (index < linkedClothes.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                    }
                }
            }
        }

        DetailActionBar(
            actions = listOf(
                DetailAction(
                    label = "编辑",
                    onClick = onEdit,
                    style = DetailActionStyle.Primary
                ),
                DetailAction(
                    label = "删除",
                    onClick = onDelete,
                    contentColor = Color(0xFFE53935),
                    borderColor = Color(0xFFEEEEEE)
                )
            )
        )
    }
}

private fun toggleColorList(list: List<String>, color: String): List<String> =
    if (list.contains(color)) list - color else list + color

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspColorChips(colors: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { c ->
            val on = selected.contains(c)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color(0xFFFFF0F3) else Color(0xFFF5F5F5))
                    .border(1.dp, if (on) WardrobeConstants.Accent else Color.Transparent, RoundedCornerShape(50))
                    .clickable { onToggle(c) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(WardrobeConstants.COLOR_HEX[c] ?: Color.Gray)
                )
                Text(c, fontSize = 13.sp, color = if (on) WardrobeConstants.Accent else Color(0xFF555555))
            }
        }
    }
}

@Composable
private fun InspTextChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFFFFF0F3) else Color(0xFFF5F5F5))
            .border(1.dp, if (active) WardrobeConstants.Accent else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (active) WardrobeConstants.Accent else Color(0xFF555555)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspDetailTagRow(label: String, colors: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = WardrobeConstants.TextSecondary,
            modifier = Modifier
                .width(32.dp)
                .padding(top = 3.dp, end = 4.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            colors.forEach { InspViewTagChip(it) }
        }
    }
}

@Composable
private fun InspViewTagChip(color: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(WardrobeConstants.COLOR_HEX[color] ?: Color.Gray)
        )
        Text(color, fontSize = 12.sp, color = Color(0xFF555555))
    }
}
