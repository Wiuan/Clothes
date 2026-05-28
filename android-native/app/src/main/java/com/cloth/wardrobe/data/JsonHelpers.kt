package com.cloth.wardrobe.data

import org.json.JSONArray
import org.json.JSONObject

object JsonHelpers {
    fun stringListToJson(list: List<String>): String {
        val arr = JSONArray()
        list.filter { it.isNotBlank() }.forEach { arr.put(it) }
        return arr.toString()
    }

    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val t = json.trim()
        if (t.startsWith("[")) {
            return try {
                val arr = JSONArray(t)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (t.contains(",")) return t.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (t.contains("·")) return t.split("·").map { it.trim() }.filter { it.isNotEmpty() }
        return listOf(t)
    }

    fun jsonToStringListFromArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    fun parseSizes(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val raw = json.trim()
        if (raw == "{}") return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { k -> o.optString(k, "").trim() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun sizesToJson(map: Map<String, String>): String {
        val o = JSONObject()
        map.filter { it.value.isNotBlank() }.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun colorTagsToJson(primary: List<String>, secondary: List<String>, accent: List<String>): String =
        JSONObject().apply {
            put("primary", JSONArray(primary))
            put("secondary", JSONArray(secondary))
            put("accent", JSONArray(accent))
        }.toString()

    fun linksToJson(linkMap: Map<String, String>): String {
        val arr = JSONArray()
        linkMap.forEach { (clothId, relation) ->
            arr.put(
                JSONObject().apply {
                    put("clothId", clothId)
                    put(
                        "relation",
                        if (relation == "want_to_buy") "want_to_buy" else "have_similar"
                    )
                }
            )
        }
        return arr.toString()
    }
}
