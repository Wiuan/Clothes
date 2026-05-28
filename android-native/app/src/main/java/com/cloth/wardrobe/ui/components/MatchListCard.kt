package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.MatchEntity
import com.cloth.wardrobe.ui.WardrobeConstants

@Composable
fun MatchListCard(
    match: MatchEntity,
    thumbClothes: List<ClothEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val clothCount = JsonHelpers.jsonToStringList(match.clothIdsJson).size

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(3) { index ->
                    val cloth = thumbClothes.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (cloth != null) WardrobeConstants.ImagePlaceholder
                                else Color(0xFFF0F0F0)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cloth != null) {
                            val img = ImageStore.fileForRef(context, cloth.imageRef)
                            if (img.isFile) {
                                AsyncImage(
                                    ImageRequest.Builder(context).data(img).crossfade(true).build(),
                                    contentDescription = cloth.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    match.name.ifBlank { "未命名搭配" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$clothCount 件",
                    fontSize = 12.sp,
                    color = WardrobeConstants.TextMuted
                )
            }
        }
    }
}
