package com.puuuuh.ingressmap.model

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class FieldData(val team: String, val points: Triple<Point, Point, Point>) {
    val bounds: LatLngBounds = LatLngBounds.builder()
        .include(LatLng(points.first.lat, points.first.lng))
        .include(LatLng(points.second.lat, points.second.lng))
        .include(LatLng(points.third.lat, points.third.lng))
        .build()
}

class FieldDeserializer : JsonDeserializer<FieldData> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): FieldData {
        val entityData = json.asJsonArray

        val team = entityData[1].asString
        val points = context.deserialize<Array<Point>>(entityData[2], Array<Point>::class.java)
        return FieldData(team, Triple(points[0], points[1], points[2]))
    }
}