package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.JsonHelpers
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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
        val trimmed = json.trim().ifBlank { """{"primary":[],"secondary":[],"accent":[]}""" }
        val o = when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("\"") -> JSONObject(JSONTokener(trimmed))
            else -> return InspirationColorTags(emptyList(), emptyList(), emptyList())
        }
        InspirationColorTags(
            colorTagList(o, "primary"),
            colorTagList(o, "secondary"),
            colorTagList(o, "accent")
        )
    } catch (_: Exception) {
        InspirationColorTags(emptyList(), emptyList(), emptyList())
    }
}

private fun colorTagList(o: JSONObject, key: String): List<String> {
    o.optJSONArray(key)?.let { arr ->
        return JsonHelpers.jsonToStringListFromArray(arr)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val single = o.optString(key, "").trim()
    if (single.isNotBlank()) {
        return JsonHelpers.jsonToStringList(single)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    return emptyList()
}

private fun normalizeColorName(name: String): String {
    val t = name.trim()
    if (t.isBlank() || t == "其他") return t
    return if (t.endsWith("色") && t.length > 1) t.dropLast(1) else t
}

private fun colorsOverlap(a: List<String>, b: List<String>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    val set = b.map { normalizeColorName(it) }.filter { it.isNotBlank() && it != "其他" }.toSet()
    if (set.isEmpty()) return false
    return a.any {
        val n = normalizeColorName(it)
        n.isNotBlank() && n != "其他" && set.contains(n)
    }
}

private fun inspirationMatchColors(tags: InspirationColorTags): List<String> =
    (tags.primary + tags.secondary + tags.accent)
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "其他" }
        .distinct()

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

/** 未关联灵感：同季（含未填季节）优先，其它季节在后 */
private fun unlinkedInspirationSeasonRank(clothSeason: String, inspSeason: String): Int =
    when {
        inspSeason.isBlank() -> 0
        inspSeason == clothSeason -> 0
        else -> 1
    }

fun buildColorRecommendations(
    cloth: ClothEntity,
    inspirations: List<InspirationEntity>,
    allClothes: List<ClothEntity>
): ColorRecommendResult {
    val clothColors = clothColorsForRecommend(cloth)
    val linked = inspirations.filter { isInspirationLinkedToCloth(it, cloth.id) }
        .sortedByDescending { it.createdAt }
    val linkedIds = linked.map { it.id }.toSet()
    val unlinkedMatched = inspirations.filter { insp ->
        if (linkedIds.contains(insp.id)) return@filter false
        val matchColors = inspirationMatchColors(parseColorTags(insp.colorTagsJson))
        if (matchColors.isEmpty()) return@filter false
        colorsOverlap(clothColors, matchColors)
    }.sortedWith(
        compareBy<InspirationEntity> { unlinkedInspirationSeasonRank(cloth.season, it.season) }
            .thenByDescending { it.createdAt }
    )
    val matched = linked + unlinkedMatched
    val palette = mutableSetOf<String>()
    matched.forEach { insp ->
        val tags = parseColorTags(insp.colorTagsJson)
        tags.secondary.forEach { palette.add(it) }
        tags.accent.forEach { palette.add(it) }
    }
    if (palette.isEmpty()) {
        val clothSet = clothColors.map { normalizeColorName(it) }.toSet()
        matched.forEach { insp ->
            inspirationMatchColors(parseColorTags(insp.colorTagsJson))
                .map { normalizeColorName(it) }
                .filter { it.isNotBlank() && it != "其他" && !clothSet.contains(it) }
                .forEach { palette.add(it) }
        }
    }
    val paletteList = palette.toList()
    val companionPalette = paletteList.ifEmpty {
        if (matched.isEmpty()) clothColors else emptyList()
    }
    val companions = allClothes.filter { c ->
        c.id != cloth.id && c.status != "discarded" && c.season == cloth.season &&
            colorsOverlap(clothColorsForRecommend(c), companionPalette)
    }.sortedWith(
        compareBy<ClothEntity> { companionTypeRank(cloth.type, it.type) }
            .thenByDescending { it.createdAt }
    )
    return ColorRecommendResult(
        ready = clothColors.isNotEmpty() || linked.isNotEmpty(),
        matchedInspirations = matched,
        companions = companions,
        palette = if (paletteList.isNotEmpty()) paletteList else companionPalette,
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
