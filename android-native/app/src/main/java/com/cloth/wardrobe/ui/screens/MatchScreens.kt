package com.cloth.wardrobe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.MatchEntity
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.ui.ClothFields
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.components.AppTopBar
import com.cloth.wardrobe.ui.components.DetailAction
import com.cloth.wardrobe.ui.components.DetailActionBar
import com.cloth.wardrobe.ui.components.DetailActionStyle
import com.cloth.wardrobe.ui.components.AppAlertDialog
import com.cloth.wardrobe.ui.components.ClothPickFilterBar
import com.cloth.wardrobe.ui.components.ClothPickGrid
import com.cloth.wardrobe.ui.components.CompactInput
import com.cloth.wardrobe.ui.components.MainTab
import com.cloth.wardrobe.ui.components.MatchListCard
import com.cloth.wardrobe.ui.components.WardrobeEmptyState
import com.cloth.wardrobe.ui.components.WardrobeFab
import com.cloth.wardrobe.ui.components.WardrobePageHeader
import com.cloth.wardrobe.ui.filterClothes
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MatchListScreen(
    repository: WardrobeRepository,
    refreshKey: Int,
    onNavigateWardrobe: () -> Unit,
    onNavigateInspiration: () -> Unit,
    onMatchClick: (String) -> Unit,
    onCreateMatch: () -> Unit
) {
    var matches by remember { mutableStateOf<List<MatchEntity>>(emptyList()) }
    LaunchedEffect(refreshKey) { matches = repository.listMatches() }

    Scaffold(
        containerColor = WardrobeConstants.PageBg,
        topBar = {
            WardrobePageHeader(
                title = "我的搭配",
                currentTab = MainTab.Match,
                compact = true,
                onTabClick = {
                    when (it) {
                        MainTab.Wardrobe -> onNavigateWardrobe()
                        MainTab.Match -> Unit
                        MainTab.Inspiration -> onNavigateInspiration()
                    }
                }
            )
        },
        floatingActionButton = { WardrobeFab(onClick = onCreateMatch) }
    ) { padding ->
        if (matches.isEmpty()) {
            WardrobeEmptyState(
                primary = "还没有搭配",
                hint = "点击右下角创建第一套",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matches, key = { it.id }) { m ->
                    var thumbs by remember(m.id) { mutableStateOf<List<ClothEntity>>(emptyList()) }
                    LaunchedEffect(m.id, refreshKey) {
                        thumbs = JsonHelpers.jsonToStringList(m.clothIdsJson)
                            .take(3)
                            .mapNotNull { id -> repository.getCloth(id) }
                    }
                    MatchListCard(
                        match = m,
                        thumbClothes = thumbs,
                        onClick = { onMatchClick(m.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun MatchDetailScreen(
    repository: WardrobeRepository,
    mode: String,
    matchId: String?,
    onDone: () -> Unit,
    onClothClick: (String) -> Unit,
    onEdit: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isCreate = mode == "create"
    val isEdit = isCreate || mode == "edit"
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var allClothes by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var outfitClothes by remember { mutableStateOf(emptyList<ClothEntity>()) }
    var filterSeason by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("") }
    var filterColor by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(matchId, mode) {
        allClothes = repository.listClothes().filter { it.status != "discarded" }
        if (!matchId.isNullOrBlank()) {
            repository.getMatch(matchId)?.let { m ->
                name = m.name
                note = m.note
                val ids = JsonHelpers.jsonToStringList(m.clothIdsJson)
                selected = ids.toSet()
                outfitClothes = ids.mapNotNull { id -> repository.getCloth(id) }
            }
        }
        loaded = true
    }

    val filteredClothes = remember(allClothes, filterSeason, filterType, filterColor) {
        filterClothes(allClothes, filterSeason, filterType, filterColor)
    }

    fun saveMatch() {
        if (name.isBlank()) return
        if (selected.isEmpty()) return
        scope.launch {
            val id = matchId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            repository.saveMatch(
                MatchEntity(
                    id = id,
                    name = name.trim(),
                    clothIdsJson = JsonHelpers.stringListToJson(selected.toList()),
                    note = note.trim()
                )
            )
            onDone()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                when {
                    isCreate -> "新建搭配"
                    isEdit -> "编辑搭配"
                    else -> "搭配详情"
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("搭配名称", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                        CompactInput(name, { name = it }, placeholder = "例如：通勤一套")
                        Text("备注", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                        CompactInput(note, { note = it }, placeholder = "选填")
                    }
                }
                item {
                    Button(
                        onClick = { if (name.isNotBlank() && selected.isNotEmpty()) saveMatch() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = WardrobeConstants.Accent),
                        enabled = name.isNotBlank() && selected.isNotEmpty()
                    ) {
                        Text(if (isCreate) "保存搭配" else "保存修改", fontSize = 15.sp)
                    }
                }
                item {
                    Text(
                        "选择衣服",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                item {
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
                        selectedCount = selected.size,
                        compactInline = true
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
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF222222))
                if (note.isNotBlank()) {
                    Text(
                        note,
                        fontSize = 13.sp,
                        color = WardrobeConstants.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                if (outfitClothes.isEmpty()) {
                    Text(
                        "搭配中的衣服已被删除",
                        fontSize = 13.sp,
                        color = WardrobeConstants.TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    outfitClothes.forEach { cloth ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { onClothClick(cloth.id) }
                        ) {
                            val img = ImageStore.fileForRef(context, cloth.imageRef)
                            Box(
                                Modifier
                                    .size(88.dp)
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
                                        "暂无图",
                                        fontSize = 12.sp,
                                        color = WardrobeConstants.TextHint,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(cloth.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF222222))
                                Text(
                                    "${cloth.type} · ${cloth.season}",
                                    fontSize = 12.sp,
                                    color = WardrobeConstants.TextMuted
                                )
                                Text(
                                    ClothFields.formatTempRange(cloth),
                                    fontSize = 12.sp,
                                    color = WardrobeConstants.Accent
                                )
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
                            onClick = { showDeleteDialog = true }
                        )
                    )
                )
            }
        }
    }

    if (showDeleteDialog && !matchId.isNullOrBlank()) {
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "删除搭配",
            text = "确定删除这套搭配？",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    repository.removeMatch(matchId)
                    onDone()
                }
            }
        )
    }
}
