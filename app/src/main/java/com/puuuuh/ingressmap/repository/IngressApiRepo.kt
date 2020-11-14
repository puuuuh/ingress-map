package com.puuuuh.ingressmap.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.puuuuh.ingressmap.settings.Settings
import okhttp3.*
import java.io.IOException
import java.net.URL

interface OnDataReadyCallback {
    fun onCellDataReceived(
        cellId: String, portals: Map<String, Portal>,
        links: Map<String, Link>,
        fields: Map<String, Field>
    )

    fun onRequestStart()
    fun onRequestEnd()
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

data class Point(val LatLng: LatLng) {
    constructor(raw: ArrayList<Any>) : this(
        LatLng(
            raw[1] as Double / 1000000,
            raw[2] as Double / 1000000
        )
    )
}

data class Link(val id: String, val team: String, val points: Array<Point>) {
    var bounds = LatLngBounds.builder()
        .include(points[0].LatLng)
        .include(points[1].LatLng)
        .build()

    constructor(id: String, raw: ArrayList<Any>) : this(
        id, raw[1] as String, arrayOf(
            Point(raw.slice(IntRange(2, 4)) as ArrayList<Any>),
            Point(raw.slice(IntRange(5, 7)) as ArrayList<Any>)
        )
    )
}

data class Field(val id: String, val team: String, val points: Array<Point>) {
    val bounds: LatLngBounds = LatLngBounds.builder()
        .include(points[0].LatLng)
        .include(points[1].LatLng)
        .include(points[2].LatLng)
        .build()

    constructor(id: String, raw: ArrayList<Any>) : this(
        id, raw[1] as String, arrayOf(
            Point((raw[2] as ArrayList<ArrayList<Any>>)[0]),
            Point((raw[2] as ArrayList<ArrayList<Any>>)[1]),
            Point((raw[2] as ArrayList<ArrayList<Any>>)[2])
        )
    )
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

data class Portal(
    var guid: String,
    var lat: Double,
    var lng: Double,
    var lvl: Int,
    var energy: Int,
    var pic: String,
    var name: String,
    var team: String,
    var mods: MutableList<Mod>,
    var resonators: MutableList<Resonator>,
    var owner: String,
) {


    constructor() : this(
        "",
        0f.toDouble(),
        0f.toDouble(),
        0,
        0,
        "",
        "",
        "",
        mutableListOf<Mod>(),
        mutableListOf<Resonator>(),
        ""
    )

    constructor(guid: String, name: String, lat: Double, lng: Double) : this() {
        this.guid = guid
        this.name = name
        this.lat = lat
        this.lng = lng
    }

    constructor(guid: String, raw: ArrayList<Any>) : this() {
        this.guid = guid
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


class IngressApiRepo {
    var okHttpClient: OkHttpClient = OkHttpClient()

    init {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
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
        if (retry > 2) {
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
        if (retry > 2) {
            return
        }

        callback.onRequestStart()

        val g = Gson()

        val payload = g.toJson(GetEntitiesPayload(tiles))

        apiCall("getEntities", payload, object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getTilesInfo(tiles, callback, retry + 1)
                callback.onRequestEnd()
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() == 400 && tiles.size > 2) {
                    tiles.withIndex()
                        .groupBy { it.index / (tiles.size / 2) }
                        .map {
                            getTilesInfo(it.value.map { it.value }, callback, retry)
                        }
                    callback.onRequestEnd()
                    return
                }
                if (response.code() != 200) {
                    callback.onRequestEnd()
                    return
                }

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
                                    links[e[0] as String] = Link(e[0] as String, entityData)
                                }
                                "r" -> {
                                    fields[e[0] as String] = Field(e[0] as String, entityData)
                                }
                            }
                        }
                        callback.onCellDataReceived(es.key, portals, links, fields)
                    }
                    if (next.isNotEmpty()) {
                        getTilesInfo(next, callback, retry + 1)
                    }
                } catch (e: Exception) {
                    print(e.message)
                }
                callback.onRequestEnd()
            }
        })
    }
}