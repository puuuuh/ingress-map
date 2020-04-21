package com.puuuuh.ingressmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.puuuuh.ingressmap.map.view.MapActivity
import com.puuuuh.ingressmap.ui.login.LoginActivity
import com.google.android.libraries.places.api.Places

class MainActivity : AppCompatActivity() {
    private val ACCESS_FINE_LOCATION_RC = 1
    private val LOGIN_RC = 0
    private val MAP_RC = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
                    startLogin()
                }
            }
        }
    }

    private fun startLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivityForResult(intent, LOGIN_RC)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == LOGIN_RC) {
            if (data != null) {
                val token = data.getStringExtra("token")
                val csrf = data.getStringExtra("csrftoken")
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("token", token)
                intent.putExtra("csrfToken", csrf)
                startActivityForResult(intent, MAP_RC)
            } else {
                startLogin()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
