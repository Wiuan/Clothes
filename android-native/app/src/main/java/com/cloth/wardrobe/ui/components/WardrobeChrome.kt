package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobePageHeader(
    title: String,
    currentTab: MainTab,
    onTabClick: (MainTab) -> Unit,
    compact: Boolean = false
) {
    Column(modifier = Modifier.background(Color.White)) {
        if (compact) {
            Text(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 0.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFF222222)
            )
            MainTabsRow(current = currentTab, onTabClick = onTabClick, compact = true)
        } else {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = Color(0xFF222222)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF222222)
                )
            )
            MainTabsRow(current = currentTab, onTabClick = onTabClick)
        }
    }
}

@Composable
fun WardrobeFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = WardrobeConstants.Accent,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier
            .size(44.dp)
            .shadow(
                6.dp,
                CircleShape,
                ambientColor = WardrobeConstants.FabShadow,
                spotColor = WardrobeConstants.FabShadow
            ),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
fun WardrobeEmptyState(
    primary: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(primary, fontSize = 15.sp, color = WardrobeConstants.TextMuted, textAlign = TextAlign.Center)
        Text(
            hint,
            fontSize = 13.sp,
            color = WardrobeConstants.TextHint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
