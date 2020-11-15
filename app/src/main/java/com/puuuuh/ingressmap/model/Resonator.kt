package com.puuuuh.ingressmap.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class Resonator(
    val owner: String,
    val level: Int,
    val energy: Int,
)

class ResonatorDeserializer : JsonDeserializer<Resonator> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Resonator {
        val src = json.asJsonArray
        val owner = src[0].asString
        val level = src[1].asInt
        val energy = src[2].asInt
        return Resonator(owner, level, energy)
    }

}