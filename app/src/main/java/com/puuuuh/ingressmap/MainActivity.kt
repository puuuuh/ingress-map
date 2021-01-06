package com.puuuuh.ingressmap

import android.app.SearchManager
import android.content.Intent
import android.content.res.ColorStateList
import android.database.MatrixCursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import androidx.appcompat.widget.Toolbar
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.puuuuh.ingressmap.repository.PlacesRepository
import com.puuuuh.ingressmap.repository.PortalsRepository
import com.puuuuh.ingressmap.settings.FullPosition
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.throttleLatest
import com.puuuuh.ingressmap.view.LoginActivity
import com.puuuuh.ingressmap.view.PortalInfo
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.search_switch.*
import kotlinx.coroutines.GlobalScope


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val mapViewModel: MapViewModel by viewModels { ViewmodelFactory(applicationContext) }
    private val loginContract = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.token == "") {
            startLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (intent.action == Intent.ACTION_VIEW && intent.dataString != null) {
            val url = Uri.parse(intent.dataString)
            val location = url.getQueryParameter("ll")
            val zoom = url.getQueryParameter("z")
            if (location != null) {
                val parts = location.split(",")
                if (parts.count() == 2) {
                    try {
                        Settings.lastPosition = FullPosition(
                                parts[0].toDouble(),
                                parts[1].toDouble(),
                                (zoom?.toFloat() ?: 17.0f)
                        )
                    } catch (e: NumberFormatException) {
                    }
                }
            }
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.main_fragment)

        navView.inflateMenu(R.menu.drawer_map)
        val graph = navController.navInflater.inflate(R.navigation.navgraph_map)

        if (Settings.mapProvider == "osm") {
            navView.menu.getItem(1).isVisible = false
            graph.startDestination = R.id.nav_osmmap
        } else {
            navView.menu.getItem(0).isVisible = false
            graph.startDestination = R.id.nav_map
        }

        navController.graph = graph

        appBarConfiguration = AppBarConfiguration(
                setOf(
                        R.id.nav_osmmap,
                        R.id.nav_map
                ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)


        Settings.liveToken.observe(this, {
            if (it == "") {
                startLogin()
            } else {
                mapViewModel.updatePlayerInfo()
            }
        })

        val header = navView.getHeaderView(0)
        val healthProgressBar = header.findViewById<ProgressBar>(R.id.healthProgress)
        val lvlProgressBar = header.findViewById<ProgressBar>(R.id.lvlProgress)
        val playerNameTextView = header.findViewById<TextView>(R.id.playerName)
        val playerLevelTextView = header.findViewById<TextView>(R.id.lvl)

        healthProgressBar.progressTintList = ColorStateList.valueOf(Color.RED)

        mapViewModel.playerInfo.observe(this, {
            val color = if (it.team == "ENLIGHTENED") {
                Color.GREEN
            } else {
                Color.BLUE
            }
            lvlProgressBar.progressTintList = ColorStateList.valueOf(color)

            val targetAP = it.min_ap_for_next_level - it.min_ap_for_current_level
            val currentAP = it.ap - it.min_ap_for_current_level

            playerNameTextView.text = it.nickname
            playerLevelTextView.text = it.verified_level.toString()

            if (targetAP > 0)
                lvlProgressBar.progress = currentAP * 100 / targetAP
            if (it.xm_capacity > 0)
                healthProgressBar.progress = it.energy * 100 / it.xm_capacity
        })

        mapViewModel.selectedPortal.observe(
                this,
                { data ->
                    if (data != null) {
                        val fm = supportFragmentManager
                        val dlg = PortalInfo.newInstance(data)
                        dlg.show(fm, "fragment_alert")
                        mapViewModel.selectPortal(null)
                    }
                })
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

        val portalsRepo = PortalsRepository()
        val placesRepo = PlacesRepository()

        placesRepo.data.observe(this, {
            val columns =
                    arrayOf("_id", "suggest_text_1", "lat", "lng")

            val matrixCursor = MatrixCursor(columns)
            for ((i, data) in it.withIndex()) {
                val test = data.properties.name
                        ?: "${data.properties.state} ${data.properties.street} ${data.properties.housenumber}"
                val name = "$test ${data.properties.country}"
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

        portalsRepo.data.observe(this, {
            val columns =
                    arrayOf("_id", "suggest_text_1", "lat", "lng")

            val matrixCursor = MatrixCursor(columns)
            for ((i, data) in it.withIndex()) {
                val name = data.name + " (" + data.address + ")"
                matrixCursor.addRow(
                        arrayOf(
                                i,
                                name,
                                data.lng.toDouble() / 1000000,
                                data.lat.toDouble() / 1000000,
                        )
                )
            }
            runOnUiThread {
                adapter.swapCursor(matrixCursor)
            }
        })

        val portalsThrottle = throttleLatest<String>(1000, GlobalScope) {
            portalsRepo.get(it)
        }

        val placesThrottle = throttleLatest<String>(1000, GlobalScope) {
            placesRepo.get(it)
        }

        val autocomplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text) as SearchAutoComplete

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                autocomplete.dropDownWidth = resources.displayMetrics.widthPixels - 10;
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
                    mapViewModel.moveCamera(
                            FullPosition(
                                    test.getDouble(3),
                                    test.getDouble(2),
                                    16.0f
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
        loginContract.launch(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.main_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}