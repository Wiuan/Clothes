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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.colorHex
import com.cloth.wardrobe.ui.colorLabel
import com.cloth.wardrobe.ui.parseItemColors

@Composable
fun ClothGridCard(
    item: ClothEntity,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageFile = ImageStore.fileForRef(context, item.imageRef)
    val shape = RoundedCornerShape(6.dp)
    val borderColor = if (selectable && selected) WardrobeConstants.Accent else Color.Transparent

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(shape)
            .border(
                width = if (selectable && selected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick),
        color = WardrobeConstants.CardBg,
        shape = shape
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(WardrobeConstants.ImagePlaceholder)
            ) {
                if (imageFile.isFile) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无图", fontSize = 10.sp, color = WardrobeConstants.TextHint)
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)) {
                Text(
                    text = item.name.ifBlank { "未命名" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF222222),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        parseItemColors(item.colorsJson).take(3).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colorHex(c))
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
                            )
                        }
                        Text(
                            text = colorLabel(item),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = WardrobeConstants.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = item.season,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = WardrobeConstants.TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .wrapContentWidth(unbounded = true)
                            .background(
                                WardrobeConstants.ImagePlaceholder,
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
