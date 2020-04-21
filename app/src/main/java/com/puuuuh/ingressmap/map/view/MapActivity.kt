package com.puuuuh.ingressmap.map.view

import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.map.viewmodel.MapViewModel
import com.puuuuh.ingressmap.map.viewmodel.UserData
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.common.geometry.*
import kotlinx.android.synthetic.main.activity_map.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback,  GoogleMap.OnCameraIdleListener {
    private lateinit var mMap: GoogleMap
    private var lines = hashMapOf<S2CellId, Polyline>()
    private val portals = hashMapOf<String, Marker>()
    private lateinit var mapViewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        val user = UserData(token=intent.getStringExtra("token"), csrfToken=intent.getStringExtra("csrfToken"))
        mapViewModel = MapViewModel(user)
        mapViewModel.portals.observe(this, androidx.lifecycle.Observer {
            for (i in it) {
                if (!portals.containsKey(i.key)) {
                    val resource = if (i.value.team == "E") {R.drawable.ic_green_portal} else {R.drawable.ic_blue_portal}
                    val point = LatLng(i.value.lat, i.value.lng)
                    val m = MarkerOptions()
                        .position(point)
                        .title(i.value.name)
                        .icon(BitmapDescriptorFactory.fromResource(resource))
                        .anchor(0.5f, 0.5f)
                        .visible(false)
                    portals[i.key] = mMap.addMarker(m)
                }
            }
            val viewport = mMap.projection.visibleRegion.latLngBounds
            val portalsEnabled = mMap.cameraPosition.zoom > 12
            for (p in portals) {
                p.value.isVisible = portalsEnabled && viewport.contains(p.value.position)
            }
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
        autocomplete.setPlaceFields(listOf(Place.Field.LAT_LNG, Place.Field.NAME))
        autocomplete.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                mMap.animateCamera(CameraUpdateFactory.newLatLng(place.latLng))
            }
            override fun onError(status: Status) {}
        })
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
        val portalsEnabled = mMap.cameraPosition.zoom > 15
        val viewport = mMap.projection.visibleRegion.latLngBounds
        for (p in portals) {
            p.value.isVisible = portalsEnabled && viewport.contains(p.value.position)
        }
    }
}
