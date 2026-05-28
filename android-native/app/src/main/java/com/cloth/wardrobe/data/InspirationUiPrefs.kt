package com.cloth.wardrobe.data

import android.content.Context

/** 灵感库列表筛选 / 排序，仅「重置」时清空 */
class InspirationUiPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class InspirationFilterState(
        val style: String = "",
        val season: String = "",
        val color: String = "",
        val wantToBuyOnly: Boolean = false,
        val sortField: String = "created",
        val sortAsc: Boolean = false
    )

    fun load(): InspirationFilterState {
        val rawField = prefs.getString(KEY_SORT_FIELD, "created") ?: "created"
        val sortField = if (rawField in VALID_SORT_FIELDS) rawField else "created"
        return InspirationFilterState(
            style = prefs.getString(KEY_STYLE, "").orEmpty(),
            season = prefs.getString(KEY_SEASON, "").orEmpty(),
            color = prefs.getString(KEY_COLOR, "").orEmpty(),
            wantToBuyOnly = prefs.getBoolean(KEY_WANT_TO_BUY, false),
            sortField = sortField,
            sortAsc = prefs.getBoolean(KEY_SORT_ASC, false)
        )
    }

    fun save(state: InspirationFilterState) {
        prefs.edit()
            .putString(KEY_STYLE, state.style)
            .putString(KEY_SEASON, state.season)
            .putString(KEY_COLOR, state.color)
            .putBoolean(KEY_WANT_TO_BUY, state.wantToBuyOnly)
            .putString(KEY_SORT_FIELD, state.sortField)
            .putBoolean(KEY_SORT_ASC, state.sortAsc)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private val VALID_SORT_FIELDS = setOf("created", "color")
        private const val PREFS_NAME = "inspiration_ui_v1"
        private const val KEY_STYLE = "filter_style"
        private const val KEY_SEASON = "filter_season"
        private const val KEY_COLOR = "filter_color"
        private const val KEY_WANT_TO_BUY = "want_to_buy"
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_ASC = "sort_asc"
    }
}
