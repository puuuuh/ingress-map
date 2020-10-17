package com.puuuuh.ingressmap.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.puuuuh.ingressmap.BuildConfig
import okhttp3.*
import java.io.IOException

data class Places(
    val features: List<Feature>,
    val type: String
)

data class Feature(
    val geometry: Geometry,
    val properties: Properties,
    val type: String
)

data class Geometry(
    val coordinates: List<Double>,
    val type: String
)

data class Properties(
    val city: String,
    val country: String,
    val extent: List<Double>,
    val housenumber: String,
    val name: String?,
    val osm_id: Long,
    val osm_key: String,
    val osm_type: String,
    val osm_value: String,
    val postcode: String,
    val state: String,
    val street: String
)

class PlacesRepository {
    var okHttpClient: OkHttpClient = OkHttpClient()

    init {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()
    }

    private val _data = MutableLiveData<List<Feature>>()
    val data: LiveData<List<Feature>> = _data

    fun get(q: String) {
        val url = HttpUrl.Builder().host("photon.komoot.de")
            .encodedPath("/api/")
            .addQueryParameter("q", q)
            .addQueryParameter("lang", "en")
            .scheme("https")
            .build()
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", BuildConfig.APPLICATION_ID)
            .get()
            .build()
        okHttpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {}

            override fun onResponse(call: Call?, response: Response?) {
                try {
                    val json = response?.body()?.string()
                    val res = Gson().fromJson(json, Places::class.java)
                    _data.postValue(res.features)
                } catch (e: Exception) {
                }
            }
        })
    }
}