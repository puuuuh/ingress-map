package com.puuuuh.ingressmap.map.view

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
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
import com.puuuuh.ingressmap.map.viewmodel.MapViewModel
import com.puuuuh.ingressmap.map.viewmodel.UserData
import kotlinx.android.synthetic.main.activity_map.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback,  GoogleMap.OnCameraIdleListener {
    private lateinit var mMap: GoogleMap
    private var lines = hashMapOf<S2CellId, Polyline>()
    private var portals = mutableMapOf<String, Marker>()
    private var fields = mutableMapOf<String, Polygon>()
    private var links = mutableMapOf<String, Polyline>()
    private lateinit var mapViewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        setSupportActionBar(toolbar)

        val user = UserData(token=intent.getStringExtra("token"), csrfToken=intent.getStringExtra("csrfToken"))
        mapViewModel = MapViewModel(user)
        mapViewModel.portals.observe(this, androidx.lifecycle.Observer {
            val newPortals = mutableMapOf<String, Marker>()
            for (i in it) {
                val old = portals.remove(i.key)
                if (old == null) {
                    val resource = if (i.value.team == "E") {
                        R.drawable.ic_green_portal
                    } else if (i.value.team == "R") {
                        R.drawable.ic_blue_portal
                    } else {
                        R.drawable.ic_white_portal
                    }
                    val point = LatLng(i.value.lat, i.value.lng)
                    val m = MarkerOptions()
                        .position(point)
                        .title(i.value.name)
                        .icon(BitmapDescriptorFactory.fromResource(resource))
                        .anchor(0.5f, 0.5f)
                    newPortals[i.key] = mMap.addMarker(m)
                } else {
                    newPortals[i.key] = old
                }
            }
            for (l in portals) {
                l.value.remove()
            }
            portals = newPortals
        })
        mapViewModel.links.observe(this, androidx.lifecycle.Observer { data ->
            val newLinks = mutableMapOf<String, Polyline>()
            for (i in data) {
                val old = links.remove(i.key)
                if (old == null) {
                    val color = if (i.value.team == "E") {Color.GREEN} else {Color.BLUE}
                    val m = PolylineOptions()
                        .add(i.value.points[0].LatLng, i.value.points[1].LatLng)
                        .width(2f)
                        .color(color)

                    newLinks[i.key] = mMap.addPolyline(m)
                } else {
                    newLinks[i.key] = old
                }
            }
            for (l in links) {
                l.value.remove()
            }
            links = newLinks
        })
        mapViewModel.fields.observe(this, androidx.lifecycle.Observer { data ->
            val newFields = mutableMapOf<String, Polygon>()
            for (i in data) {
                val old = fields.remove(i.key)
                if (old == null) {
                    val color = if (i.value.team == "E") {Color.argb(100, 0, 255, 0)} else {Color.argb(100, 0, 0, 255)}
                    val strokeColor = if (i.value.team == "E") {Color.argb(255, 0, 255, 0)} else {Color.argb(255, 0, 140, 255)}
                    val m = PolygonOptions()
                        .add(i.value.points[0].LatLng, i.value.points[1].LatLng, i.value.points[2].LatLng)
                        .fillColor(color)
                        .strokeWidth(4f)
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
            val newLines = hashMapOf<S2CellId, Polyline>()
            data.map {
                var line = lines[it.key]
                if (line == null) {
                    when (it.key.level()) {
                        14 -> {
                            it.value
                                .color(Color.rgb(239, 70, 15))
                                .width(8f)
                                .zIndex(2f)
                        }
                        17 -> {
                            it.value
                                .color(Color.rgb(8, 152, 152))
                                .width(4f)
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
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.showPortals -> {
                item.isChecked = !item.isChecked
                mapViewModel.setPortalsVisible(item.isChecked)
                return true
            }
            R.id.showFields -> {
                item.isChecked = !item.isChecked
                mapViewModel.setFieldsVisible(item.isChecked)
                return true
            }
            R.id.showLinks -> {
                item.isChecked = !item.isChecked
                mapViewModel.setLinksVisible(item.isChecked)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMaxZoomPreference(21f)
        mMap.setMinZoomPreference(3f)
        mMap.setOnCameraIdleListener(this)
        mMap.isMyLocationEnabled = true
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
    }

    override fun onCameraIdle() {
        mapViewModel.updateRegion(mMap.projection.visibleRegion.latLngBounds, mMap.cameraPosition.zoom.toInt())
    }
}

