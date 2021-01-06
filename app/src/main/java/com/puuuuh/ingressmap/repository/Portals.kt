package com.puuuuh.ingressmap.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import com.puuuuh.ingressmap.MainApplication
import okhttp3.*
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class Portal(
        val name: String,
        val address: String,
        val lat: Int,
        val lng: Int,
)

val trustMyCert =
        object : X509TrustManager {
            val certPk = listOf<Byte>(48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -101, -27, -106, -47, -83, -53, 85, -13, -127, -84, -119, 86, -57, -114, 121, 21, 97, -82, -105, -2, -83, 81, 106, 33, 118, 76, -26, 109, -65, -52, -109, -70, -67, -122, -78, -23, -109, -109, 30, -44, -127, 5, 53, 116, -115, 122, 82, 51, 13, -9, -122, 32, 75, 67, 88, 127, 12, -58, -104, -6, -73, 9, 86, -5, -7, -115, -89, 49, -20, 57, 41, -93, -123, 12, -73, -17, 26, 27, -113, -56, 0, 106, 70, -101, 14, -56, 49, -82, -16, -40, -39, 41, 31, 39, 32, 36, -17, -105, -112, -125, 26, 71, -116, -72, 116, 125, -30, -112, -46, 14, -99, 81, -61, -84, 49, -50, -101, 94, 114, 120, 59, -114, -31, -28, -80, 15, 67, -70, 79, -123, 1, -87, -101, -60, 69, -85, 34, -108, 15, -3, -94, 26, -71, 13, 74, 65, -44, 33, 105, 124, 77, -70, -35, -98, -53, 57, 18, 114, 102, 75, 58, 39, 106, 99, -112, -127, 73, -97, 127, -53, 87, -108, 95, -20, -111, -96, 46, 100, 20, 95, -50, -35, -63, -6, 73, -5, 42, -106, -39, 9, 107, -78, 83, 117, 67, -105, -93, -86, 84, -114, 80, -68, -39, 63, -40, 36, 57, -52, -111, -116, -35, -1, -48, 78, -115, 87, 110, -60, 23, 0, 23, -70, -27, -74, 37, -87, -75, 96, -46, 59, -106, -89, 75, 71, 62, 28, -126, 78, -108, -28, 109, 95, 17, 49, -29, 65, 112, -79, 68, 92, -56, 74, 27, 0, -12, -35, 2, 3, 1, 0, 1);
            override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
            }

            override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                if (chain?.size != 1 || chain[0]?.publicKey?.encoded?.asList() != certPk) {
                    throw CertificateException("invalid cert")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        }



class PortalsRepository {
    var okHttpClient: OkHttpClient = OkHttpClient()

    init {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf(trustMyCert), SecureRandom())

        okHttpClient = OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, trustMyCert).hostnameVerifier { _, _ -> true }.build()
    }

    private val _data = MutableLiveData<Array<Portal>>()
    val data: LiveData<Array<Portal>> = _data

    fun get(q: String) {
        val url = HttpUrl.Builder().host("146.148.80.97")
                .encodedPath("/search")
                .addQueryParameter("name", q)
                .scheme("https")
                .build()
        val req = Request.Builder()
                .url(url)
                .get()
                .build()

        okHttpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                Log.e("PORTALS_REPO", e?.message ?: "");
            }

            override fun onResponse(call: Call?, response: Response?) {
                try {
                    val json = response?.body()?.string()
                    val res = MainApplication.gson.fromJson(json, Array<Portal>::class.java)
                    _data.postValue(res)
                } catch (e: Exception) {
                    Log.e("PORTALS_REPO", e.message ?: "");
                }
            }
        })
    }
}