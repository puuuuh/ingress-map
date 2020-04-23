package com.puuuuh.ingressmap.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(token: String, cfToken: String) {
        _loginResult.value =
                LoginResult(success = LoggedInUserView(token = token, csrfToken = cfToken))
    }
}
