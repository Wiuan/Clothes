package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.JsonHelpers
import org.json.JSONArray
import org.json.JSONObject

data class InspirationColorTags(
    val primary: List<String>,
    val secondary: List<String>,
    val accent: List<String>
)

data class InspirationLink(val clothId: String, val relation: String)

data class ColorRecommendResult(
    val ready: Boolean,
    val matchedInspirations: List<InspirationEntity>,
    val companions: List<ClothEntity>,
    val palette: List<String>,
    val matchedTotal: Int,
    val companionTotal: Int
)

fun parseColorTags(json: String): InspirationColorTags {
    return try {
        val o = JSONObject(json.ifBlank { """{"primary":[],"secondary":[],"accent":[]}""" })
        fun tags(key: String) = JsonHelpers.jsonToStringListFromArray(o.optJSONArray(key))
        InspirationColorTags(tags("primary"), tags("secondary"), tags("accent"))
    } catch (_: Exception) {
        InspirationColorTags(emptyList(), emptyList(), emptyList())
    }
}

fun parseLinks(json: String): List<InspirationLink> {
    return try {
        val arr = JSONArray(json.ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val clothId = o.optString("clothId", "")
            if (clothId.isBlank()) return@mapNotNull null
            val rel = if (o.optString("relation") == "want_to_buy") "want_to_buy" else "have_similar"
            InspirationLink(clothId, rel)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun isInspirationLinkedToCloth(insp: InspirationEntity, clothId: String): Boolean =
    parseLinks(insp.linksJson).any { it.clothId == clothId }

private fun colorsOverlap(a: List<String>, b: List<String>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    val set = b.toSet()
    return a.any { set.contains(it) }
}

private fun seasonsMatch(clothSeason: String, inspSeason: String): Boolean =
    inspSeason.isBlank() || clothSeason == inspSeason

fun buildColorRecommendations(
    cloth: ClothEntity,
    inspirations: List<InspirationEntity>,
    allClothes: List<ClothEntity>,
    maxInsp: Int = 8,
    maxCloth: Int = 10
): ColorRecommendResult {
    val clothColors = parseItemColors(cloth.colorsJson)
    val linked = inspirations.filter { isInspirationLinkedToCloth(it, cloth.id) }
        .sortedByDescending { it.createdAt }
    val linkedIds = linked.map { it.id }.toSet()
    val colorMatched = inspirations.filter { insp ->
        if (linkedIds.contains(insp.id)) return@filter false
        if (!seasonsMatch(cloth.season, insp.season)) return@filter false
        val primary = parseColorTags(insp.colorTagsJson).primary
        if (primary.isEmpty()) return@filter false
        colorsOverlap(clothColors, primary)
    }.sortedByDescending { it.createdAt }
    val matched = linked + colorMatched
    val palette = mutableSetOf<String>()
    matched.forEach { insp ->
        val tags = parseColorTags(insp.colorTagsJson)
        tags.secondary.forEach { palette.add(it) }
        tags.accent.forEach { palette.add(it) }
    }
    if (palette.isEmpty()) {
        val clothSet = clothColors.toSet()
        matched.forEach { insp ->
            parseColorTags(insp.colorTagsJson).primary
                .filter { !clothSet.contains(it) }
                .forEach { palette.add(it) }
        }
    }
    val paletteList = palette.toList()
    val companions = allClothes.filter { c ->
        c.id != cloth.id && c.status != "discarded" && c.season == cloth.season &&
            colorsOverlap(parseItemColors(c.colorsJson), paletteList)
    }.sortedWith { a, b ->
        val ra = companionTypeRank(cloth.type, a.type)
        val rb = companionTypeRank(cloth.type, b.type)
        if (ra != rb) ra.compareTo(rb) else (b.createdAt - a.createdAt).toInt()
    }
    return ColorRecommendResult(
        ready = clothColors.isNotEmpty() || linked.isNotEmpty(),
        matchedInspirations = matched.take(maxInsp),
        companions = companions.take(maxCloth),
        palette = paletteList,
        matchedTotal = matched.size,
        companionTotal = companions.size
    )
}

private fun companionTypeRank(currentType: String, candidateType: String): Int {
    return when (currentType) {
        "上衣" -> when (candidateType) {
            "下装" -> 0
            "上衣" -> 1
            else -> 2
        }
        "下装" -> when (candidateType) {
            "上衣" -> 0
            "下装" -> 1
            else -> 2
        }
        else -> when (candidateType) {
            "上衣" -> 0
            "下装" -> 1
            else -> 2
        }
    }
}

fun filterInspirations(
    list: List<InspirationEntity>,
    style: String,
    season: String,
    primaryColor: String,
    wantToBuyOnly: Boolean
): List<InspirationEntity> = list.filter { item ->
    if (style.isNotEmpty() && style != WardrobeConstants.ALL && item.style != style) return@filter false
    if (season.isNotEmpty() && season != WardrobeConstants.ALL && item.season != season) return@filter false
    if (primaryColor.isNotEmpty() && primaryColor != WardrobeConstants.ALL) {
        if (!parseColorTags(item.colorTagsJson).primary.contains(primaryColor)) return@filter false
    }
    if (wantToBuyOnly && parseLinks(item.linksJson).none { it.relation == "want_to_buy" }) return@filter false
    true
}

fun allPrimaryColorsFromInspirations(list: List<InspirationEntity>): List<String> {
    val set = linkedSetOf<String>()
    list.forEach { insp ->
        parseColorTags(insp.colorTagsJson).primary.forEach { if (it.isNotBlank()) set.add(it) }
    }
    return set.toList()
}

private fun inspirationColorSortKey(item: InspirationEntity): Triple<Int, Int, String> {
    val name = parseColorTags(item.colorTagsJson).primary.firstOrNull() ?: "其他"
    val idx = WardrobeConstants.PRESET_COLORS.indexOf(name)
    return if (idx >= 0) Triple(0, idx, name) else Triple(1, 0, name)
}

private fun byInspCreatedDesc(a: InspirationEntity, b: InspirationEntity): Int =
    (b.createdAt - a.createdAt).toInt()

private fun byInspCreatedAsc(a: InspirationEntity, b: InspirationEntity): Int =
    (a.createdAt - b.createdAt).toInt()

fun nextInspirationSortField(current: String): Pair<String, Boolean> = when (current) {
    "created" -> "color" to true
    else -> "created" to false
}

/** 与 uni-app `sortInspirations` 一致 */
fun sortInspirations(
    list: List<InspirationEntity>,
    field: String = "created",
    ascending: Boolean = false
): List<InspirationEntity> {
    when (field) {
        "color" -> {
            val arr = list.toMutableList()
            arr.sortWith { a, b ->
                val ka = inspirationColorSortKey(a)
                val kb = inspirationColorSortKey(b)
                var cmp = when {
                    ka.first != kb.first -> ka.first.compareTo(kb.first)
                    ka.first == 0 -> ka.second.compareTo(kb.second)
                    else -> ka.third.compareTo(kb.third)
                }
                if (cmp == 0) cmp = if (ascending) byInspCreatedAsc(a, b) else byInspCreatedDesc(a, b)
                if (ascending) cmp else -cmp
            }
            return arr
        }
        else -> return if (ascending) list.sortedBy { it.createdAt }
        else list.sortedByDescending { it.createdAt }
    }
}
