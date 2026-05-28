package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

enum class MainTab { Wardrobe, Match, Inspiration }

@Composable
fun MainTabsRow(
    current: MainTab,
    onTabClick: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 12.dp)
        ) {
            TabItem("衣柜", current == MainTab.Wardrobe, compact) { onTabClick(MainTab.Wardrobe) }
            TabItem("搭配", current == MainTab.Match, compact) { onTabClick(MainTab.Match) }
            TabItem("灵感", current == MainTab.Inspiration, compact) { onTabClick(MainTab.Inspiration) }
        }
        HorizontalDivider(color = WardrobeConstants.Divider, thickness = 0.5.dp)
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    active: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val tabPadV = if (compact) 5.dp else 8.dp
    val tabFont = if (compact) 14.sp else 15.sp
    val indicatorTop = if (compact) 4.dp else 8.dp
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(top = tabPadV, bottom = if (compact) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = tabFont,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) WardrobeConstants.Accent else WardrobeConstants.TextSecondary
            )
            Box(
                modifier = Modifier
                    .padding(top = indicatorTop)
                    .width(if (compact) 20.dp else 24.dp)
                    .height(3.dp)
                    .background(
                        if (active) WardrobeConstants.Accent else Color.Transparent,
                        RoundedCornerShape(1.5.dp)
                    )
            )
        }
    }
}
