package com.cloth.wardrobe.data

import android.content.Context

/** 今日推荐当天缓存：同日同季固定一套，换一套覆盖 */
class TodayRecommendPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Cached(
        val date: String,
        val season: String,
        val inspirationId: String,
        val inspirationName: String,
        val palette: List<String>,
        val topId: String,
        val bottomId: String,
        val longId: String
    )

    fun loadFor(date: String, season: String): Cached? {
        if (prefs.getString(KEY_DATE, "") != date) return null
        if (prefs.getString(KEY_SEASON, "") != season) return null
        val paletteRaw = prefs.getString(KEY_PALETTE, "").orEmpty()
        return Cached(
            date = date,
            season = season,
            inspirationId = prefs.getString(KEY_INSP_ID, "").orEmpty(),
            inspirationName = prefs.getString(KEY_INSP_NAME, "").orEmpty(),
            palette = if (paletteRaw.isBlank()) emptyList()
            else paletteRaw.split("·").map { it.trim() }.filter { it.isNotEmpty() },
            topId = prefs.getString(KEY_TOP_ID, "").orEmpty(),
            bottomId = prefs.getString(KEY_BOTTOM_ID, "").orEmpty(),
            longId = prefs.getString(KEY_LONG_ID, "").orEmpty()
        )
    }

    fun save(date: String, season: String, cached: Cached) {
        prefs.edit()
            .putString(KEY_DATE, date)
            .putString(KEY_SEASON, season)
            .putString(KEY_INSP_ID, cached.inspirationId)
            .putString(KEY_INSP_NAME, cached.inspirationName)
            .putString(KEY_PALETTE, cached.palette.joinToString("·"))
            .putString(KEY_TOP_ID, cached.topId)
            .putString(KEY_BOTTOM_ID, cached.bottomId)
            .putString(KEY_LONG_ID, cached.longId)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "today_recommend_v1"
        private const val KEY_DATE = "date"
        private const val KEY_SEASON = "season"
        private const val KEY_INSP_ID = "inspiration_id"
        private const val KEY_INSP_NAME = "inspiration_name"
        private const val KEY_PALETTE = "palette"
        private const val KEY_TOP_ID = "top_id"
        private const val KEY_BOTTOM_ID = "bottom_id"
        private const val KEY_LONG_ID = "long_id"
    }
}
