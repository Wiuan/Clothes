package com.cloth.wardrobe.export

import android.content.Context
import android.net.Uri
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.JsonHelpers
import com.cloth.wardrobe.data.MatchEntity
import com.cloth.wardrobe.data.WearLogEntity
import org.json.JSONObject
import java.util.zip.ZipInputStream

data class ImportBundle(
    val clothes: List<ClothEntity>,
    val matches: List<MatchEntity>,
    val inspirations: List<InspirationEntity>,
    val wearLogs: List<WearLogEntity> = emptyList()
)

data class ImportResult(
    val clothCount: Int,
    val matchCount: Int,
    val inspirationCount: Int,
    val wearLogCount: Int = 0
) {
    fun summaryText(): String {
        val parts = mutableListOf(
            "衣服 $clothCount 件",
            "搭配 $matchCount 组",
            "灵感 $inspirationCount 条"
        )
        if (wearLogCount > 0) parts.add("穿着记录 $wearLogCount 条")
        return parts.joinToString("，")
    }
}

object ZipBundleImporter {
    fun importFromUri(context: Context, uri: Uri): ImportBundle {
        val clothes = mutableListOf<ClothEntity>()
        val matches = mutableListOf<MatchEntity>()
        val inspirations = mutableListOf<InspirationEntity>()
        val wearLogs = mutableListOf<WearLogEntity>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "manifest.json" || entry.name.endsWith("/manifest.json") -> {
                            parseManifest(
                                zis.readBytes().toString(Charsets.UTF_8),
                                clothes,
                                matches,
                                inspirations,
                                wearLogs
                            )
                        }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
                            val name = entry.name.removePrefix("images/")
                            val safeRef = name.removeSuffix(".jpg")
                            ImageStore.fileForRef(context, safeRef).outputStream()
                                .use { out -> zis.copyTo(out) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: error("无法打开 ZIP")
        if (clothes.isEmpty()) error("ZIP 中无衣物数据")
        return ImportBundle(clothes, matches, inspirations, wearLogs)
    }

    private fun parseManifest(
        text: String,
        clothes: MutableList<ClothEntity>,
        matches: MutableList<MatchEntity>,
        inspirations: MutableList<InspirationEntity>,
        wearLogs: MutableList<WearLogEntity>
    ) {
        val root = JSONObject(text)
        if (root.optInt("version", 0) != 3) error("仅支持 version 3 备份包")
        root.optJSONArray("clothes")?.let { arr ->
            for (i in 0 until arr.length()) {
                parseCloth(arr.getJSONObject(i))?.let { clothes.add(it) }
            }
        }
        root.optJSONArray("matches")?.let { arr ->
            for (i in 0 until arr.length()) {
                parseMatch(arr.getJSONObject(i))?.let { matches.add(it) }
            }
        }
        root.optJSONArray("inspirations")?.let { arr ->
            for (i in 0 until arr.length()) {
                parseInsp(arr.getJSONObject(i))?.let { inspirations.add(it) }
            }
        }
        root.optJSONArray("wearLogs")?.let { arr ->
            for (i in 0 until arr.length()) {
                parseWear(arr.getJSONObject(i))?.let { wearLogs.add(it) }
            }
        }
    }

    private fun parseCloth(o: JSONObject): ClothEntity? {
        val id = o.optString("id", "")
        if (id.isBlank()) return null
        val colorsJson = JsonHelpers.stringListToJson(
            JsonHelpers.jsonToStringListFromArray(o.optJSONArray("colors"))
        )
        return ClothEntity(
            id = id,
            name = o.optString("name", "未命名"),
            colorsJson = colorsJson.ifBlank { JsonHelpers.stringListToJson(listOf("其他")) },
            season = o.optString("season", "夏"),
            type = o.optString("type", "上衣").let { if (it == "外套") "长款" else it },
            status = o.optString("status", "active"),
            imageRef = o.optString("imageRef", ImageStore.refForClothId(id)),
            note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            tempMin = if (o.has("tempMin") && !o.isNull("tempMin")) o.optInt("tempMin") else null,
            tempMax = if (o.has("tempMax") && !o.isNull("tempMax")) o.optInt("tempMax") else null,
            sizesJson = o.optJSONObject("sizes")?.toString() ?: "{}",
            purchaseDate = o.optString("purchaseDate", ""),
            purchasePrice = o.optString("purchasePrice", ""),
            material = o.optString("material", ""),
            discardedAt = if (o.has("discardedAt") && !o.isNull("discardedAt")) o.optLong("discardedAt") else null,
            colorHexMapJson = o.optJSONObject("colorHexMap")?.toString() ?: "{}"
        )
    }

    private fun parseMatch(o: JSONObject): MatchEntity? {
        val id = o.optString("id", "")
        if (id.isBlank()) return null
        return MatchEntity(
            id = id,
            name = o.optString("name", "搭配"),
            clothIdsJson = JsonHelpers.stringListToJson(
                JsonHelpers.jsonToStringListFromArray(o.optJSONArray("clothIds"))
            ),
            note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun parseInsp(o: JSONObject): InspirationEntity? {
        val id = o.optString("id", "")
        if (id.isBlank()) return null
        return InspirationEntity(
            id = id,
            name = o.optString("name", o.optString("title", "灵感")),
            imageRef = o.optString("imageRef", ImageStore.refForInspirationId(id)),
            note = o.optString("note", ""),
            season = o.optString("season", ""),
            style = o.optString("style", ""),
            occasion = o.optString("occasion", ""),
            colorTagsJson = o.optJSONObject("colorTags")?.toString()
                ?: """{"primary":[],"secondary":[],"accent":[]}""",
            linksJson = o.optJSONArray("links")?.toString() ?: "[]",
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun parseWear(o: JSONObject): WearLogEntity? {
        val id = o.optString("id", "")
        if (id.isBlank()) return null
        val clothIds = JsonHelpers.jsonToStringListFromArray(o.optJSONArray("clothIds"))
        if (clothIds.isEmpty()) return null
        return WearLogEntity(
            id = id,
            date = o.optString("date", "").take(10),
            type = if (o.optString("type") == "match") "match" else "single",
            clothIdsJson = JsonHelpers.stringListToJson(clothIds),
            matchId = o.optString("matchId").takeIf { it.isNotBlank() },
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
