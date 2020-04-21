package com.puuuuh.ingressmap.ui.login

/**
 * User details post authentication that is exposed to the UI
 */
data class LoggedInUserView(
    val token: String,
    val csrfToken: String
)
