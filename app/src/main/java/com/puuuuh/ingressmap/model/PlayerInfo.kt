package com.puuuuh.ingressmap.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class PlayerInfo(
    val ap: Int,
    val available_invites: Int,
    val energy: Int,
    val min_ap_for_current_level: Int,
    val min_ap_for_next_level: Int,
    val nickname: String,
    val team: String,
    val verified_level: Int,
    val xm_capacity: Int
)

@Suppress("NAME_SHADOWING")
class PlayerInfoDeserializer : JsonDeserializer<PlayerInfo> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): PlayerInfo {
        val json = json.asJsonObject
        val ap = json.get("ap").asString.toIntOrNull() ?: 0
        val availableInvites = json.get("available_invites").asString.toIntOrNull() ?: 0
        val energy = json.get("energy").asInt
        val minApForCurrentLevel = json.get("min_ap_for_current_level").asString.toIntOrNull() ?: 0
        val minApForNextLevel = json.get("min_ap_for_next_level").asString.toIntOrNull() ?: 0
        val nickname = json.get("nickname").asString
        val team = json.get("team").asString
        val verifiedLevel = json.get("verified_level").asInt
        val xmCapacity = json.get("xm_capacity").asString.toIntOrNull() ?: 0
        return PlayerInfo(
            ap,
            availableInvites,
            energy,
            minApForCurrentLevel,
            minApForNextLevel,
            nickname,
            team,
            verifiedLevel,
            xmCapacity
        )
    }
}