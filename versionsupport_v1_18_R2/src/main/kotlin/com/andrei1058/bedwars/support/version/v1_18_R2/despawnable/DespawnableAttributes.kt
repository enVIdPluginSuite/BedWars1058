package com.andrei1058.bedwars.support.version.v1_18_R2.despawnable

data class DespawnableAttributes(
    val type: DespawnableType,
    val speed: Double,
    val health: Double,
    val damage: Double,
    val despawnSeconds: Int
)
