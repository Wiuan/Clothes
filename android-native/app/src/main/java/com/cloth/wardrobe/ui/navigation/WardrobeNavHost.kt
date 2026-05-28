package com.cloth.wardrobe.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cloth.wardrobe.data.WardrobeRepository
import com.cloth.wardrobe.ui.MainScreen
import com.cloth.wardrobe.ui.screens.BatchEditScreen
import com.cloth.wardrobe.ui.screens.CheckinScreen
import com.cloth.wardrobe.ui.screens.ClothDetailScreen
import com.cloth.wardrobe.ui.screens.ClothEditScreen
import com.cloth.wardrobe.ui.screens.DiscardedScreen
import com.cloth.wardrobe.ui.screens.InspirationDetailScreen
import com.cloth.wardrobe.ui.screens.InspirationListScreen
import com.cloth.wardrobe.ui.screens.MatchDetailScreen
import com.cloth.wardrobe.ui.screens.MatchListScreen
import com.cloth.wardrobe.ui.screens.WearStatsScreen

@Composable
fun WardrobeNavHost(
    repository: WardrobeRepository,
    onImportZip: () -> Unit,
    importTick: Int = 0
) {
    val nav = rememberNavController()
    var refreshKey by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(importTick) {
        if (importTick > 0) refreshKey++
    }
    fun bump() { refreshKey++ }
    fun pop() { nav.popBackStack() }

    NavHost(navController = nav, startDestination = Routes.WARDROBE) {
        composable(Routes.WARDROBE) {
            MainScreen(
                repository = repository,
                refreshKey = refreshKey,
                onImportZip = {
                    onImportZip()
                    bump()
                },
                onNavigateMatch = {
                    nav.navigate(Routes.MATCH) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateInspiration = {
                    nav.navigate(Routes.INSPIRATION) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onClothClick = { id -> nav.navigate(Routes.clothDetail(id)) },
                onAddCloth = { nav.navigate(Routes.clothEdit(null)) },
                onToday = { nav.navigate(Routes.CHECKIN) },
                onWearStats = { nav.navigate(Routes.WEAR_STATS) },
                onDiscarded = { nav.navigate(Routes.DISCARDED) },
                onBatchEdit = { ids ->
                    nav.navigate(Routes.batchEdit(ids.joinToString(",")))
                }
            )
        }
        composable(Routes.MATCH) {
            MatchListScreen(
                repository = repository,
                refreshKey = refreshKey,
                onNavigateWardrobe = {
                    nav.navigate(Routes.WARDROBE) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateInspiration = {
                    nav.navigate(Routes.INSPIRATION) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onMatchClick = { id ->
                    nav.navigate(Routes.matchDetail("view", id))
                },
                onCreateMatch = { nav.navigate(Routes.matchDetail("create", "")) }
            )
        }
        composable(Routes.INSPIRATION) {
            InspirationListScreen(
                repository = repository,
                refreshKey = refreshKey,
                onNavigateWardrobe = {
                    nav.navigate(Routes.WARDROBE) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateMatch = {
                    nav.navigate(Routes.MATCH) {
                        popUpTo(Routes.WARDROBE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onInspirationClick = { id -> nav.navigate(Routes.inspirationDetail("view", id)) },
                onCreate = { nav.navigate(Routes.inspirationDetail("create", "")) }
            )
        }
        composable(
            Routes.CLOTH_DETAIL,
            arguments = listOf(navArgument("clothId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("clothId") ?: return@composable
            ClothDetailScreen(
                repository = repository,
                clothId = id,
                onBack = { pop(); bump() },
                onEdit = { nav.navigate(Routes.clothEdit(it)) },
                onWearStats = { nav.navigate(Routes.WEAR_STATS) },
                onInspirationClick = { nav.navigate(Routes.inspirationDetail("view", it)) },
                onClothClick = { nav.navigate(Routes.clothDetail(it)) }
            )
        }
        composable(
            Routes.CLOTH_EDIT,
            arguments = listOf(navArgument("clothId") { type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("clothId").orEmpty()
            val id = raw.takeIf { it.isNotBlank() && it != "new" }
            ClothEditScreen(repository, id, onDone = { pop(); bump() })
        }
        composable(
            Routes.MATCH_DETAIL,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "view" },
                navArgument("matchId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "view"
            val matchId = entry.arguments?.getString("matchId").orEmpty().ifBlank { null }
            MatchDetailScreen(
                repository = repository,
                mode = mode,
                matchId = matchId,
                onDone = { pop(); bump() },
                onClothClick = { nav.navigate(Routes.clothDetail(it)) },
                onEdit = {
                    if (!matchId.isNullOrBlank()) {
                        nav.navigate(Routes.matchDetail("edit", matchId))
                    }
                }
            )
        }
        composable(
            Routes.INSPIRATION_DETAIL,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "view" },
                navArgument("inspirationId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "view"
            val id = entry.arguments?.getString("inspirationId").orEmpty().ifBlank { null }
            InspirationDetailScreen(
                repository = repository,
                mode = mode,
                inspirationId = id,
                onDone = { pop(); bump() },
                onClothClick = { nav.navigate(Routes.clothDetail(it)) },
                onEdit = {
                    if (!id.isNullOrBlank()) {
                        nav.navigate(Routes.inspirationDetail("edit", id))
                    }
                }
            )
        }
        composable(Routes.CHECKIN) {
            CheckinScreen(repository, onBack = { pop(); bump() })
        }
        composable(Routes.WEAR_STATS) {
            WearStatsScreen(
                repository = repository,
                onBack = { pop() },
                onClothClick = { nav.navigate(Routes.clothDetail(it)) }
            )
        }
        composable(Routes.DISCARDED) {
            DiscardedScreen(
                repository = repository,
                onBack = { pop() },
                onClothClick = { nav.navigate(Routes.clothDetail(it)) }
            )
        }
        composable(
            Routes.BATCH_EDIT,
            arguments = listOf(navArgument("ids") { type = NavType.StringType })
        ) { entry ->
            val ids = entry.arguments?.getString("ids").orEmpty()
                .split(",").filter { it.isNotBlank() }.toSet()
            BatchEditScreen(repository, ids, onDone = { pop(); bump() })
        }
    }
}
