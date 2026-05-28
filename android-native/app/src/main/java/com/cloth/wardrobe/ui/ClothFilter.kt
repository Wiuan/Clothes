package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.JsonHelpers

fun parseItemColors(colorsJson: String): List<String> =
    JsonHelpers.jsonToStringList(colorsJson).ifEmpty { listOf("其他") }

fun itemMatchesColor(item: ClothEntity, filterColor: String): Boolean {
    if (filterColor.isEmpty()) return true
    val list = parseItemColors(item.colorsJson)
    if (list.contains(filterColor)) return true
    if (filterColor == "其他") {
        return list.any { !WardrobeConstants.PRESET_COLORS.contains(it) || it == "其他" }
    }
    return false
}

fun filterDiscardedClothes(
    list: List<ClothEntity>,
    season: String = "",
    type: String = ""
): List<ClothEntity> = list.filter { item ->
    if (item.status != "discarded") return@filter false
    if (season.isNotEmpty() && item.season != season) return@filter false
    if (type.isNotEmpty() && item.type != type) return@filter false
    true
}

fun filterClothes(
    list: List<ClothEntity>,
    season: String,
    type: String,
    color: String,
    currentTemp: String = ""
): List<ClothEntity> = list.filter { item ->
    if (item.status == "discarded") return@filter false
    if (season.isNotEmpty() && item.season != season) return@filter false
    if (type.isNotEmpty() && item.type != type) return@filter false
    if (!itemMatchesColor(item, color)) return@filter false
    val temp = currentTemp.trim().toIntOrNull()
    if (temp != null && !matchTemperature(item, temp)) return@filter false
    true
}

private fun matchTemperature(item: ClothEntity, current: Int): Boolean {
    val min = item.tempMin
    val max = item.tempMax
    if (min == null && max == null) return true
    val lo = min ?: Int.MIN_VALUE
    val hi = max ?: Int.MAX_VALUE
    return current in lo..hi
}

private val LENGTH_KEYS = listOf("衣长", "裤长裙长")

private fun parseGarmentLength(item: ClothEntity): Int? {
    val sizes = JsonHelpers.parseSizes(item.sizesJson)
    for (key in LENGTH_KEYS) {
        val raw = sizes[key]?.trim().orEmpty()
        if (raw.isEmpty()) continue
        val n = raw.toDoubleOrNull() ?: continue
        if (n.isFinite()) return n.toInt()
    }
    return null
}

private fun byCreatedDesc(a: ClothEntity, b: ClothEntity): Int =
    (b.createdAt - a.createdAt).toInt()

private fun byCreatedAsc(a: ClothEntity, b: ClothEntity): Int =
    (a.createdAt - b.createdAt).toInt()

fun nextWardrobeSortField(current: String): Pair<String, Boolean> = when (current) {
    "created" -> "length" to true
    "length" -> "color" to true
    else -> "created" to false
}

fun sortClothes(
    list: List<ClothEntity>,
    field: String = "created",
    ascending: Boolean = false
): List<ClothEntity> {
    when (field) {
        "length" -> {
            val arr = list.toMutableList()
            arr.sortWith { a, b ->
                val la = parseGarmentLength(a)
                val lb = parseGarmentLength(b)
                when {
                    la == null && lb == null ->
                        if (ascending) byCreatedAsc(a, b) else byCreatedDesc(a, b)
                    la == null -> 1
                    lb == null -> -1
                    else -> {
                        val diff = la - lb
                        if (diff != 0) if (ascending) diff else -diff
                        else if (ascending) byCreatedAsc(a, b) else byCreatedDesc(a, b)
                    }
                }
            }
            return arr
        }
        "color" -> {
            val arr = list.toMutableList()
            arr.sortWith { a, b ->
                val ka = colorSortKey(a)
                val kb = colorSortKey(b)
                var cmp = when {
                    ka.first != kb.first -> ka.first.compareTo(kb.first)
                    ka.first == 0 -> ka.second.compareTo(kb.second)
                    else -> ka.third.compareTo(kb.third)
                }
                if (cmp == 0) cmp = if (ascending) byCreatedAsc(a, b) else byCreatedDesc(a, b)
                if (ascending) cmp else -cmp
            }
            return arr
        }
        else -> return if (ascending) list.sortedBy { it.createdAt }
        else list.sortedByDescending { it.createdAt }
    }
}

private fun colorSortKey(item: ClothEntity): Triple<Int, Int, String> {
    val name = parseItemColors(item.colorsJson).firstOrNull() ?: "其他"
    val idx = WardrobeConstants.PRESET_COLORS.indexOf(name)
    return if (idx >= 0) Triple(0, idx, name) else Triple(1, 0, name)
}

fun colorLabel(item: ClothEntity): String =
    parseItemColors(item.colorsJson).joinToString("·").ifEmpty { "其他" }

fun colorHex(name: String): androidx.compose.ui.graphics.Color =
    WardrobeConstants.COLOR_HEX[name] ?: WardrobeConstants.COLOR_HEX["其他"]!!

fun colorHexFor(item: ClothEntity, name: String): androidx.compose.ui.graphics.Color {
    return try {
        val map = org.json.JSONObject(item.colorHexMapJson.ifBlank { "{}" })
        val hex = map.optString(name, "")
        if (hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        } else {
            colorHex(name)
        }
    } catch (_: Exception) {
        colorHex(name)
    }
}
