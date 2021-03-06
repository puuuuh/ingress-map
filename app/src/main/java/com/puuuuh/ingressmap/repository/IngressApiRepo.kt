package com.puuuuh.ingressmap.repository

import android.util.Log
import com.puuuuh.ingressmap.MainApplication
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.PlayerInfo
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.AuthInterceptor
import okhttp3.*
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface OnDataReadyCallback {
    fun onCellDataReceived(
        seq: Int,
        cellId: String, portals: Map<String, GameEntity.Portal>,
        links: Map<String, GameEntity.Link>,
        fields: Map<String, GameEntity.Field>
    )

    fun onRequestStart()
    fun onRequestEnd()
}

interface OnPortalExReadyCallback {
    fun onPortalExReady(portal: PortalData)
}

interface OnPlayerInfoReadyCallback {
    fun onPlayerInfoReady(info: PlayerInfo)
}

data class GetEntitiesPayload(val tileKeys: List<String>) {
    val v = Settings.apiVersion
}

data class GetPortalDataPayload(val guid: String) {
    val v = Settings.apiVersion
}

data class GetEntitiesResponse(
    val result: EntitiesMap
) {
    data class EntitiesMap(
        val map: Map<String, Entities>
    ) {
        data class Entities(
            val gameEntities: Array<GameEntity>?
        )
    }
}

data class GetPortalDataResponse(
    val result: PortalData
)

data class CacheEntry(
    val portals: Map<String, GameEntity.Portal>,
    val links: Map<String, GameEntity.Link>,
    val fields: Map<String, GameEntity.Field>,
)

class IngressApiRepo {
    var cache: ConcurrentMap<String, CacheEntry> = ConcurrentHashMap()
    val g = MainApplication.gson
    var okHttpClient: OkHttpClient = OkHttpClient()

    init {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()
    }

    private fun apiCall(method: String, payload: String, cb: Callback, seq: Int = Int.MAX_VALUE) {
        val r = Request.Builder()
            .url(URL("https://intel.ingress.com/r/$method"))
            .post(RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), payload))
            .tag(seq)
            .build()
        okHttpClient.newCall(r).enqueue(cb)
    }

    private fun getPage(url: String, cb: Callback) {
        val r = Request.Builder()
            .url(URL("https://intel.ingress.com/intel/$url"))
            .get()
            .build()
        okHttpClient.newCall(r).enqueue(cb)
    }

    fun getPlayerInfo(callback: OnPlayerInfoReadyCallback, retry: Int = 0) {
        if (retry > 2) {
            return
        }
        getPage("", object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getPlayerInfo(callback, retry + 1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200)
                    return
                try {
                    val html = response.body()?.string()
                    val r = Regex("var PLAYER = (\\{.*?\\})")
                    if (html != null) {
                        val json = r.find(html)?.groups?.get(1)?.value
                        if (json != null) {
                            callback.onPlayerInfoReady(g.fromJson(json, PlayerInfo::class.java))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IngressApiRepo", e.message ?: "")
                }
            }
        })
    }

    fun getExtendedPortalData(guid: String, callback: OnPortalExReadyCallback, retry: Int = 0) {
        if (retry > 2) {
            return
        }


        val payload = g.toJson(GetPortalDataPayload(guid))

        apiCall("getPortalDetails", payload, object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getExtendedPortalData(guid, callback, retry + 1)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() != 200 || (response.body()?.contentType()
                        ?.type() != "application" ||
                            response.body()?.contentType()?.subtype() != "json")
                )
                    return
                try {
                    val json = response.body()?.string()
                    val res = g.fromJson(json, GetPortalDataResponse::class.java)
                    callback.onPortalExReady(res.result)
                } catch (e: Exception) {
                    Log.e("IngressApiRepo", e.message ?: "")
                }
            }
        })
    }

    fun getTilesInfo(seq: Int, tiles: List<String>, callback: OnDataReadyCallback, retry: Int = 0) {
        try {
            for (call in okHttpClient.dispatcher().queuedCalls()) {
                if ((call.request().tag() as Int) < seq)
                    call.cancel()
            }
        } catch (e: Exception) {
            Log.e("IngressApiRepo", e.message ?: "")
        }

        if (retry == 0) {
            tiles.forEach {
                val cached = cache[it]
                if (cached != null) {
                    callback.onCellDataReceived(
                        seq,
                        it,
                        cached.portals,
                        cached.links,
                        cached.fields
                    )
                }
            }
        }

        if (retry > 4) {
            return
        }

        callback.onRequestStart()

        val g = MainApplication.gson

        val payload = g.toJson(GetEntitiesPayload(tiles))

        apiCall("getEntities", payload, object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                getTilesInfo(seq, tiles, callback, retry + 1)
                callback.onRequestEnd()
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response!!.code() == 400 && tiles.size > 2) {
                    tiles.withIndex()
                        .groupBy { it.index / (tiles.size / 2) }
                        .map {
                            getTilesInfo(seq, it.value.map { it.value }, callback, retry)
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
                        val portals = mutableMapOf<String, GameEntity.Portal>()
                        val links = mutableMapOf<String, GameEntity.Link>()
                        val fields = mutableMapOf<String, GameEntity.Field>()
                        for (e in es.value.gameEntities!!) {
                            when (e) {
                                is GameEntity.Portal -> {
                                    portals[e.id] = e
                                }
                                is GameEntity.Link -> {
                                    links[e.id] = e
                                }
                                is GameEntity.Field -> {
                                    fields[e.id] = e
                                }
                            }
                        }

                        cache[es.key] = CacheEntry(portals, links, fields)
                        callback.onCellDataReceived(seq, es.key, portals, links, fields)
                    }
                    if (next.isNotEmpty()) {
                        getTilesInfo(seq, next, callback, retry + 1)
                    }
                } catch (e: Exception) {
                    val stacktrace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString().trim()
                    Log.e("IngressApiRepo", stacktrace)
                }
                callback.onRequestEnd()
            }
        }, seq)
    }
}