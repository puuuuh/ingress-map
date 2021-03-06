package com.puuuuh.ingressmap.utils

import com.puuuuh.ingressmap.settings.Settings
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val resp = chain.proceed(
            req.newBuilder()
                .addHeader(
                    "cookie",
                    "csrftoken=${Settings.csrfToken}; sessionid=${Settings.token};"
                )
                .addHeader("x-csrftoken", Settings.csrfToken)
                .addHeader("referer", "https://intel.ingress.com/")
                .build()
        )
        if (resp.code() == 403 || resp.code() == 200 && (resp.body()?.contentType()
                ?.type() != "application" ||
                    resp.body()?.contentType()?.subtype() != "json") && req.method() == "POST"
        ) {
            if (Settings.token != "") {
                Settings.token = ""
            }
        }
        return resp
    }
}