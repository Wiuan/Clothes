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
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.ui.WardrobeConstants
import com.cloth.wardrobe.ui.colorHex
import com.cloth.wardrobe.ui.parseColorTags
import com.cloth.wardrobe.ui.parseLinks

@Composable
fun InspirationGridCard(
    item: InspirationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(6.dp)
    val imageFile = ImageStore.fileForRef(context, item.imageRef)
    val displayName = item.name.ifBlank { item.style.ifBlank { "灵感" } }
    val primaryColors = parseColorTags(item.colorTagsJson).primary.take(3)
    val hasWantToBuy = parseLinks(item.linksJson).any { it.relation == "want_to_buy" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                1.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(shape)
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
                        ImageRequest.Builder(context).data(imageFile).crossfade(true).build(),
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无图", fontSize = 10.sp, color = WardrobeConstants.TextHint)
                    }
                }
                if (hasWantToBuy) {
                    Text(
                        "想买",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(WardrobeConstants.Accent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                Text(
                    displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    primaryColors.forEach { c ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colorHex(c))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
                        )
                    }
                    if (item.style.isNotBlank()) {
                        Text(
                            item.style,
                            fontSize = 10.sp,
                            color = WardrobeConstants.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
