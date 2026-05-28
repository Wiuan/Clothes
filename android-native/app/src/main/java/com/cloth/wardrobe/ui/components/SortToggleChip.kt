package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

@Composable
fun SortToggleChip(
    label: String,
    ascending: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(shape)
            .background(if (selected) WardrobeConstants.ChipActiveBg else WardrobeConstants.CardBg)
            .border(
                1.dp,
                if (selected) WardrobeConstants.ChipActiveBorder else WardrobeConstants.Divider,
                shape
            )
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            color = if (selected) WardrobeConstants.Accent else WardrobeConstants.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onSelect)
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp, minHeight = 32.dp)
                .clickable(onClick = onToggleDirection),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (ascending) "↑" else "↓",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                color = WardrobeConstants.Accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
