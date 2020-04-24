package com.puuuuh.ingressmap.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.google.android.libraries.places.api.Places
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.settings.Settings

class MainActivity : AppCompatActivity() {
    private val ACCESS_FINE_LOCATION_RC = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Settings.init(applicationContext)
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        fineLocationRequest()
    }

    private fun fineLocationRequest() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            ACCESS_FINE_LOCATION_RC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
        val intent = Intent(this, MapActivity::class.java)
        startActivityForResult(intent, 0)
    }
}
