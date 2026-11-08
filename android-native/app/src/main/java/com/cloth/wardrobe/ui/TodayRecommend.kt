package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.TodayRecommendPrefs
import com.cloth.wardrobe.data.WearLogUtils
import java.time.LocalDate
import kotlin.random.Random

data class TodayRecommendResult(
    val season: String,
    val inspirationId: String?,
    val inspirationName: String,
    val palette: List<String>,
    val topId: String?,
    val bottomId: String?,
    val longId: String?
) {
    val hasOutfit: Boolean
        get() = (topId != null && bottomId != null) || longId != null

    val wearClothIds: List<String>
        get() = when {
            topId != null && bottomId != null -> listOfNotNull(topId, bottomId)
            longId != null -> listOf(longId)
            else -> emptyList()
        }

    fun toCache(date: String) = TodayRecommendPrefs.Cached(
        date = date,
        season = season,
        inspirationId = inspirationId.orEmpty(),
        inspirationName = inspirationName,
        palette = palette,
        topId = topId.orEmpty(),
        bottomId = bottomId.orEmpty(),
        longId = longId.orEmpty()
    )

    companion object {
        fun fromCache(c: TodayRecommendPrefs.Cached) = TodayRecommendResult(
            season = c.season,
            inspirationId = c.inspirationId.ifBlank { null },
            inspirationName = c.inspirationName,
            palette = c.palette,
            topId = c.topId.ifBlank { null },
            bottomId = c.bottomId.ifBlank { null },
            longId = c.longId.ifBlank { null }
        )
    }
}

/** 按日期推断季节：夏 6–8 / 冬 12–2 / 其余春秋 */
fun inferSeasonFromDate(date: LocalDate = LocalDate.now()): String = when (date.monthValue) {
    in 6..8 -> "夏"
    12, 1, 2 -> "冬"
    else -> "春秋"
}

fun todayRecommendDateStr(): String = WearLogUtils.todayDateStr()

/**
 * 依据灵感配色，随机推荐当前季节上衣+下装；凑不齐时退成长款。
 * @param exclude 换一套时尽量避开上一套
 */
fun buildTodayRecommend(
    season: String,
    clothes: List<ClothEntity>,
    inspirations: List<InspirationEntity>,
    random: Random = Random.Default,
    exclude: TodayRecommendResult? = null
): TodayRecommendResult {
    val active = clothes.filter { it.status != "discarded" && it.season == season }
    val tops = active.filter { it.type == "上衣" }
    val bottoms = active.filter { it.type == "下装" }
    val longs = active.filter { it.type == "长款" }

    val inspCandidates = inspirations.filter { insp ->
        (insp.season.isBlank() || insp.season == season) &&
            inspirationPaletteColors(insp).isNotEmpty()
    }.shuffled(random)

    fun pickCloth(
        pool: List<ClothEntity>,
        palette: List<String>,
        excludeId: String?
    ): ClothEntity? {
        if (pool.isEmpty()) return null
        val colored = if (palette.isNotEmpty()) {
            pool.filter { colorsOverlap(clothColorsForRecommend(it), palette) }
        } else emptyList()
        val base = if (colored.isNotEmpty()) colored else pool
        val without = base.filter { excludeId == null || it.id != excludeId }
        return (without.ifEmpty { base }).randomOrNull(random)
    }

    fun inspLabel(insp: InspirationEntity?): String {
        if (insp == null) return "暂无匹配灵感"
        return insp.name.ifBlank { insp.style.ifBlank { "灵感" } }
    }

    // 优先：灵感配色命中上+下
    for (insp in inspCandidates) {
        val palette = inspirationPaletteColors(insp)
        val top = pickCloth(tops, palette, exclude?.topId)
        val bottom = pickCloth(bottoms, palette, exclude?.bottomId)
        if (top != null && bottom != null) {
            return TodayRecommendResult(
                season = season,
                inspirationId = insp.id,
                inspirationName = inspLabel(insp),
                palette = palette,
                topId = top.id,
                bottomId = bottom.id,
                longId = null
            )
        }
    }

    // 放宽：同季任意上+下（仍带一条灵感作展示）
    val fallbackInsp = inspCandidates.firstOrNull()
    val fallbackPalette = fallbackInsp?.let { inspirationPaletteColors(it) }.orEmpty()
    val top = pickCloth(tops, fallbackPalette, exclude?.topId)
    val bottom = pickCloth(bottoms, fallbackPalette, exclude?.bottomId)
    if (top != null && bottom != null) {
        return TodayRecommendResult(
            season = season,
            inspirationId = fallbackInsp?.id,
            inspirationName = inspLabel(fallbackInsp),
            palette = fallbackPalette,
            topId = top.id,
            bottomId = bottom.id,
            longId = null
        )
    }

    // 兜底：同季长款
    val longItem = pickCloth(longs, fallbackPalette, exclude?.longId)
    return TodayRecommendResult(
        season = season,
        inspirationId = fallbackInsp?.id,
        inspirationName = when {
            longItem != null -> inspLabel(fallbackInsp).let {
                if (fallbackInsp == null) "长款兜底" else it
            }
            else -> "暂无可用衣物"
        },
        palette = fallbackPalette,
        topId = null,
        bottomId = null,
        longId = longItem?.id
    )
}
