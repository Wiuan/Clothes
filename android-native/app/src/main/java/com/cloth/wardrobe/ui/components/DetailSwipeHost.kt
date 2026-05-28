package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cloth.wardrobe.ui.WardrobeConstants

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailSwipeHost(
    orderedIds: List<String>,
    initialId: String,
    titleBase: String,
    onBack: () -> Unit,
    page: @Composable (id: String, padding: PaddingValues) -> Unit
) {
    val initialPage = orderedIds.indexOf(initialId).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { orderedIds.size }
    )
    val currentIndex = pagerState.currentPage
    val title = if (orderedIds.size > 1) {
        "$titleBase (${currentIndex + 1}/${orderedIds.size})"
    } else {
        titleBase
    }

    Scaffold(
        topBar = { AppTopBar(title, onBack = onBack) },
        containerColor = WardrobeConstants.PageBg
    ) { scaffoldPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) { pageIndex ->
            page(orderedIds[pageIndex], PaddingValues())
        }
    }
}
