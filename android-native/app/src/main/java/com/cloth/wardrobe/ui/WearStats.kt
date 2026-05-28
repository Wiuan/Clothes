package com.cloth.wardrobe.ui

import com.cloth.wardrobe.data.ClothEntity
import com.cloth.wardrobe.data.WearLogEntity
import com.cloth.wardrobe.data.WearLogUtils

data class WearRankRow(
    val cloth: ClothEntity,
    val count: Int,
    val lastDate: String?
)

object WearStats {
    val PERIOD_OPTS = listOf(
        "30d" to "近30天",
        "90d" to "近90天",
        "365d" to "近一年",
        "all" to "全部"
    )

    fun buildRankList(
        clothes: List<ClothEntity>,
        logs: List<WearLogEntity>,
        startMs: Long?,
        endMs: Long,
        season: String = "",
        ascending: Boolean = true
    ): List<WearRankRow> {
        val rows = clothes
            .filter { it.status != "discarded" }
            .filter { season.isEmpty() || it.season == season }
            .map { cloth ->
                WearRankRow(
                    cloth = cloth,
                    count = WearLogUtils.countWears(cloth.id, logs, startMs, endMs),
                    lastDate = WearLogUtils.lastWearDate(cloth.id, logs)
                )
            }
        return if (ascending) {
            rows.sortedWith(compareBy<WearRankRow> { it.count }.thenBy { it.lastDate ?: "" })
        } else {
            rows.sortedWith(
                compareByDescending<WearRankRow> { it.count }
                    .thenByDescending { it.lastDate ?: "" }
            )
        }
    }
}
