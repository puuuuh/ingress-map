package com.puuuuh.ingressmap

import android.app.SearchManager
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.navigation.NavigationView
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.repository.PlacesRepository
import com.puuuuh.ingressmap.repository.PortalsRepo
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.view.LoginActivity
import com.puuuuh.ingressmap.view.PortalInfo
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.search_switch.*
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val mapViewModel: MapViewModel by viewModels { ViewmodelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
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



        Settings.liveToken.observe(this, Observer {
            if (it == "") {
                startLogin()
            }
        })

        Settings.liveMapProvider.observe(this, Observer {
            navView.menu.clear()
            appBarConfiguration = if (it == "osm") {
                navView.inflateMenu(R.menu.drawer_osm)
                navController.setGraph(R.navigation.navgraph_osm)
                AppBarConfiguration(
                    setOf(
                        R.id.nav_osmmap
                    ), drawerLayout
                )
            } else {
                navView.inflateMenu(R.menu.drawer_gmap)
                navController.setGraph(R.navigation.navgraph_gmaps)
                AppBarConfiguration(
                    setOf(
                        R.id.nav_map
                    ), drawerLayout
                )
            }

            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
            /*if (it == null) {
                return@Observer
            }
            val mStartActivity = Intent(this, MainActivity::class.java)
            val mPendingIntentId = 123456
            val mPendingIntent = PendingIntent.getActivity(
                this,
                mPendingIntentId,
                mStartActivity,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            (this.getSystemService(Context.ALARM_SERVICE) as AlarmManager)[AlarmManager.RTC, System.currentTimeMillis() + 100] =
                mPendingIntent
            exitProcess(0)*/
        })

        mapViewModel.selectedPortal.observe(
            this,
            androidx.lifecycle.Observer { data ->
                if (data != null) {
                    val fm = supportFragmentManager
                    val dlg = PortalInfo.newInstance(data)
                    dlg.show(fm, "fragment_alert")
                    mapViewModel.selectPortal(null)
                }
            })
    }

    private fun <T> throttleLatest(
        intervalMs: Long = 300L,
        coroutineScope: CoroutineScope,
        destinationFunction: (T) -> Unit
    ): (T) -> Unit {
        var throttleJob: Job? = null
        var latestParam: T
        return { param: T ->
            latestParam = param
            if (throttleJob?.isCompleted != false) {
                throttleJob = coroutineScope.launch {
                    delay(intervalMs)
                    latestParam.let(destinationFunction)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val item = menu.findItem(R.id.search_toggle_item)
        item.setActionView(R.layout.search_switch)

        val searchView: SearchView = menu.findItem(R.id.action_search).actionView as SearchView
        val columNames =
            arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1)
        val viewIds = intArrayOf(android.R.id.text1)

        val adapter: CursorAdapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_list_item_1, null, columNames, viewIds
        )
        searchView.suggestionsAdapter = adapter

        val portalsRepo = PortalsRepo()
        val placesRepo = PlacesRepository()

        placesRepo.data.observe(this, androidx.lifecycle.Observer {
            val columns =
                arrayOf("_id", "suggest_text_1", "lat", "lng")

            val matrixCursor = MatrixCursor(columns)
            for ((i, data) in it.withIndex()) {
                val test = data.properties.name
                    ?: "${data.properties.street} ${data.properties.housenumber}"
                val name = "$test ${data.properties.state} ${data.properties.country}"
                matrixCursor.addRow(
                    arrayOf(
                        i,
                        name,
                        data.geometry.coordinates[0],
                        data.geometry.coordinates[1]
                    )
                )
            }
            runOnUiThread {
                adapter.swapCursor(matrixCursor)
            }
        })

        val portalsThrottle = throttleLatest<String>(1000, GlobalScope) {
            GlobalScope.launch {
                val columns =
                    arrayOf("_id", "suggest_text_1", "lat", "lng", "id")

                val matrixCursor = MatrixCursor(columns)
                for ((i, data) in portalsRepo.find(it).withIndex()) {
                    matrixCursor.addRow(
                        arrayOf(
                            i,
                            data.title,
                            data.lng,
                            data.lat,
                            data.id,
                        )
                    )
                }
                runOnUiThread {
                    adapter.swapCursor(matrixCursor)
                }
            }
        }

        val placesThrottle = throttleLatest<String>(1000, GlobalScope) {
            placesRepo.get(it)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) {
                    if (search_toggle.isChecked)
                        placesThrottle(newText)
                    else
                        portalsThrottle(newText)
                }
                return true
            }
        })

        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val test = adapter.getItem(position)
                if (test is MatrixCursor) {
                    mapViewModel.moveCamera(LatLng(test.getDouble(3), test.getDouble(2)))
                    if (test.columnCount == 5)
                        mapViewModel.selectPortal(
                            GameEntity.Portal(
                                test.getString(4),
                                PortalData(
                                    test.getString(1),
                                    test.getDouble(3),
                                    test.getDouble(2)
                                )
                            )
                        )
                }

                return true
            }
        })

        return true
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