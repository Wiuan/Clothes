package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.JsonHelpers

data class SizeField(val key: String, val label: String)

data class DetailRow(val label: String, val value: String, val full: Boolean = false)

data class DetailSection(val title: String, val rows: List<DetailRow>)

object ClothFields {
    private val TOP_SIZE_FIELDS = listOf(
        SizeField("衣长", "衣长 (cm)"),
        SizeField("胸围", "胸围 (cm)"),
        SizeField("肩宽", "肩宽 (cm)"),
        SizeField("袖长", "袖长 (cm)")
    )
    private val TOP_EXTRA_SIZE_FIELDS = listOf(
        SizeField("后片长", "后片长 (cm)"),
        SizeField("领宽", "领宽 (cm)")
    )
    private val BOTTOM_SIZE_FIELDS = listOf(
        SizeField("裤长裙长", "裤长/裙长 (cm)"),
        SizeField("腰围", "腰围 (cm)"),
        SizeField("臀围", "臀围 (cm)")
    )
    private val BOTTOM_EXTRA_SIZE_FIELDS = listOf(
        SizeField("大腿围", "大腿围 (cm)"),
        SizeField("前档", "前档 (cm)"),
        SizeField("后档", "后档 (cm)"),
        SizeField("脚口", "脚口 (cm)")
    )

    fun migrateType(type: String): String = if (type == "外套") "长款" else type

    /** 与 uni-app `utils/constants.js` → `getSizeFieldsForType` 一致 */
    fun getSizeFieldsForType(type: String): List<SizeField> = when (migrateType(type)) {
        "下装" -> BOTTOM_SIZE_FIELDS + BOTTOM_EXTRA_SIZE_FIELDS
        "上衣" -> TOP_SIZE_FIELDS + TOP_EXTRA_SIZE_FIELDS
        "长款", "运动" -> TOP_SIZE_FIELDS
        else -> TOP_SIZE_FIELDS
    }

    /** 编辑页：当前类型字段 + 已保存但不在模板中的键（避免切换类型后丢显示） */
    fun getEditSizeFields(type: String, saved: Map<String, String>): List<SizeField> {
        val base = getSizeFieldsForType(type)
        val known = base.map { it.key }.toSet()
        val extra = saved.keys
            .filter { it.isNotBlank() && it !in known && saved[it]?.isNotBlank() == true }
            .sorted()
            .map { SizeField(it, "$it (cm)") }
        return base + extra
    }

    fun typeHasMaterial(type: String): Boolean {
        val t = migrateType(type)
        return t == "上衣" || t == "下装"
    }

    fun formatTempRange(item: ClothEntity): String {
        val min = item.tempMin
        val max = item.tempMax
        if (min == null && max == null) return "未设置"
        if (min != null && max != null) return "${min}-${max}℃"
        if (min != null) return "${min}℃以上"
        return "${max}℃以下"
    }

    /** 与 uni `buildClothDetailSections` 一致 */
    fun buildDetailSections(item: ClothEntity): List<DetailSection> {
        val sections = mutableListOf<DetailSection>()
        val sizes = JsonHelpers.parseSizes(item.sizesJson)
        val sizeFields = getSizeFieldsForType(item.type)
        if (sizeFields.isNotEmpty()) {
            sections.add(
                DetailSection(
                    title = "尺寸",
                    rows = sizeFields.map { f ->
                        val v = sizes[f.key]?.trim().orEmpty()
                        DetailRow(
                            label = f.label.replace(" (cm)", ""),
                            value = v.ifBlank { "-" }
                        )
                    }
                )
            )
        }
        val infoRows = mutableListOf<DetailRow>()
        if (item.purchaseDate.isNotBlank()) {
            infoRows.add(DetailRow("买入时间", item.purchaseDate))
        }
        if (item.purchasePrice.isNotBlank()) {
            val p = item.purchasePrice.trim()
            infoRows.add(
                DetailRow(
                    "买入价钱",
                    if (p.contains("元") || p.contains("¥")) p else "$p 元"
                )
            )
        }
        if (typeHasMaterial(item.type) && item.material.isNotBlank()) {
            infoRows.add(DetailRow("材质", item.material))
        }
        if (item.note.isNotBlank()) {
            infoRows.add(DetailRow("备注", item.note, full = true))
        }
        if (infoRows.isNotEmpty()) {
            sections.add(DetailSection("购买信息", infoRows))
        }
        return sections
    }
}
