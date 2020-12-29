package com.puuuuh.ingressmap.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

data class FullPosition(val lat: Double, val lng: Double, val zoom: Float)

object Settings {
    private const val SHOW_FIELDS = "SHOW_FIELDS"
    private const val SHOW_LINKS = "SHOW_LINKS"
    private const val SHOW_CELLS = "SHOW_CELLS"
    private const val DRAW_MODE = "DRAW_MODE"
    private const val SHOW_PORTALS = "SHOW_PORTALS"
    private const val LAST_LAT = "LAST_LAT"
    private const val LAST_LNG = "LAST_LNG"
    private const val LAST_ZOOM = "LAST_ZOOM"
    private const val TOKEN = "TOKEN"
    private const val CSRFTOKEN = "CSRFTOKEN"
    private const val MY_LOCATION = "MY_LOCATION"
    private const val API_VERSION = "API_VERSION"
    private const val MAP_PROVIDER = "MAP_PROVIDER"
    private const val THEME = "THEME"
    private const val HIDE_TEAMS = "HIDE_TEAMS"

    private var mSharedPref: SharedPreferences? = null

    private val _liveToken = MutableLiveData<String>()
    val liveToken: LiveData<String> = _liveToken

    private val _liveHideTeams = MutableLiveData<Boolean>()
    val liveHideTeams: LiveData<Boolean> = _liveHideTeams

    fun init(context: Context) {
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        _liveToken.value = this.token
        _liveHideTeams.value = this.hideTeams

        mSharedPref!!.registerOnSharedPreferenceChangeListener { _: SharedPreferences, s: String ->
            when (s) {
                TOKEN -> _liveToken.value = this.token
                HIDE_TEAMS -> _liveHideTeams.value = this.hideTeams
            }
        }
    }

    var showFields: Boolean
        get() {
            return mSharedPref?.getBoolean(SHOW_FIELDS, false) ?: false
        }
        set(value) {
            mSharedPref?.edit()?.putBoolean(SHOW_FIELDS, value)?.apply()
        }

    var showPortals: Boolean
        get() {
            return mSharedPref?.getBoolean(SHOW_PORTALS, false) ?: false
        }
        set(value) {
            mSharedPref?.edit()?.putBoolean(SHOW_PORTALS, value)?.apply()
        }

    var showLinks: Boolean
        get() {
            return mSharedPref?.getBoolean(SHOW_LINKS, false) ?: false
        }
        set(value) {
            mSharedPref?.edit()?.putBoolean(SHOW_LINKS, value)?.apply()
        }

    var showCells: Boolean
        get() {
            return mSharedPref?.getBoolean(SHOW_CELLS, false) ?: false
        }
        set(value) {
            mSharedPref?.edit()?.putBoolean(SHOW_CELLS, value)?.apply()
        }

    var drawMode: Boolean
        get() {
            return mSharedPref?.getBoolean(DRAW_MODE, false) ?: false
        }
        set(value) {
            mSharedPref?.edit()?.putBoolean(DRAW_MODE, value)?.apply()
        }

    var lastPosition: FullPosition
        get() {
            val lat = mSharedPref?.getFloat(LAST_LAT, 0f)!!.toDouble()
            val lng = mSharedPref?.getFloat(LAST_LNG, 0f)!!.toDouble()
            val zoom = mSharedPref?.getFloat(LAST_ZOOM, 3f)!!
            return FullPosition(lat, lng, zoom)
        }
        set(value) {
            mSharedPref?.edit()
                ?.putFloat(LAST_LAT, value.lat.toFloat())
                ?.putFloat(LAST_LNG, value.lng.toFloat())
                ?.putFloat(LAST_ZOOM, value.zoom)
                ?.apply()
        }
    var apiVersion: String
        get() {
            return mSharedPref?.getString(API_VERSION, "")!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putString(API_VERSION, value)
                ?.apply()
        }
    var token: String
        get() {
            return mSharedPref?.getString(TOKEN, "")!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putString(TOKEN, value)
                ?.apply()
        }
    var csrfToken: String
        get() {
            return mSharedPref?.getString(CSRFTOKEN, "")!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putString(CSRFTOKEN, value)
                ?.apply()
        }

    var myLocation: Boolean
        get() {
            return mSharedPref?.getBoolean(MY_LOCATION, true)!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putBoolean(MY_LOCATION, value)
                ?.apply()
        }

    var mapProvider: String
        get() {
            return mSharedPref?.getString(MAP_PROVIDER, "")!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putString(MAP_PROVIDER, value)
                ?.apply()
        }

    var theme: Int
        get() {
            return Integer.parseInt(mSharedPref?.getString(THEME, "-1")!!)
        }
        set(value) {
            mSharedPref?.edit()
                    ?.putString(THEME, value.toString())
                    ?.apply()
        }

    var hideTeams: Boolean
        get() {
            return mSharedPref?.getBoolean(HIDE_TEAMS, true)!!
        }
        set(value) {
            mSharedPref?.edit()
                    ?.putBoolean(HIDE_TEAMS, value)
                    ?.apply()
        }
}