package com.cloth.wardrobe.data

import android.content.Context
import java.io.File

object ImageStore {
    private fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    fun fileForRef(context: Context, ref: String): File {
        val safe = ref.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir(context), "$safe.jpg")
    }

    fun refForClothId(id: String) = "img_$id"

    fun refForInspirationId(id: String) = "insp_$id"

    fun zipImageName(ref: String): String {
        val safe = ref.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "$safe.jpg"
    }

    fun zipImagePath(ref: String) = "images/${zipImageName(ref)}"
}
