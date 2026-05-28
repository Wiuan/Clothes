package com.cloth.wardrobe.ui

/**
 * 列表进入详情前写入当前展示顺序（含筛选、排序），详情左右滑与此一致。
 */
object BrowseOrderHolder {
    var clothIds: List<String> = emptyList()
    var inspirationIds: List<String> = emptyList()

    fun clothIdsForSwipe(anchorId: String): List<String>? =
        clothIds.takeIf { it.isNotEmpty() && anchorId in it }

    fun inspirationIdsForSwipe(anchorId: String): List<String>? =
        inspirationIds.takeIf { it.isNotEmpty() && anchorId in it }
}
