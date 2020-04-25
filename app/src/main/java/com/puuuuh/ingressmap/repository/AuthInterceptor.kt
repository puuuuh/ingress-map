package com.puuuuh.ingressmap.repository

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.startActivity
import com.puuuuh.ingressmap.MainApplication
import com.puuuuh.ingressmap.view.LoginActivity
import com.puuuuh.ingressmap.settings.Settings
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val resp = chain.proceed(req.newBuilder()
            .addHeader("cookie", "csrftoken=${Settings.csrfToken}; sessionid=${Settings.token};")
            .addHeader("x-csrftoken", Settings.csrfToken)
            .addHeader("referer", "https://intel.ingress.com/")
            .build())
        if (resp.code() == 403 || resp.code() == 200 && (resp.body()?.contentType()?.type() != "application" ||
            resp.body()?.contentType()?.subtype() != "json")) {
            Settings.token = ""
        }
        return resp
    }
}