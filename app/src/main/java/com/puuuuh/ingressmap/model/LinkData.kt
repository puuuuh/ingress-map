package com.puuuuh.ingressmap.model

import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.puuuuh.ingressmap.utils.toLatLng
import java.lang.reflect.Type

data class LinkData(val team: String, val points: Pair<Point, Point>) {
    val bounds: LatLngBounds = LatLngBounds.builder()
        .include(points.first.toLatLng())
        .include(points.second.toLatLng())
        .build()
}

class LinkDeserializer : JsonDeserializer<LinkData> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): LinkData {
        val entityData = json.asJsonArray
        return LinkData(
            entityData[1].asString, Pair(
                Point(
                    entityData[3].asDouble / 1000000,
                    entityData[4].asDouble / 1000000
                ),
                Point(
                    entityData[6].asDouble / 1000000,
                    entityData[7].asDouble / 1000000
                )
            )
        )
    }
}