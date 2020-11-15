package com.puuuuh.ingressmap.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

sealed class GameEntity(var id: String) {
    class Portal(id: String, val data: PortalData) : GameEntity(id)
    class Link(id: String, val data: LinkData) : GameEntity(id)
    class Field(id: String, val data: FieldData) : GameEntity(id)
}

class GameEntityDeserializer : JsonDeserializer<GameEntity> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): GameEntity {
        val src = json.asJsonArray
        val id = src[0].asString
        val entityData = src[2].asJsonArray
        return when (entityData[0].asString) {
            "p" -> {
                (GameEntity::Portal)(
                    id,
                    context.deserialize<PortalData>(entityData, PortalData::class.java)
                )
            }
            "e" -> {
                (GameEntity::Link)(
                    id,
                    context.deserialize<LinkData>(entityData, LinkData::class.java)
                )
            }
            "r" -> {
                (GameEntity::Field)(
                    id,
                    context.deserialize<FieldData>(entityData, FieldData::class.java)
                )
            }
            else -> throw JsonParseException("Invalid game entity")
        }
    }

}