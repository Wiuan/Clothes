package com.cloth.wardrobe.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.ImageStore
import com.cloth.wardrobe.data.InspirationEntity
import com.cloth.wardrobe.data.MatchEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val path: String,
    val sizeBytes: Long
)

object ZipBundleExporter {
    const val EXPORT_FILE_NAME = "wardrobe_export.zip"
    private const val BUNDLE_VERSION = 3

    fun export(
        context: Context,
        clothes: List<ClothEntity>,
        matches: List<MatchEntity>,
        inspirations: List<InspirationEntity>,
        wearLogs: List<com.cloth.wardrobe.data.WearLogEntity> = emptyList()
    ): ExportResult {
        val manifest = buildManifest(clothes, matches, inspirations, wearLogs)
        val imageRefs = collectImageRefs(clothes, inspirations)
        val cacheZip = File(context.cacheDir, EXPORT_FILE_NAME)
        if (cacheZip.exists()) cacheZip.delete()
        writeZipFile(cacheZip, manifest, context, imageRefs)
        val size = cacheZip.length()
        if (size < 10) error("ZIP 生成为空")
        val publicPath = publishToDownloads(context, cacheZip)
        return ExportResult(publicPath, size)
    }

    private fun buildManifest(
        clothes: List<ClothEntity>,
        matches: List<MatchEntity>,
        inspirations: List<InspirationEntity>,
        wearLogs: List<com.cloth.wardrobe.data.WearLogEntity>
    ): JSONObject {
        return JSONObject().apply {
            put("version", BUNDLE_VERSION)
            put("format", "zip")
            put("exportedAt", System.currentTimeMillis())
            put("clothes", JSONArray().apply {
                clothes.forEach { put(clothToJson(it)) }
            })
            put("matches", JSONArray().apply {
                matches.forEach { put(matchToJson(it)) }
            })
            put("inspirations", JSONArray().apply {
                inspirations.forEach { put(inspToJson(it)) }
            })
            put("wearLogs", JSONArray().apply {
                wearLogs.forEach { put(wearToJson(it)) }
            })
        }
    }

    private fun clothToJson(c: ClothEntity): JSONObject {
        return JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("colors", org.json.JSONArray(com.cloth.wardrobe.data.JsonHelpers.jsonToStringList(c.colorsJson)))
            put("season", c.season)
            put("type", c.type)
            put("status", c.status)
            put("imageRef", c.imageRef)
            put("note", c.note)
            put("createdAt", c.createdAt)
            c.tempMin?.let { put("tempMin", it) }
            c.tempMax?.let { put("tempMax", it) }
            put("sizes", JSONObject(c.sizesJson.ifBlank { "{}" }))
            put("purchaseDate", c.purchaseDate)
            put("purchasePrice", c.purchasePrice)
            put("material", c.material)
            c.discardedAt?.let { put("discardedAt", it) }
            if (c.colorHexMapJson.isNotBlank() && c.colorHexMapJson != "{}") {
                put("colorHexMap", JSONObject(c.colorHexMapJson))
            }
        }
    }

    private fun matchToJson(m: MatchEntity): JSONObject {
        return JSONObject().apply {
            put("id", m.id)
            put("name", m.name)
            put("clothIds", org.json.JSONArray(com.cloth.wardrobe.data.JsonHelpers.jsonToStringList(m.clothIdsJson)))
            put("note", m.note)
            put("createdAt", m.createdAt)
        }
    }

    private fun inspToJson(i: InspirationEntity): JSONObject {
        return JSONObject().apply {
            put("id", i.id)
            put("name", i.name)
            put("imageRef", i.imageRef)
            put("note", i.note)
            put("season", i.season)
            put("style", i.style)
            put("occasion", i.occasion)
            put("colorTags", JSONObject(i.colorTagsJson.ifBlank { """{"primary":[],"secondary":[],"accent":[]}""" }))
            put("links", org.json.JSONArray(i.linksJson.ifBlank { "[]" }))
            put("createdAt", i.createdAt)
        }
    }

    private fun wearToJson(w: com.cloth.wardrobe.data.WearLogEntity): JSONObject {
        return JSONObject().apply {
            put("id", w.id)
            put("date", w.date)
            put("type", w.type)
            put("clothIds", org.json.JSONArray(com.cloth.wardrobe.data.JsonHelpers.jsonToStringList(w.clothIdsJson)))
            w.matchId?.let { put("matchId", it) }
            put("createdAt", w.createdAt)
        }
    }

    private fun collectImageRefs(
        clothes: List<ClothEntity>,
        inspirations: List<InspirationEntity>
    ): List<String> {
        val refs = linkedSetOf<String>()
        clothes.forEach { refs.add(it.imageRef) }
        inspirations.forEach { refs.add(it.imageRef) }
        return refs.toList()
    }

    private fun writeZipFile(
        outFile: File,
        manifest: JSONObject,
        context: Context,
        imageRefs: List<String>
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            val bytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
            zos.write(bytes)
            zos.closeEntry()
            for (ref in imageRefs) {
                val img = ImageStore.fileForRef(context, ref)
                if (!img.exists() || img.length() < 1L) continue
                zos.putNextEntry(ZipEntry(ImageStore.zipImagePath(ref)))
                img.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun publishToDownloads(context: Context, cacheZip: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(EXPORT_FILE_NAME)
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, EXPORT_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建 Download 条目")
            resolver.openOutputStream(uri)?.use { out ->
                cacheZip.inputStream().use { it.copyTo(out) }
            } ?: error("无法写入 Download")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val dest = File(dir, EXPORT_FILE_NAME)
        if (!dest.exists() || dest.length() < cacheZip.length()) {
            cacheZip.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest.absolutePath
    }
}
