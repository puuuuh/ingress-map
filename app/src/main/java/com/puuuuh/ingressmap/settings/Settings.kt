package com.puuuuh.ingressmap.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng

object Settings {
    private const val APP_SETTINGS = "APP_SETTINGS"
    private const val SHOW_FIELDS = "SHOW_FIELDS"
    private const val SHOW_LINKS = "SHOW_LINKS"
    private const val SHOW_PORTALS = "SHOW_PORTALS"
    private const val LAST_LAT = "LAST_LAT"
    private const val LAST_LNG = "LAST_LNG"
    private const val LAST_ZOOM = "LAST_ZOOM"
    private const val TOKEN = "TOKEN"
    private const val CSRFTOKEN = "CSRFTOKEN"
    private const val API_VERSION = "API_VERSION"
    private var mSharedPref: SharedPreferences? = null

    fun init(context: Context) {
        mSharedPref = context.getSharedPreferences(APP_SETTINGS, Activity.MODE_PRIVATE);
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

    var lastPosition: LatLng
        get() {
            val lat = mSharedPref?.getFloat(LAST_LAT, 0f)!!.toDouble()
            val lng = mSharedPref?.getFloat(LAST_LNG, 0f)!!.toDouble()
            return LatLng(lat, lng)
        }
        set(value) {
            mSharedPref?.edit()
                ?.putFloat(LAST_LAT, value.latitude.toFloat())
                ?.putFloat(LAST_LNG, value.longitude.toFloat())
                ?.apply()
        }
    var lastZoom: Float
        get() {
            return mSharedPref?.getFloat(LAST_ZOOM, 3f)!!
        }
        set(value) {
            mSharedPref?.edit()
                ?.putFloat(LAST_ZOOM, value)
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
}