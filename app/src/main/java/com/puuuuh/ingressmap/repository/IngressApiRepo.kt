package com.puuuuh.ingressmap.repository

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.puuuuh.ingressmap.settings.Settings
import okhttp3.*
import java.io.IOException
import java.net.URL

interface OnDataReadyCallback {
    fun onCellDataReceived(cellId: String, portal: Map<String, Portal>,
                           links: Map<String, Link>,
                           fields: Map<String, Field>)
}

interface OnPortalExReadyCallback {
    fun OnPortalExReady(portal: Portal)
}

data class GetEntitiesPayload(val tileKeys: List<String>) {
    val v = Settings.apiVersion
}

data class GetPortalDataPayload(val guid: String) {
    val v = Settings.apiVersion
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

data class GetPortalDataResponse (
    val result: ArrayList<Any>
)

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
            )
            ),
            Point(raw[5] as String, LatLng(
                raw[6] as Double / 1000000,
                raw[7] as Double / 1000000
            )
            )
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
            )
            ),
            Point(pointsRaw[1][0] as String, LatLng(
                pointsRaw[1][1] as Double / 1000000,
                pointsRaw[1][2] as Double / 1000000
            )
            ),
            Point(pointsRaw[2][0] as String, LatLng(
                pointsRaw[2][1] as Double / 1000000,
                pointsRaw[2][2] as Double / 1000000
            )
            )
        )
        bounds = LatLngBounds.builder()
            .include(points[0].LatLng)
            .include(points[1].LatLng)
            .include(points[2].LatLng)
            .build()
    }
}

data class Mod(val raw: ArrayList<Any>) {
    var owner = ""
    var name = ""
    var level = ""
    var effects = mapOf<String, String>()
    init {
        owner = raw[0] as String
        name = raw[1] as String
        level = raw[2] as String
        if (raw[3] is LinkedTreeMap<*, *>)
            effects = (raw[3] as LinkedTreeMap<String, String>).toMap()
    }
}

data class Resonator(val raw: ArrayList<Any>) {
    var owner = ""
    var level = 0
    var energy = 0
    init {
        owner = raw[0] as String
        level = (raw[1] as Double).toInt()
        energy = (raw[2] as Double).toInt()
    }
}

data class Portal(val guid: String, val raw: ArrayList<Any>) {
    var lat = 0f.toDouble()
    var lng = 0f.toDouble()
    var lvl = 0
    var energy = 0
    var pic = ""
    var name = ""
    var team = ""
    var mods = mutableListOf<Mod>()
    var resonators = mutableListOf<Resonator>()
    var owner = ""
    init {
        team = raw[1] as String
        lat = raw[2] as Double / 1000000
        lng = raw[3] as Double / 1000000
        lvl = (raw[4] as Double).toInt()
        energy = (raw[5] as Double).toInt()

        pic = if (raw[7] is String) {
            raw[7] as String
        } else {
            ""
        }
        name = raw[8] as String
        if (raw.size > 14) {
            val rawModArr = raw[14] as ArrayList<Any>
            mods = mutableListOf()
            for (rawMod in rawModArr) {
                if (rawMod is ArrayList<*>)
                    mods.add(Mod(rawMod as ArrayList<Any>))
            }
            val rawModResonator = raw[15] as ArrayList<Any>
            resonators = mutableListOf()
            for (rawResonator in rawModResonator) {
                if (rawResonator is ArrayList<*>)
                    resonators.add(Resonator(rawResonator as ArrayList<Any>))
            }
            owner = raw[16] as String
        }
    }
}


class IngressApiRepo(val context: Context) {
    var okHttpClient: OkHttpClient = OkHttpClient()

    init {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .build()
    }

    private fun apiCall(method: String, payload: String, cb: Callback) {
        val r = Request.Builder()
            .url(URL("https://intel.ingress.com/r/$method"))
            .post(RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), payload))
            .build()
        okHttpClient.newCall(r).enqueue(cb)
    }

    fun getExtendedPortalData(guid: String, callback: OnPortalExReadyCallback, retry: Int = 0) {
        if (retry > 1) {
            return
        }
        val g = Gson()

        val payload = g.toJson(GetPortalDataPayload(guid))

        apiCall("getPortalDetails", payload, object: Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getExtendedPortalData(guid, callback, retry+1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200 || (response.body()?.contentType()?.type() != "application" ||
                        response.body()?.contentType()?.subtype() != "json"))
                    return
                try {
                    val json = response.body()?.string()
                    val res = g.fromJson(json, GetPortalDataResponse::class.java)
                    val portalEx = Portal(guid, res.result)
                    callback.OnPortalExReady(portalEx)
                } catch (e: Exception) {
                    print(e.message)
                }
            }
        })
    }

    fun getTilesInfo( tiles: List<String>, callback: OnDataReadyCallback, retry: Int = 0) {
        if (retry > 1) {
            return
        }
        val g = Gson()

        val payload = g.toJson(GetEntitiesPayload(tiles))

        apiCall("getEntities", payload, object: Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getTilesInfo(tiles, callback, retry+1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200)
                    return
                try {
                    val next = mutableListOf<String>()
                    val json = response.body()?.string()
                    val res = g.fromJson(json, GetEntitiesResponse::class.java)
                    for (es in res.result.map) {
                        if (es.value.gameEntities == null) {
                            next.add(es.key)
                            continue
                        }
                        val portals = mutableMapOf<String, Portal>()
                        val links = mutableMapOf<String, Link>()
                        val fields = mutableMapOf<String, Field>()
                        for (e in es.value.gameEntities!!) {
                            val entityData = e[2] as ArrayList<Any>
                            when (entityData[0] as String) {
                                "p" -> {
                                    portals[e[0] as String] = Portal(e[0] as String, entityData)
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
                        getTilesInfo(next, callback, retry+1)
                    }
                } catch (e: Exception) {
                    print(e.message)
                }

            }
        })
    }
}