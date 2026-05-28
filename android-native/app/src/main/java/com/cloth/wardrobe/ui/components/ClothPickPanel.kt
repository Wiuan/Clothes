package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.ui.WardrobeConstants

@Composable
fun ClothPickFilterBar(
    filterSeason: String,
    filterType: String,
    filterColor: String,
    filteredCount: Int,
    totalCount: Int,
    onSeason: (String) -> Unit,
    onType: (String) -> Unit,
    onColor: (String) -> Unit,
    onReset: () -> Unit,
    showColorFilter: Boolean = true,
    selectedCount: Int? = null,
    compactInline: Boolean = false
) {
    val hasFilter = filterSeason.isNotEmpty() || filterType.isNotEmpty() ||
        (showColorFilter && filterColor.isNotEmpty())
    val statsText = if (selectedCount != null) {
        "已选$selectedCount·$filteredCount/$totalCount"
    } else {
        "$filteredCount/$totalCount"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compactInline) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compactInline) {
            FilterChipDropdown(
                label = null,
                value = filterSeason.ifEmpty { WardrobeConstants.ALL },
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.SEASONS,
                onSelect = onSeason,
                modifier = Modifier.wrapContentWidth()
            )
            FilterChipDropdown(
                label = null,
                value = filterType.ifEmpty { WardrobeConstants.ALL },
                options = listOf(WardrobeConstants.ALL) + WardrobeConstants.TYPES,
                onSelect = onType,
                modifier = Modifier.wrapContentWidth()
            )
            if (showColorFilter) {
                FilterChipDropdown(
                    label = null,
                    value = filterColor.ifEmpty { WardrobeConstants.ALL },
                    options = listOf(WardrobeConstants.ALL) + WardrobeConstants.PRESET_COLORS,
                    onSelect = onColor,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            if (hasFilter) {
                Text(
                    "重置",
                    color = WardrobeConstants.Accent,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable(onClick = onReset)
                )
            }
            Text(
                statsText,
                fontSize = 10.sp,
                color = WardrobeConstants.TextMuted,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(start = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        } else {
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
            if (showColorFilter) {
                FilterChipDropdown(
                    label = "颜色",
                    value = filterColor,
                    options = listOf(WardrobeConstants.ALL) + WardrobeConstants.PRESET_COLORS,
                    onSelect = onColor,
                    modifier = Modifier.weight(1f)
                )
            }
            if (hasFilter) {
                Text(
                    "重置",
                    color = WardrobeConstants.Accent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable(onClick = onReset)
                )
            }
        }
    }
    if (!compactInline) {
        Text(
            if (selectedCount != null) {
                "已选 $selectedCount 件 · 显示 $filteredCount / $totalCount"
            } else {
                "显示 $filteredCount / $totalCount 件"
            },
            fontSize = 11.sp,
            color = WardrobeConstants.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun ClothPickGrid(
    clothes: List<ClothEntity>,
    selectedIds: Set<String>,
    linkMap: Map<String, String>? = null,
    onToggle: (String) -> Unit,
    onToggleRelation: ((String) -> Unit)? = null
) {
    val rows = clothes.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cloth ->
                    ClothPickCell(
                        cloth = cloth,
                        selected = selectedIds.contains(cloth.id),
                        relation = linkMap?.get(cloth.id),
                        onClick = { onToggle(cloth.id) },
                        onRelationClick = if (linkMap != null && linkMap.containsKey(cloth.id)) {
                            { onToggleRelation?.invoke(cloth.id) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ClothPickCell(
    cloth: ClothEntity,
    selected: Boolean,
    relation: String?,
    onClick: () -> Unit,
    onRelationClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor = if (selected) WardrobeConstants.Accent else Color.Transparent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(WardrobeConstants.ImagePlaceholder)
        ) {
            val img = ImageStore.fileForRef(context, cloth.imageRef)
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
                    fontSize = 11.sp,
                    color = WardrobeConstants.TextHint,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (selected && relation == null) {
                Text(
                    "✓",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(WardrobeConstants.Accent, RoundedCornerShape(50))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (relation != null) {
                val label = WardrobeConstants.LINK_RELATION_LABEL[relation] ?: ""
                Text(
                    label,
                    fontSize = 10.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xE6FF2442))
                        .clickable { onRelationClick?.invoke() }
                        .padding(vertical = 2.dp)
                )
            }
        }
        Text(
            cloth.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}
