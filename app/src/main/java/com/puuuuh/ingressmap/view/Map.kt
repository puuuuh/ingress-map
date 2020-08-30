package com.puuuuh.ingressmap.view

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.repository.Portal
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.activity_map.*


class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener,
    GoogleMap.OnMarkerClickListener, GoogleMap.OnPolylineClickListener,
    DialogInterface.OnDismissListener {
    private lateinit var mMap: GoogleMap
    private var lines = hashMapOf<S2CellId, Polyline>()
    private var portals = mutableMapOf<String, Marker>()
    private var fields = mutableMapOf<String, Polygon>()
    private var links = mutableMapOf<String, Polyline>()
    private var customLinks = mutableMapOf<PolylineOptions, Polyline>()
    private var selectedPoint: Marker? = null
    private lateinit var mapViewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        setSupportActionBar(toolbar)

        mapViewModel = ViewModelProvider(
            this,
            ViewmodelFactory(application)
        ).get(MapViewModel::class.java)

        mapViewModel.portals.observe(this, androidx.lifecycle.Observer {
            if (!this::mMap.isInitialized) {
                return@Observer
            }
            val newPortals = mutableMapOf<String, Marker>()
            for (i in it) {
                val old = portals.remove(i.key)
                val iconRes = when (i.value.team) {
                    "E" -> {
                        R.drawable.ic_green_portal
                    }
                    "R" -> {
                        R.drawable.ic_blue_portal
                    }
                    else -> {
                        R.drawable.ic_white_portal
                    }
                }
                val pos = LatLng(i.value.lat, i.value.lng)
                if (old == null) {
                    val m = MarkerOptions()
                        .position(pos)
                        .title(i.value.name)
                        .icon(BitmapDescriptorFactory.fromResource(iconRes))
                        .zIndex(2f)
                        .anchor(0.5f, 0.5f)
                    newPortals[i.key] = mMap.addMarker(m)
                } else {
                    if (old.position != pos) {
                        old.position = pos
                    }
                    if ((old.tag as Portal).team != i.value.team) {
                        old.setIcon(BitmapDescriptorFactory.fromResource(iconRes))
                    }
                    newPortals[i.key] = old
                }
                newPortals[i.key]!!.tag = i.value
            }
            for (l in portals) {
                l.value.remove()
            }
            portals = newPortals
        })
        mapViewModel.links.observe(this, androidx.lifecycle.Observer { data ->
            if (!this::mMap.isInitialized) {
                return@Observer
            }
            val newLinks = mutableMapOf<String, Polyline>()
            for (i in data) {
                val old = links.remove(i.key)
                val color = if (i.value.team == "E") {
                    Color.GREEN
                } else {
                    Color.BLUE
                }

                if (old == null) {
                    val m = PolylineOptions()
                        .add(i.value.points[0].LatLng, i.value.points[1].LatLng)
                        .width(2f)
                        .zIndex(3f)
                        .color(color)

                    newLinks[i.key] = mMap.addPolyline(m)
                } else {
                    if (old.color != color) {
                        old.color = color
                    }
                    if (old.points[0] != i.value.points[0].LatLng) {
                        old.points[0] = i.value.points[0].LatLng
                    }
                    if (old.points[1] != i.value.points[1].LatLng) {
                        old.points[1] = i.value.points[1].LatLng
                    }
                    newLinks[i.key] = old
                }
            }
            for (l in links) {

                l.value.remove()
            }
            links = newLinks
        })
        mapViewModel.fields.observe(this, androidx.lifecycle.Observer { data ->
            if (!this::mMap.isInitialized) {
                return@Observer
            }
            val newFields = mutableMapOf<String, Polygon>()
            for (i in data) {
                val old = fields.remove(i.key)
                if (old == null) {
                    val color = if (i.value.team == "E") {
                        Color.argb(100, 0, 255, 0)
                    } else {
                        Color.argb(100, 0, 0, 255)
                    }
                    val strokeColor = if (i.value.team == "E") {
                        Color.argb(255, 0, 255, 0)
                    } else {
                        Color.argb(255, 0, 140, 255)
                    }
                    val m = PolygonOptions()
                        .add(
                            i.value.points[0].LatLng,
                            i.value.points[1].LatLng,
                            i.value.points[2].LatLng
                        )
                        .fillColor(color)
                        .strokeWidth(4f)
                        .zIndex(2f)
                        .strokeColor(strokeColor)

                    newFields[i.key] = mMap.addPolygon(m)
                } else {
                    newFields[i.key] = old
                }
            }
            for (l in fields) {
                l.value.remove()
            }
            fields = newFields
        })
        mapViewModel.cellLines.observe(this, androidx.lifecycle.Observer { data ->
            if (!this::mMap.isInitialized) {
                return@Observer
            }
            val newLines = hashMapOf<S2CellId, Polyline>()
            data.map {
                var line = lines[it.key]
                if (line == null) {
                    when (it.key.level()) {
                        14 -> {
                            it.value
                                .color(Color.rgb(239, 70, 15))
                                .width(8f)
                                .zIndex(1f)
                        }
                        17 -> {
                            it.value
                                .color(Color.rgb(8, 152, 152))
                                .width(4f)
                                .zIndex(0f)
                        }
                    }
                    line = mMap.addPolyline(it.value)
                }
                newLines[it.key] = line!!
                lines.remove(it.key)
            }
            lines.map {
                it.value.remove()
            }
            lines = newLines
        })

        mapViewModel.status.observe(this, androidx.lifecycle.Observer {
            statusView.text = "Status: " + if (it.requestsInProgress == 0) {
                "up to date"
            } else {
                "${it.requestsInProgress} request in progress, please, wait..."
            }
        })

        mapViewModel.customLines.observe(this, androidx.lifecycle.Observer { data ->
            val newLinks = mutableMapOf<PolylineOptions, Polyline>()
            for (i in data) {
                val old = customLinks.remove(i)
                val color = Color.RED

                if (old == null) {
                    val m = PolylineOptions()
                        .add(i.points[0], i.points[1])
                        .width(10f)
                        .zIndex(4f)
                        .color(color)
                        .clickable(true)

                    newLinks[i] = mMap.addPolyline(m)
                } else {
                    newLinks[i] = old
                }
            }
            customLinks.map {
                it.value.remove()
            }
            customLinks = newLinks
        })

        mapViewModel.selectedPortal.observe(this, androidx.lifecycle.Observer { data ->
            if (data != null) {
                val fm = supportFragmentManager
                val dlg = PortalInfo.newInstance(data)
                dlg.show(fm, "fragment_alert")
                fm.executePendingTransactions()
                val dialog = dlg.dialog
                if (dialog != null) {
                    dialog.setOnDismissListener(this)
                } else {
                    mapViewModel.selectPortal(null)
                }

            }
        })

        mapViewModel.selectedPoint.observe(this, androidx.lifecycle.Observer { data ->
            selectedPoint?.remove()
            if (data != null) {
                selectedPoint = mMap.addMarker(
                    MarkerOptions()
                        .position(data)
                )
            }
        })

        (map as SupportMapFragment).getMapAsync(this)
        val autocomplete =
            supportFragmentManager.findFragmentById(R.id.autocomplete) as AutocompleteSupportFragment
        autocomplete.setPlaceFields(listOf(Place.Field.LAT_LNG))
        autocomplete.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val pos = CameraPosition.Builder()
                    .zoom(15f)
                    .target(place.latLng)
                    .build()
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(pos))
            }

            override fun onError(status: Status) {}
        })

        mapViewModel.setFieldsVisible(Settings.showFields)
        mapViewModel.setLinksVisible(Settings.showLinks)
        mapViewModel.setPortalsVisible(Settings.showPortals)
        mapViewModel.setDrawMode(Settings.drawMode)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.showLinks)?.isChecked = Settings.showLinks
        menu?.findItem(R.id.showFields)?.isChecked = Settings.showFields
        menu?.findItem(R.id.showPortals)?.isChecked = Settings.showPortals
        menu?.findItem(R.id.drawMode)?.isChecked = Settings.drawMode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.showPortals -> {
                item.isChecked = !item.isChecked
                Settings.showPortals = item.isChecked
                mapViewModel.setPortalsVisible(item.isChecked)
                return true
            }
            R.id.showFields -> {
                item.isChecked = !item.isChecked
                Settings.showFields = item.isChecked
                mapViewModel.setFieldsVisible(item.isChecked)
                return true
            }
            R.id.showLinks -> {
                item.isChecked = !item.isChecked
                Settings.showLinks = item.isChecked
                mapViewModel.setLinksVisible(item.isChecked)
                return true
            }
            R.id.drawMode -> {
                item.isChecked = !item.isChecked
                Settings.drawMode = item.isChecked
                mapViewModel.setDrawMode(item.isChecked)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMaxZoomPreference(21f)
        mMap.setOnMarkerClickListener(this)
        mMap.setOnPolylineClickListener(this)
        mMap.setMinZoomPreference(3f)
        var zoom = Settings.lastZoom
        if (zoom < 3) {
            zoom = 3f
        }
        if (zoom > 21f) {
            Settings.lastZoom = 21f
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Settings.lastPosition, zoom))
        mMap.setOnCameraIdleListener(this)
        mMap.isMyLocationEnabled = true
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
    }

    override fun onCameraIdle() {
        Settings.lastPosition = mMap.cameraPosition.target
        Settings.lastZoom = mMap.cameraPosition.zoom
        mapViewModel.updateRegion(
            mMap.projection.visibleRegion.latLngBounds,
            mMap.cameraPosition.zoom.toInt()
        )
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        if (p0?.tag is Portal) {
            mapViewModel.selectPortal(p0.tag as Portal)
            return true

        }

        return true
    }

    override fun onPolylineClick(p0: Polyline) {
        mapViewModel.removeCustomLine(p0.points.toTypedArray())

        return
    }

    override fun onDismiss(dialog: DialogInterface?) {
        mapViewModel.selectPortal(null)
    }
}

