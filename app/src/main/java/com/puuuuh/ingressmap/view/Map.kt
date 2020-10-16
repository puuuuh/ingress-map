package com.puuuuh.ingressmap.view

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.repository.Portal
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.android.synthetic.main.fragment_map.view.*

class Map : Fragment(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener,
    GoogleMap.OnMarkerClickListener, GoogleMap.OnPolylineClickListener {
    private val mapViewModel: MapViewModel by activityViewModels { ViewmodelFactory(this.requireContext()) }
    private lateinit var mMap: GoogleMap
    private var lines = hashMapOf<S2CellId, Polyline>()
    private var portals = mutableMapOf<String, Pair<Marker, Circle?>>()
    private var fields = mutableMapOf<String, Polygon>()
    private var links = mutableMapOf<String, Polyline>()
    private var customLinks = mutableMapOf<String, Polyline>()
    private var customFields = mutableMapOf<String, Polygon>()
    private var selectedPoint: Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        // Start the autocomplete intent.
        val call =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        val data = Autocomplete.getPlaceFromIntent(result.data!!)
                        val pos = CameraPosition.Builder()
                            .zoom(15f)
                            .target(data.latLng)
                            .build()
                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(pos))
                    }
                }
            }
        view.fab.setOnClickListener { _ ->
            val intent =
                context?.let {
                    Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.FULLSCREEN,
                        listOf(Place.Field.LAT_LNG)
                    )
                        .build(it)
                }
            call.launch(intent)
        }

        if (Settings.myLocation) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
                    Settings.myLocation = false
                }
            }.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }

        val map = childFragmentManager.findFragmentById(R.id.map)
        (map as SupportMapFragment).getMapAsync(this)
        return view
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
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        enableMyLocation()

        mapViewModel.portals.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            val newPortals = mutableMapOf<String, Pair<Marker, Circle?>>()
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
                    newPortals[i.key] = Pair(
                        mMap.addMarker(m),
                        if (mMap.cameraPosition.zoom > 16) {
                            val oreol = CircleOptions()
                                .center(pos)
                                .radius(20.0)
                                .fillColor(0x77303030)
                                .strokeWidth(0.0F)
                            mMap.addCircle(oreol)
                        } else {
                            null
                        }
                    )

                } else {
                    if (old.first.position != pos) {
                        old.second?.center = pos
                        old.first.position = pos
                    }
                    if ((old.first.tag as Portal).team != i.value.team) {
                        old.first.setIcon(BitmapDescriptorFactory.fromResource(iconRes))
                    }
                    if (mMap.cameraPosition.zoom > 16 && old.second == null) {
                        val oreol = CircleOptions()
                            .center(pos)
                            .radius(20.0)
                            .fillColor(0x77303030)
                            .strokeWidth(0.0F)
                        newPortals[i.key] = Pair(old.first, mMap.addCircle(oreol))
                    } else if (mMap.cameraPosition.zoom <= 16 && old.second != null) {
                        old.second!!.remove()
                        newPortals[i.key] = Pair(old.first, null)
                    } else {
                        newPortals[i.key] = old
                    }
                }
                newPortals[i.key]!!.first.tag = i.value
            }
            for (l in portals) {
                l.value.first.remove()
                l.value.second?.remove()
            }
            portals = newPortals
        })
        mapViewModel.links.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
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
        mapViewModel.fields.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
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
        mapViewModel.cellLines.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
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

        mapViewModel.status.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            statusView.text = "Status: " + if (it.requestsInProgress == 0) {
                "up to date"
            } else {
                "${it.requestsInProgress} request in progress, please, wait..."
            }
        })

        mapViewModel.customLines.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
            val newLinks = mutableMapOf<String, Polyline>()
            for (i in data) {
                val old = customLinks.remove(i.key)
                val color = Color.RED

                if (old == null) {
                    val m = i.value
                        .width(4f)
                        .zIndex(4f)
                        .color(color)
                        .clickable(true)
                    val line = mMap.addPolyline(m)
                    line.tag = i.key
                    newLinks[i.key] = line
                } else {
                    newLinks[i.key] = old
                }
            }
            customLinks.map {
                it.value.remove()
            }
            customLinks = newLinks
        })

        mapViewModel.selectedPortal.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { data ->
                if (data != null) {
                    val fm = childFragmentManager
                    val dlg = PortalInfo.newInstance(data)
                    dlg.show(fm, "fragment_alert")
                    mapViewModel.selectPortal(null)
                }
            })

        mapViewModel.selectedPoint.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
            selectedPoint?.remove()
            if (data != null) {
                selectedPoint = mMap.addMarker(
                    MarkerOptions()
                        .position(data)
                )
            }
        })

        mapViewModel.customFields.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
            val new = mutableMapOf<String, Polygon>()
            for (i in data) {
                val old = customFields.remove(i.key)
                if (old == null) {
                    val t = PolygonOptions()
                        .add(i.value[0])
                        .add(i.value[1])
                        .add(i.value[2])
                        .strokeWidth(4f)
                        .fillColor(Color.argb(100, 200, 0, 0))

                    new[i.key] = mMap.addPolygon(t)
                } else {
                    new[i.key] = old
                }
            }
            customFields.map {
                it.value.remove()
            }
            customFields = new
        })
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mMap.isMyLocationEnabled = true
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
        mapViewModel.removeCustomLine(p0.tag as String)
        return
    }
}