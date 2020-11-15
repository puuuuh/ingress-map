package com.puuuuh.ingressmap.repository

import android.util.Log
import com.puuuuh.ingressmap.MainApplication
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.AuthInterceptor
import okhttp3.*
import java.io.IOException
import java.net.URL

interface OnDataReadyCallback {
    fun onCellDataReceived(
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
        val g = MainApplication.gson

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

    fun getTilesInfo(tiles: List<String>, callback: OnDataReadyCallback, retry: Int = 0) {
        if (retry > 4) {
            return
        }

        callback.onRequestStart()

        val g = MainApplication.gson

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
                        callback.onCellDataReceived(es.key, portals, links, fields)
                    }
                    if (next.isNotEmpty()) {
                        getTilesInfo(next, callback, retry + 1)
                    }
                } catch (e: Exception) {
                    Log.e("IngressApiRepo", e.message ?: "")
                }
                callback.onRequestEnd()
            }
        })
    }
}