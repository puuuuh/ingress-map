package com.puuuuh.ingressmap.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.settings.Settings

class MainActivity : AppCompatActivity() {
    private val ACCESS_FINE_LOCATION_RC = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Settings.init(applicationContext)

        if (intent.action == Intent.ACTION_VIEW && intent.dataString != null) {
            val url = Uri.parse(intent.dataString)
            val location = url.getQueryParameter("ll")
            val zoom = url.getQueryParameter("z")
            if (location != null) {
                val parts = location.split(",")
                if (parts.count() == 2) {
                    try {
                        Settings.lastPosition = LatLng(parts[0].toDouble(), parts[1].toDouble())
                    } catch (e: NumberFormatException) {}
                }
            }
            if (zoom != null) {
                try {
                    Settings.lastZoom = zoom.toFloat()
                } catch (e: NumberFormatException) {}
            }
        }

        Settings.liveToken.observe(this, Observer {
            if (it == "") {
                startLogin()
            }
        })

        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        fineLocationRequest()
    }

    private fun fineLocationRequest() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            ACCESS_FINE_LOCATION_RC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            ACCESS_FINE_LOCATION_RC -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    fineLocationRequest()
                } else {
                    startMap()
                }
            }
        }
    }

    private fun startMap() {
        startActivity(Intent(this, MapActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun startLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}
