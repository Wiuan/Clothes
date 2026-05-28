package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.JsonHelpers
import org.json.JSONObject

data class ColorFormState(
    val selectedColors: Set<String> = setOf("白"),
    val otherSelected: Boolean = false,
    val colorCustom: String = "",
    val colorHex: String = ""
)

data class ColorPayload(
    val colorsJson: String,
    val colorHexMapJson: String
)

fun colorsToForm(item: ClothEntity): ColorFormState {
    val colors = parseItemColors(item.colorsJson)
    val presets = colors.filter { WardrobeConstants.PRESET_COLORS.contains(it) && it != "其他" }.toSet()
    val custom = colors.filter { !WardrobeConstants.PRESET_COLORS.contains(it) || it == "其他" }
    val hexMap = try {
        JSONObject(item.colorHexMapJson.ifBlank { "{}" })
    } catch (_: Exception) {
        JSONObject()
    }
    val customName = custom.firstOrNull() ?: ""
    return ColorFormState(
        selectedColors = if (presets.isNotEmpty()) presets else setOf("白"),
        otherSelected = custom.isNotEmpty(),
        colorCustom = customName,
        colorHex = if (customName.isNotBlank()) hexMap.optString(customName, "") else ""
    )
}

fun buildColorsPayload(form: ColorFormState): ColorPayload? {
    val colors = form.selectedColors.filter { it.isNotBlank() && it != "其他" }.toMutableList()
    val hexObj = JSONObject()
    if (form.otherSelected) {
        val custom = form.colorCustom.trim().ifBlank { "其他" }
        if (!colors.contains(custom)) colors.add(custom)
        var hex = form.colorHex.trim()
        if (hex.isNotEmpty() && !hex.startsWith("#")) hex = "#$hex"
        if (hex.matches(Regex("#[0-9A-Fa-f]{6}"))) hexObj.put(custom, hex)
    }
    if (colors.isEmpty()) return null
    return ColorPayload(
        colorsJson = JsonHelpers.stringListToJson(colors),
        colorHexMapJson = hexObj.toString()
    )
}

fun isPresetColor(name: String): Boolean =
    name.isNotBlank() && name != "其他" && WardrobeConstants.PRESET_COLORS.contains(name)
