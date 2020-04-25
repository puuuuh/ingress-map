package com.puuuuh.ingressmap.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class AuthInfo(val token: String, val csrf: String, val version: String)

class LoginViewModel : ViewModel() {
    private val _authInfo = MutableLiveData<AuthInfo>()
    val authInfo: LiveData<AuthInfo> = _authInfo

    fun setTokens(token: String, csrf: String) {
        val old = authInfo.value
        if (old != null)
            _authInfo.value = AuthInfo(token, csrf, old.version)
        else
            _authInfo.value = AuthInfo(token, csrf, "")
    }

    fun setVersion(version: String) {
        val old = authInfo.value
        if (old != null)
            _authInfo.value = AuthInfo(old.token, old.csrf, version)
        else
            _authInfo.value = AuthInfo("", "", version)
    }
}
