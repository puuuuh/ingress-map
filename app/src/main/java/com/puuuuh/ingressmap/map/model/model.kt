package com.puuuuh.ingressmap.map.model

import com.puuuuh.ingressmap.map.viewmodel.UserData
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import java.net.URL
import kotlin.math.*

interface OnDataReadyCallback {
    fun onDataReady(data: List<Entity>)
}

data class GetEntitiesPayload(val tileKeys: List<String>) {
    val v = "f0d1685cfbbd243ac6688645056a6ed2c642cea5"
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

data class Entity (val guid: String, val raw: ArrayList<Any>) {
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

    fun tryLoad(userData: UserData, tiles: List<String>, callback: OnDataReadyCallback, retry: Int = 0) {
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
                tryLoad(userData, tiles, callback, retry+1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200)
                    return
                val data = mutableListOf<Entity>()
                val next = mutableListOf<String>()
                val json = response.body()?.string()
                val res = g.fromJson(json, GetEntitiesResponse::class.java)
                for (es in res.result.map) {
                    if (es.value.gameEntities == null) {
                        next.add(es.key)
                        continue
                    }
                    for (e in es.value.gameEntities!!) {
                        val entityData = e[2] as ArrayList<Any>
                        if ((entityData[0] as String) == "p") {
                            data.add(Entity(e[0] as String, entityData))
                        }
                    }
                }
                if (next.isNotEmpty()) {
                    tryLoad(userData, next, callback, retry+1)
                }

                callback.onDataReady(data)
            }
        })
    }

    fun refreshData(userData: UserData, region: LatLngBounds, zoom: Int, callback: OnDataReadyCallback) {
        val zoomToLevel = arrayOf(8,8,8,8,7,7,7,6,6,5,4,4,3,2,2,1,1)
        val level = if(zoom >= zoomToLevel.size) { 0 } else { zoomToLevel[zoom] }
        val ne = getXYTile(region.northeast.latitude, region.northeast.longitude, zoom)
        val sw = getXYTile(region.southwest.latitude, region.southwest.longitude, zoom)
        var tiles = mutableListOf<String>()
        for (y in ne.second..sw.second) {
            for (x in sw.first..ne.first) {
                tiles.add("${zoom}_${x}_${y}_${level}_8_100")
                if (tiles.size == 29) {
                    tryLoad(userData, tiles, callback)
                    tiles = mutableListOf()
                }
            }
        }
        if (tiles.isNotEmpty()) {
            tryLoad(userData, tiles, callback)
        }
    }
}