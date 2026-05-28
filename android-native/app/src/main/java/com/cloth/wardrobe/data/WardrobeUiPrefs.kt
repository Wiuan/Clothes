package com.cloth.wardrobe.data

import android.content.Context

/** 衣柜列表筛选 / 排序，仅「重置」时清空 */
class WardrobeUiPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class WardrobeFilterState(
        val season: String = "",
        val type: String = "",
        val color: String = "",
        val temp: String = "",
        val sortField: String = "created",
        val sortAsc: Boolean = false
    )

    fun load(): WardrobeFilterState {
        val rawField = prefs.getString(KEY_SORT_FIELD, "created") ?: "created"
        val sortField = if (rawField in VALID_SORT_FIELDS) rawField else "created"
        return WardrobeFilterState(
            season = prefs.getString(KEY_SEASON, "").orEmpty(),
            type = prefs.getString(KEY_TYPE, "").orEmpty(),
            color = prefs.getString(KEY_COLOR, "").orEmpty(),
            temp = prefs.getString(KEY_TEMP, "").orEmpty(),
            sortField = sortField,
            sortAsc = prefs.getBoolean(KEY_SORT_ASC, false)
        )
    }

    fun save(state: WardrobeFilterState) {
        prefs.edit()
            .putString(KEY_SEASON, state.season)
            .putString(KEY_TYPE, state.type)
            .putString(KEY_COLOR, state.color)
            .putString(KEY_TEMP, state.temp)
            .putString(KEY_SORT_FIELD, state.sortField)
            .putBoolean(KEY_SORT_ASC, state.sortAsc)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private val VALID_SORT_FIELDS = setOf("created", "length", "color")
        private const val PREFS_NAME = "wardrobe_ui_v1"
        private const val KEY_SEASON = "filter_season"
        private const val KEY_TYPE = "filter_type"
        private const val KEY_COLOR = "filter_color"
        private const val KEY_TEMP = "filter_temp"
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_ASC = "sort_asc"
    }
}
