package com.puuuuh.ingressmap.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class Point(val lat: Double, val lng: Double)

class PointDeserializer : JsonDeserializer<Point> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Point {
        val src = json.asJsonArray
        val lat = src[1].asDouble / 1000000
        val lng = src[2].asDouble / 1000000

        return Point(lat, lng)
    }
}