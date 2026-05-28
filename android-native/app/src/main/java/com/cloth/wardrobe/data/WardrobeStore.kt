package com.cloth.wardrobe.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WardrobeStore(context: Context) {
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val clothesFile = File(dir, "clothes.json")
    private val matchesFile = File(dir, "matches.json")
    private val inspirationsFile = File(dir, "inspirations.json")
    private val wearLogsFile = File(dir, "wear_logs.json")

    fun readAllClothes(): List<ClothEntity> = readClothes()
    fun listActiveClothes(): List<ClothEntity> = readClothes().filter { it.status != "discarded" }
    fun listDiscardedClothes(): List<ClothEntity> = readClothes().filter { it.status == "discarded" }
    fun getClothById(id: String): ClothEntity? = readClothes().find { it.id == id }

    fun saveClothes(items: List<ClothEntity>) {
        writeArray(clothesFile, items.map { clothToJson(it) })
    }

    fun listMatches(): List<MatchEntity> = readMatches()
    fun getMatchById(id: String): MatchEntity? = readMatches().find { it.id == id }
    fun saveMatches(items: List<MatchEntity>) {
        writeArray(matchesFile, items.map { matchToJson(it) })
    }

    fun listInspirations(): List<InspirationEntity> = readInspirations()
    fun getInspirationById(id: String): InspirationEntity? = readInspirations().find { it.id == id }
    fun saveInspirations(items: List<InspirationEntity>) {
        writeArray(inspirationsFile, items.map { inspToJson(it) })
    }

    fun listWearLogs(): List<WearLogEntity> = readWearLogs()
    fun saveWearLogs(items: List<WearLogEntity>) {
        writeArray(wearLogsFile, items.map { wearLogToJson(it) })
    }

    fun replaceAll(
        clothes: List<ClothEntity>,
        matches: List<MatchEntity>,
        inspirations: List<InspirationEntity>,
        wearLogs: List<WearLogEntity> = readWearLogs()
    ) {
        saveClothes(clothes)
        saveMatches(matches)
        saveInspirations(inspirations)
        saveWearLogs(wearLogs)
    }

    private fun readClothes(): List<ClothEntity> {
        return readArray(clothesFile).mapNotNull { parseCloth(it) }
    }

    private fun parseCloth(o: JSONObject): ClothEntity? {
        val id = o.optString("id", "")
        if (id.isBlank()) return null
        val colors = when {
            o.has("colors") -> JsonHelpers.stringListToJson(
                JsonHelpers.jsonToStringListFromArray(o.optJSONArray("colors"))
            )
            else -> {
                val legacy = o.optString("colorsJson", o.optString("color", "其他"))
                if (legacy.startsWith("[")) legacy
                else JsonHelpers.stringListToJson(JsonHelpers.jsonToStringList(legacy))
            }
        }
        val sizes = when {
            o.has("sizes") && o.opt("sizes") is JSONObject -> o.optJSONObject("sizes")?.toString() ?: "{}"
            else -> o.optString("sizesJson", "{}")
        }
        return ClothEntity(
            id = id,
            name = o.optString("name", "未命名"),
            colorsJson = colors,
            season = o.optString("season", "夏"),
            type = migrateType(o.optString("type", "上衣")),
            status = if (o.optString("status") == "discarded") "discarded" else "active",
            imageRef = o.optString("imageRef", ImageStore.refForClothId(id)),
            note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            tempMin = parseTemp(o, "tempMin"),
            tempMax = parseTemp(o, "tempMax"),
            sizesJson = sizes,
            purchaseDate = o.optString("purchaseDate", ""),
            purchasePrice = o.optString("purchasePrice", ""),
            material = o.optString("material", ""),
            discardedAt = if (o.has("discardedAt") && !o.isNull("discardedAt")) o.optLong("discardedAt") else null,
            colorHexMapJson = when {
                o.has("colorHexMap") -> o.optJSONObject("colorHexMap")?.toString() ?: "{}"
                else -> o.optString("colorHexMapJson", "{}")
            }
        )
    }

    private fun parseTemp(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        val n = o.optDouble(key, Double.NaN)
        return if (n.isNaN()) null else n.toInt()
    }

    private fun migrateType(type: String): String =
        if (type == "外套") "长款" else type

    private fun readMatches(): List<MatchEntity> {
        return readArray(matchesFile).mapNotNull { o ->
            val id = o.optString("id", "")
            if (id.isBlank()) return@mapNotNull null
            val ids = when {
                o.has("clothIds") -> JsonHelpers.stringListToJson(
                    JsonHelpers.jsonToStringListFromArray(o.optJSONArray("clothIds"))
                )
                else -> o.optString("clothIdsJson", "")
            }
            MatchEntity(
                id = id,
                name = o.optString("name", "未命名搭配"),
                clothIdsJson = ids,
                note = o.optString("note", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun readInspirations(): List<InspirationEntity> {
        return readArray(inspirationsFile).mapNotNull { o ->
            val id = o.optString("id", "")
            if (id.isBlank()) return@mapNotNull null
            val colorTags = when {
                o.has("colorTags") -> o.optJSONObject("colorTags")?.toString()
                    ?: """{"primary":[],"secondary":[],"accent":[]}"""
                else -> o.optString("colorTagsJson", """{"primary":[],"secondary":[],"accent":[]}""")
            }
            val links = when {
                o.has("links") -> o.optJSONArray("links")?.toString() ?: "[]"
                else -> o.optString("linksJson", "[]")
            }
            InspirationEntity(
                id = id,
                name = o.optString("name", o.optString("title", "灵感")),
                imageRef = o.optString("imageRef", ImageStore.refForInspirationId(id)),
                note = o.optString("note", ""),
                season = o.optString("season", ""),
                style = o.optString("style", ""),
                occasion = o.optString("occasion", ""),
                colorTagsJson = colorTags,
                linksJson = links,
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun readWearLogs(): List<WearLogEntity> {
        return readArray(wearLogsFile).mapNotNull { o ->
            val id = o.optString("id", "")
            if (id.isBlank()) return@mapNotNull null
            val clothIds = when {
                o.has("clothIds") -> JsonHelpers.stringListToJson(
                    JsonHelpers.jsonToStringListFromArray(o.optJSONArray("clothIds"))
                )
                else -> o.optString("clothIdsJson", "[]")
            }
            WearLogEntity(
                id = id,
                date = o.optString("date", "").take(10),
                type = if (o.optString("type") == "match") "match" else "single",
                clothIdsJson = clothIds,
                matchId = o.optString("matchId").takeIf { it.isNotBlank() },
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    fun clothToJson(c: ClothEntity): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("colors", JSONArray(JsonHelpers.jsonToStringList(c.colorsJson)))
        put("season", c.season)
        put("type", c.type)
        put("status", c.status)
        put("imageRef", c.imageRef)
        put("note", c.note)
        put("createdAt", c.createdAt)
        c.tempMin?.let { put("tempMin", it) } ?: put("tempMin", JSONObject.NULL)
        c.tempMax?.let { put("tempMax", it) } ?: put("tempMax", JSONObject.NULL)
        put("sizes", JSONObject(c.sizesJson.ifBlank { "{}" }))
        put("purchaseDate", c.purchaseDate)
        put("purchasePrice", c.purchasePrice)
        put("material", c.material)
        c.discardedAt?.let { put("discardedAt", it) }
        if (c.colorHexMapJson.isNotBlank() && c.colorHexMapJson != "{}") {
            put("colorHexMap", JSONObject(c.colorHexMapJson))
        }
    }

    fun matchToJson(m: MatchEntity): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("clothIds", JSONArray(JsonHelpers.jsonToStringList(m.clothIdsJson)))
        put("note", m.note)
        put("createdAt", m.createdAt)
    }

    fun inspToJson(i: InspirationEntity): JSONObject = JSONObject().apply {
        put("id", i.id)
        put("name", i.name)
        put("imageRef", i.imageRef)
        put("note", i.note)
        put("season", i.season)
        put("style", i.style)
        put("occasion", i.occasion)
        put("colorTags", JSONObject(i.colorTagsJson.ifBlank { """{"primary":[],"secondary":[],"accent":[]}""" }))
        put("links", JSONArray(i.linksJson.ifBlank { "[]" }))
        put("createdAt", i.createdAt)
    }

    fun wearLogToJson(w: WearLogEntity): JSONObject = JSONObject().apply {
        put("id", w.id)
        put("date", w.date)
        put("type", w.type)
        put("clothIds", JSONArray(JsonHelpers.jsonToStringList(w.clothIdsJson)))
        w.matchId?.let { put("matchId", it) }
        put("createdAt", w.createdAt)
    }

    private fun readArray(file: File): List<JSONObject> {
        if (!file.exists() || file.length() < 2L) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeArray(file: File, objects: List<JSONObject>) {
        val arr = JSONArray()
        objects.forEach { arr.put(it) }
        file.writeText(arr.toString())
    }
}
