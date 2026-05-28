package com.cloth.wardrobe.data

import android.content.Context

/** 穿着统计筛选 / 排序，退出后再进仍保留 */
class WearStatsUiPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class WearStatsFilterState(
        val period: String = "365d",
        val season: String = "",
        val sortAsc: Boolean = true
    )

    fun load(): WearStatsFilterState {
        val rawPeriod = prefs.getString(KEY_PERIOD, "365d") ?: "365d"
        val period = if (rawPeriod in VALID_PERIODS) rawPeriod else "365d"
        return WearStatsFilterState(
            period = period,
            season = prefs.getString(KEY_SEASON, "").orEmpty(),
            sortAsc = prefs.getBoolean(KEY_SORT_ASC, true)
        )
    }

    fun save(state: WearStatsFilterState) {
        prefs.edit()
            .putString(KEY_PERIOD, state.period)
            .putString(KEY_SEASON, state.season)
            .putBoolean(KEY_SORT_ASC, state.sortAsc)
            .apply()
    }

    companion object {
        private val VALID_PERIODS = setOf("30d", "90d", "365d", "all")
        private const val PREFS_NAME = "wear_stats_ui_v1"
        private const val KEY_PERIOD = "period"
        private const val KEY_SEASON = "filter_season"
        private const val KEY_SORT_ASC = "sort_asc"
    }
}
