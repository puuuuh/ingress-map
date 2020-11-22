package com.puuuuh.ingressmap.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View.INVISIBLE
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.viewmodel.LoginViewModel
import kotlinx.android.synthetic.main.activity_login.*


class LoginActivity : AppCompatActivity() {
    private lateinit var viewModel: LoginViewModel

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        viewModel = ViewModelProvider(this).get(LoginViewModel::class.java)
        viewModel.setTokens("", "")
        viewModel.setVersion("")

        viewModel.authInfo.observe(this, {
            if (it.csrf.isNotEmpty() && it.token.isNotEmpty() && it.version.isNotEmpty()) {
                finish()
            }
        })


        login_webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request?.url.toString().startsWith("https://intel.ingress.com/intel?state=GOOGLE&code=")) {
                    view?.visibility = INVISIBLE
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("https://intel.ingress.com/intel?state=GOOGLE&code=")) {
                    val cookieManager: CookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie("intel.ingress.com")
                    if (cookies != null) {
                        val temp = cookies.split(";").toTypedArray()
                        var token = ""
                        var csrfToken = ""
                        for (ar1 in temp) {
                            if (ar1.contains("sessionid=")) {
                                val temp1 = ar1.split("=").toTypedArray()
                                if (temp1.size == 2) {
                                    token = temp1[1]
                                }
                            }
                            if (ar1.contains("csrftoken=")) {
                                val temp1 = ar1.split("=").toTypedArray()
                                if (temp1.size == 2) {
                                    csrfToken = temp1[1]
                                }
                            }
                        }
                        view.evaluateJavascript("var re = /gen_dashboard_(.*?).js/; re.exec(document.body.innerHTML)[1];") {
                            Settings.apiVersion = it.trim('"')
                            viewModel.setVersion(it.trim('"'))
                        }
                        Settings.token = token
                        Settings.csrfToken = csrfToken
                        viewModel.setTokens(token, csrfToken)
                    }
                }

                return
            }
        }
        login_webview.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 7.0; SM-G930V Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.125 Mobile Safari/537.36"
        login_webview.settings.javaScriptEnabled = true
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie("intel.ingress.com", "sessionid=")


        login_webview.loadUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=369030586920-h43qso8aj64ft2h5ruqsqlaia9g9huvn.apps.googleusercontent.com&redirect_uri=https://intel.ingress.com/intel&prompt=consent%20select_account&state=GOOGLE&scope=email%20profile&response_type=code")
    }
}