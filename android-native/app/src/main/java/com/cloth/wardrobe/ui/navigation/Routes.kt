package com.cloth.wardrobe.ui.navigation

object Routes {
    const val WARDROBE = "wardrobe"
    const val MATCH = "match"
    const val INSPIRATION = "inspiration"
    const val CLOTH_DETAIL = "cloth/{clothId}"
    const val CLOTH_EDIT = "cloth_edit/{clothId}"
    const val MATCH_DETAIL = "match_detail?mode={mode}&matchId={matchId}"
    const val INSPIRATION_DETAIL = "inspiration_detail?mode={mode}&inspirationId={inspirationId}"
    const val CHECKIN = "checkin"
    const val WEAR_STATS = "wear_stats"
    const val DISCARDED = "discarded"
    const val BATCH_EDIT = "batch_edit/{ids}"

    fun clothDetail(id: String) = "cloth/$id"
    fun clothEdit(id: String? = null) = if (!id.isNullOrBlank()) "cloth_edit/$id" else "cloth_edit/new"
    fun matchDetail(mode: String, matchId: String = "") = "match_detail?mode=$mode&matchId=$matchId"
    fun inspirationDetail(mode: String, inspirationId: String = "") =
        "inspiration_detail?mode=$mode&inspirationId=$inspirationId"
    fun batchEdit(ids: String) = "batch_edit/$ids"
}
