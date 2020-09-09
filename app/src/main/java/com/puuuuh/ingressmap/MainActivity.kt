package com.puuuuh.ingressmap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.material.navigation.NavigationView
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.view.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        setContentView(R.layout.activity_main)

        if (intent.action == Intent.ACTION_VIEW && intent.dataString != null) {
            val url = Uri.parse(intent.dataString)
            val location = url.getQueryParameter("ll")
            val zoom = url.getQueryParameter("z")
            if (location != null) {
                val parts = location.split(",")
                if (parts.count() == 2) {
                    try {
                        Settings.lastPosition = LatLng(parts[0].toDouble(), parts[1].toDouble())
                    } catch (e: NumberFormatException) {
                    }
                }
            }
            if (zoom != null) {
                try {
                    Settings.lastZoom = zoom.toFloat()
                } catch (e: NumberFormatException) {
                }
            }
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.main_fragment)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_map
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        Settings.liveToken.observe(this, Observer {
            if (it == "") {
                startLogin()
            }
        })
    }

    private fun startLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.main_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}