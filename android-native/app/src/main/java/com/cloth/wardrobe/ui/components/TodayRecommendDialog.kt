package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil.size.Size
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.ui.TodayRecommendResult
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.colorHex

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayRecommendDialog(
    result: TodayRecommendResult?,
    season: String,
    clothesById: Map<String, ClothEntity>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSeasonChange: (String) -> Unit,
    onReroll: () -> Unit,
    onWearToday: () -> Unit,
    onClothClick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WardrobeConstants.CardBg,
        titleContentColor = Color(0xFF222222),
        textContentColor = Color(0xFF666666),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text("今日推荐", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WardrobeConstants.SEASONS.forEach { s ->
                        val selected = season == s
                        Text(
                            s,
                            fontSize = 11.sp,
                            color = if (selected) WardrobeConstants.Accent else WardrobeConstants.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) WardrobeConstants.ChipActiveBg else Color.White
                                )
                                .border(
                                    0.5.dp,
                                    if (selected) WardrobeConstants.ChipActiveBorder else Color(0xFFE8E8E8),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSeasonChange(s) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                when {
                    loading || result == null -> {
                        Text("生成中…", fontSize = 12.sp, color = WardrobeConstants.TextHint)
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "依据：${result.inspirationName.ifBlank { "灵感" }}",
                                fontSize = 11.sp,
                                color = WardrobeConstants.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            result.palette.take(5).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colorHex(c))
                                        .border(
                                            0.5.dp,
                                            Color.Black.copy(alpha = 0.08f),
                                            RoundedCornerShape(4.dp)
                                        )
                                ) {}
                            }
                        }

                        if (!result.hasOutfit) {
                            Text(
                                "当前季节衣柜衣物不足，可换一套或改季节",
                                fontSize = 12.sp,
                                color = WardrobeConstants.TextHint,
                                lineHeight = 16.sp
                            )
                        } else if (result.longId != null) {
                            Text(
                                "上+下凑不齐，推荐一件长款",
                                fontSize = 11.sp,
                                color = WardrobeConstants.TextMuted
                            )
                            val longCloth = clothesById[result.longId]
                            if (longCloth != null) {
                                RecommendClothThumb(
                                    cloth = longCloth,
                                    role = "长款",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onClothClick(longCloth.id) }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val top = clothesById[result.topId]
                                val bottom = clothesById[result.bottomId]
                                if (top != null) {
                                    RecommendClothThumb(
                                        cloth = top,
                                        role = "上衣",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onClothClick(top.id) }
                                    )
                                }
                                if (bottom != null) {
                                    RecommendClothThumb(
                                        cloth = bottom,
                                        role = "下装",
                                        modifier = Modifier.weight(1f),
                                        onClick = { onClothClick(bottom.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onReroll,
                    enabled = !loading
                ) {
                    Text("换一套", color = WardrobeConstants.LinkBlue, fontSize = 13.sp)
                }
                TextButton(
                    onClick = onWearToday,
                    enabled = !loading && result?.hasOutfit == true
                ) {
                    Text("记为今日穿着", color = WardrobeConstants.Accent, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = WardrobeConstants.TextMuted, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun RecommendClothThumb(
    cloth: ClothEntity,
    role: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val img = ImageStore.fileForRef(context, cloth.imageRef)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Text(
            role,
            fontSize = 10.sp,
            color = WardrobeConstants.TextMuted,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(WardrobeConstants.ImagePlaceholder)
        ) {
            if (img.isFile) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(img)
                        .size(Size(192, 192))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            cloth.name,
            fontSize = 11.sp,
            color = WardrobeConstants.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
