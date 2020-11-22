package com.puuuuh.ingressmap.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class Mod(
    val owner: String,
    val name: String,
    val level: String,
    val effects: Map<String, String>
)

class ModDeserializer : JsonDeserializer<Mod> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Mod {
        val src = json.asJsonArray
        val owner = src[0].asString
        val name = src[1].asString
        val level = src[2].asString
        val effects = mutableMapOf<String, String>()
        src[3].asJsonObject.asJsonObject.entrySet().forEach {
            effects[it.key] = it.value.asString
        }
        return Mod(owner, name, level, effects)
    }
}