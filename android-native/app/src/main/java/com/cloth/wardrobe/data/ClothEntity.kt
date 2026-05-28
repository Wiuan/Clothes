package com.cloth.wardrobe.data

data class ClothEntity(
    val id: String,
    val name: String,
    val colorsJson: String,
    val season: String,
    val type: String,
    val status: String = "active",
    val imageRef: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tempMin: Int? = null,
    val tempMax: Int? = null,
    val sizesJson: String = "{}",
    val purchaseDate: String = "",
    val purchasePrice: String = "",
    val material: String = "",
    val discardedAt: Long? = null,
    val colorHexMapJson: String = "{}"
)

data class MatchEntity(
    val id: String,
    val name: String,
    val clothIdsJson: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class InspirationEntity(
    val id: String,
    val name: String,
    val imageRef: String,
    val note: String = "",
    val season: String = "",
    val style: String = "",
    val occasion: String = "",
    val colorTagsJson: String = """{"primary":[],"secondary":[],"accent":[]}""",
    val linksJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

data class WearLogEntity(
    val id: String,
    val date: String,
    val type: String,
    val clothIdsJson: String,
    val matchId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
