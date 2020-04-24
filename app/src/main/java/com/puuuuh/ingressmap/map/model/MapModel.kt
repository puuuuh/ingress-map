package com.puuuuh.ingressmap.map.model

import com.google.android.gms.maps.model.LatLng
import com.puuuuh.ingressmap.map.viewmodel.UserData
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import java.net.URL
import kotlin.math.*

interface OnDataReadyCallback {
    fun onCellDataReceived(cellId: String, portal: Map<String, Entity>,
                           links: Map<String, Link>,
                           fields: Map<String, Field>)
    fun onAuthNeeded()
}

data class GetEntitiesPayload(val tileKeys: List<String>) {
    val v = "e84906a74ad12f19e8c0f930b566de42a5ec44f9"
}

data class GetEntitiesResponse (
    val result: EntitiesMap
) {
    data class EntitiesMap(
        val map: Map<String, Entities>
    ) {
        data class Entities (
            val gameEntities: Array<Array<Any>>?
        )
    }
}

data class Entity (val raw: ArrayList<Any>) {
    var lat = 0f.toDouble()
    var lng = 0f.toDouble()
    var pic = ""
    var name = ""
    var team = ""
    init {
        team = raw[1] as String
        lat = raw[2] as Double / 1000000
        lng = raw[3] as Double / 1000000

        pic = if (raw[7] is String) {
            raw[7] as String
        } else {
            ""
        }
        name = raw[8] as String
    }
}

data class Link(val raw: ArrayList<Any>) {
    data class Point(val guid: String, val LatLng: LatLng)

    var team = ""
    var points = arrayOf<Point>()
    var bounds = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0));

    init {
        team = raw[1] as String
        points = arrayOf(
            Point(raw[2] as String, LatLng(
                raw[3] as Double / 1000000,
                raw[4] as Double / 1000000
            )),
            Point(raw[5] as String, LatLng(
                raw[6] as Double / 1000000,
                raw[7] as Double / 1000000
            ))
        )
        bounds = LatLngBounds.builder()
            .include(points[0].LatLng)
            .include(points[1].LatLng)
            .build()

    }
}

data class Field(val raw: ArrayList<Any>) {
    data class Point(val guid: String, val LatLng: LatLng)

    var team = ""
    var points = arrayOf<Point>()
    var bounds = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0));

    init {
        team = raw[1] as String
        val pointsRaw = raw[2] as ArrayList<ArrayList<Any>>
        points = arrayOf(
            Point(pointsRaw[0][0] as String, LatLng(
                pointsRaw[0][1] as Double / 1000000,
                pointsRaw[0][2] as Double / 1000000
            )),
            Point(pointsRaw[1][0] as String, LatLng(
                pointsRaw[1][1] as Double / 1000000,
                pointsRaw[1][2] as Double / 1000000
            )),
            Point(pointsRaw[2][0] as String, LatLng(
                pointsRaw[2][1] as Double / 1000000,
                pointsRaw[2][2] as Double / 1000000
            ))
        )
        bounds = LatLngBounds.builder()
            .include(points[0].LatLng)
            .include(points[1].LatLng)
            .include(points[2].LatLng)
            .build()
    }
}

class MapModel {
    var okHttpClient: OkHttpClient = OkHttpClient()

    private fun getXYTile(lat : Double, lng: Double, zoom : Int) : Pair<Int, Int> {
        val tileCounts = arrayOf(1,1,1,40,40,80,80,320,1000,2000,2000,4000,8000,16000,16000,32000)
        val latRad = Math.toRadians(lat)

        val cnt = if (zoom >= tileCounts.size) {tileCounts[tileCounts.size - 1]} else {tileCounts[zoom]}
        val x = (((lng + 180.0) / 360.0) * cnt).toInt()
        val y = ((1.0 - log(tan(latRad) + (1 / cos(latRad)), E) / PI) / 2.0 * cnt).toInt()
        return Pair(x, y)
    }

    fun tryLoadData(userData: UserData, tiles: List<String>, callback: OnDataReadyCallback, retry: Int = 0) {
        if (retry > 1) {
            return
        }
        val g = Gson()

        val payload = g.toJson(GetEntitiesPayload(tiles))

        val r = Request.Builder()
            .url(URL("https://intel.ingress.com/r/getEntities"))
            .post(RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), payload))
            .addHeader("cookie", "csrftoken=${userData.csrfToken}; sessionid=${userData.token};")
            .addHeader("x-csrftoken", userData.csrfToken)
            .addHeader("referer", "https://intel.ingress.com/")
            .build()
        okHttpClient.newCall(r).enqueue(object: Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                tryLoadData(userData, tiles, callback, retry+1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200)
                    return
                if (response.code() == 403){
                    return callback.onAuthNeeded()
                }
                val next = mutableListOf<String>()
                val json = response.body()?.string()
                val res = g.fromJson(json, GetEntitiesResponse::class.java)
                for (es in res.result.map) {
                    if (es.value.gameEntities == null) {
                        next.add(es.key)
                        continue
                    }
                    val portals = mutableMapOf<String, Entity>()
                    val links = mutableMapOf<String, Link>()
                    val fields = mutableMapOf<String, Field>()
                    for (e in es.value.gameEntities!!) {
                        val entityData = e[2] as ArrayList<Any>
                        when (entityData[0] as String) {
                            "p" -> {
                                portals[e[0] as String] = Entity(entityData)
                            }
                            "e" -> {
                                links[e[0] as String] = Link(entityData)
                            }
                            "r" -> {
                                fields[e[0] as String] = Field(entityData)
                            }
                        }
                    }
                    callback.onCellDataReceived(es.key, portals, links, fields)
                }
                if (next.isNotEmpty()) {
                    tryLoadData(userData, next, callback, retry+1)
                }
            }
        })
    }

    fun refreshData(userData: UserData, region: LatLngBounds, zoom: Int, callback: OnDataReadyCallback) {
        val zoomToLevel = arrayOf(8,8,8,8,7,7,7,6,6,5,4,4,3,2,2,1,1)
        val level = if(zoom >= zoomToLevel.size) { 0 } else { zoomToLevel[zoom] }
        val ne = getXYTile(region.northeast.latitude, region.northeast.longitude, zoom)
        val sw = getXYTile(region.southwest.latitude, region.southwest.longitude, zoom)
        val tiles = mutableListOf<String>()
        for (y in ne.second..sw.second) {
            for (x in sw.first..ne.first) {
                tiles.add("${zoom}_${x}_${y}_${level}_8_100")
            }
        }
        tiles.withIndex()
            .groupBy { it.index / 30 }
            .map { tryLoadData(userData, tiles, callback) }
    }

    fun updateCells(zoom: Int, bounds: LatLngBounds, callback: OnCellsReadyCallback) {
        CalcCells(callback).execute(TaskArg(zoom, bounds))
    }
}