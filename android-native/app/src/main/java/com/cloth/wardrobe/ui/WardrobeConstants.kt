package com.cloth.wardrobe.ui

import androidx.compose.ui.graphics.Color

object WardrobeConstants {
    val Accent = Color(0xFFFF2442)
    val PageBg = Color(0xFFF7F7F8)
    val ChipBg = Color(0xFFF7F7F8)
    val ChipActiveBg = Color(0xFFFFF5F6)
    val ChipActiveBorder = Color(0xFFFFCDD2)
    val TextPrimary = Color(0xFF333333)
    val TextSecondary = Color(0xFF888888)
    val TextMuted = Color(0xFF999999)
    val TextHint = Color(0xFFBBBBBB)
    val Divider = Color(0xFFEEEEEE)
    val CardBg = Color(0xFFFFFFFF)
    val ImagePlaceholder = Color(0xFFF5F5F5)
    val LinkBlue = Color(0xFF1E88E5)
    val DiscardBrown = Color(0xFF996600)
    val FabShadow = Color(0x59FF2442)

    const val ALL = "全部"

    val SEASONS = listOf("夏", "春秋", "冬")
    val TYPES = listOf("上衣", "下装", "长款", "运动")
    val PRESET_COLORS = listOf("红", "黄", "蓝", "黑", "白", "绿", "紫", "棕", "灰", "其他")

    val COLOR_HEX = mapOf(
        "红" to Color(0xFFE53935),
        "黄" to Color(0xFFF9A825),
        "蓝" to Color(0xFF1E88E5),
        "黑" to Color(0xFF212121),
        "白" to Color(0xFFE0E0E0),
        "绿" to Color(0xFF43A047),
        "紫" to Color(0xFF8E24AA),
        "棕" to Color(0xFF6D4C41),
        "灰" to Color(0xFF757575),
        "其他" to Color(0xFF9E9E9E)
    )

    val SORT_FIELD_LABELS = mapOf(
        "created" to "时间",
        "length" to "衣长",
        "color" to "颜色"
    )

    val INSPIRATION_SORT_FIELD_LABELS = mapOf(
        "created" to "时间",
        "color" to "颜色"
    )

    val INSPIRATION_STYLES = listOf("通勤", "休闲", "约会", "运动")

    val LINK_RELATION_LABEL = mapOf(
        "have_similar" to "我有类似",
        "want_to_buy" to "想买"
    )
}
