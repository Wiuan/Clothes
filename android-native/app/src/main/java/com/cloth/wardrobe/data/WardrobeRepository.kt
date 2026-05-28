package com.cloth.wardrobe.data

import android.content.Context
import android.net.Uri
import com.cloth.wardrobe.export.ImportResult
import com.cloth.wardrobe.export.ZipBundleExporter
import com.cloth.wardrobe.export.ZipBundleImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class WardrobeRepository(private val context: Context, private val store: WardrobeStore) {

    suspend fun listClothes(): List<ClothEntity> = withContext(Dispatchers.IO) {
        store.listActiveClothes()
    }

    suspend fun listAllClothes(): List<ClothEntity> = withContext(Dispatchers.IO) {
        store.readAllClothes()
    }

    suspend fun listDiscarded(): List<ClothEntity> = withContext(Dispatchers.IO) {
        store.listDiscardedClothes()
    }

    suspend fun getCloth(id: String): ClothEntity? = withContext(Dispatchers.IO) {
        store.getClothById(id)
    }

    /** 详情左右滑动：与列表默认一致，按创建时间倒序 */
    suspend fun browseClothIds(anchorId: String): List<String> = withContext(Dispatchers.IO) {
        val anchor = store.getClothById(anchorId) ?: return@withContext emptyList()
        val list = if (anchor.status == "discarded") store.listDiscardedClothes()
        else store.listActiveClothes()
        list.sortedByDescending { it.createdAt }.map { it.id }
    }

    suspend fun browseInspirationIds(): List<String> = withContext(Dispatchers.IO) {
        store.listInspirations().sortedByDescending { it.createdAt }.map { it.id }
    }

    suspend fun saveCloth(item: ClothEntity) = withContext(Dispatchers.IO) {
        val list = store.readAllClothes()
        val idx = list.indexOfFirst { it.id == item.id }
        val next = if (idx >= 0) {
            list.toMutableList().apply { set(idx, item) }
        } else {
            (listOf(item) + list)
        }
        store.saveClothes(next)
    }

    suspend fun addCloth(item: ClothEntity) = saveCloth(item)

    suspend fun removeCloth(id: String) = withContext(Dispatchers.IO) {
        ImageStore.fileForRef(context, ImageStore.refForClothId(id)).delete()
        removeClothFromMatches(id)
        store.saveClothes(store.readAllClothes().filter { it.id != id })
    }

    suspend fun setClothStatus(id: String, discarded: Boolean) = withContext(Dispatchers.IO) {
        val list = store.readAllClothes().map { c ->
            if (c.id != id) c
            else c.copy(
                status = if (discarded) "discarded" else "active",
                discardedAt = if (discarded) System.currentTimeMillis() else null
            )
        }
        store.saveClothes(list)
    }

    suspend fun batchUpdateClothes(ids: Set<String>, updater: (ClothEntity) -> ClothEntity) =
        withContext(Dispatchers.IO) {
            val idSet = ids.toSet()
            store.saveClothes(
                store.readAllClothes().map { c ->
                    if (idSet.contains(c.id)) updater(c) else c
                }
            )
        }

    suspend fun batchRemoveClothes(ids: Set<String>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            ImageStore.fileForRef(context, ImageStore.refForClothId(id)).delete()
        }
        removeClothFromMatches(ids)
        store.saveClothes(store.readAllClothes().filter { it.id !in ids })
    }

    suspend fun listMatches(): List<MatchEntity> = withContext(Dispatchers.IO) {
        store.listMatches()
    }

    suspend fun getMatch(id: String): MatchEntity? = withContext(Dispatchers.IO) {
        store.getMatchById(id)
    }

    suspend fun saveMatch(item: MatchEntity) = withContext(Dispatchers.IO) {
        val list = store.listMatches()
        val idx = list.indexOfFirst { it.id == item.id }
        val next = if (idx >= 0) list.toMutableList().apply { set(idx, item) }
        else listOf(item) + list
        store.saveMatches(next)
    }

    suspend fun removeMatch(id: String) = withContext(Dispatchers.IO) {
        store.saveMatches(store.listMatches().filter { it.id != id })
    }

    suspend fun removeClothFromMatches(clothId: String) = withContext(Dispatchers.IO) {
        removeClothFromMatches(setOf(clothId))
    }

    private fun removeClothFromMatches(clothIds: Set<String>) {
        val next = store.listMatches()
            .map { m ->
                val ids = JsonHelpers.jsonToStringList(m.clothIdsJson).filter { it !in clothIds }
                m.copy(clothIdsJson = JsonHelpers.stringListToJson(ids))
            }
            .filter { JsonHelpers.jsonToStringList(it.clothIdsJson).isNotEmpty() }
        store.saveMatches(next)
    }

    suspend fun listInspirations(): List<InspirationEntity> = withContext(Dispatchers.IO) {
        store.listInspirations()
    }

    suspend fun getInspiration(id: String): InspirationEntity? = withContext(Dispatchers.IO) {
        store.getInspirationById(id)
    }

    suspend fun saveInspiration(item: InspirationEntity) = withContext(Dispatchers.IO) {
        val list = store.listInspirations()
        val idx = list.indexOfFirst { it.id == item.id }
        val next = if (idx >= 0) list.toMutableList().apply { set(idx, item) }
        else listOf(item) + list
        store.saveInspirations(next)
    }

    suspend fun removeInspiration(id: String) = withContext(Dispatchers.IO) {
        ImageStore.fileForRef(context, ImageStore.refForInspirationId(id)).delete()
        store.saveInspirations(store.listInspirations().filter { it.id != id })
    }

    suspend fun listWearLogs(): List<WearLogEntity> = withContext(Dispatchers.IO) {
        store.listWearLogs()
    }

    suspend fun addWearLog(
        clothIds: List<String>,
        type: String = "single",
        matchId: String? = null,
        date: String = WearLogUtils.todayDateStr()
    ) = withContext(Dispatchers.IO) {
        val ids = clothIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return@withContext null
        val log = WearLogEntity(
            id = "wear_${System.currentTimeMillis()}",
            date = date,
            type = if (type == "match") "match" else "single",
            clothIdsJson = JsonHelpers.stringListToJson(ids),
            matchId = matchId
        )
        store.saveWearLogs(listOf(log) + store.listWearLogs())
        log
    }

    suspend fun removeWearLog(id: String) = withContext(Dispatchers.IO) {
        store.saveWearLogs(store.listWearLogs().filter { it.id != id })
    }

    suspend fun seedDemoIfEmpty() = withContext(Dispatchers.IO) {
        if (store.listActiveClothes().isNotEmpty()) return@withContext
        val id = UUID.randomUUID().toString()
        store.saveClothes(
            listOf(
                ClothEntity(
                    id = id,
                    name = "示例白衬衫",
                    colorsJson = JsonHelpers.stringListToJson(listOf("白")),
                    season = "夏",
                    type = "上衣",
                    imageRef = ImageStore.refForClothId(id),
                    note = "原生 App 示例，可删除"
                )
            )
        )
    }

    suspend fun exportZip() = withContext(Dispatchers.IO) {
        ZipBundleExporter.export(
            context,
            store.readAllClothes(),
            store.listMatches(),
            store.listInspirations(),
            store.listWearLogs()
        )
    }

    suspend fun importZip(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bundle = ZipBundleImporter.importFromUri(context, uri)
        store.replaceAll(bundle.clothes, bundle.matches, bundle.inspirations, bundle.wearLogs)
        ImportResult(
            clothCount = bundle.clothes.size,
            matchCount = bundle.matches.size,
            inspirationCount = bundle.inspirations.size,
            wearLogCount = bundle.wearLogs.size
        )
    }

    suspend fun saveImageFromUri(ref: String, uri: Uri) = withContext(Dispatchers.IO) {
        val dest = ImageStore.fileForRef(context, ref)
        ImageCompressor.compressUriToJpegFile(context, uri, dest)
    }

    /** 选图阶段已压缩到缓存文件时，保存直接复制，避免重复解码。 */
    suspend fun saveImageFromFile(ref: String, source: File) = withContext(Dispatchers.IO) {
        val dest = ImageStore.fileForRef(context, ref)
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
    }
}
