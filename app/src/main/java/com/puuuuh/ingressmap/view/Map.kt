package com.puuuuh.ingressmap.view

import android.Manifest
import android.content.Intent
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
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.toLatLng
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.fragment_map.*

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

        if (Settings.myLocation) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
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
        mMap.setOnCameraIdleListener(this)
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        enableMyLocation()

        mapViewModel.targetPosition.observe(viewLifecycleOwner, {
            if (it.longitude != 0.0 && it.latitude != 0.0)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
        })

        mapViewModel.portals.observe(viewLifecycleOwner, {
            val newPortals = mutableMapOf<String, Pair<Marker, Circle?>>()
            for (i in it) {
                val old = portals.remove(i.key)
                val iconRes = when (i.value.data.team) {
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
                val pos = LatLng(i.value.data.lat, i.value.data.lng)
                if (old == null) {
                    val m = MarkerOptions()
                        .position(pos)
                        .title(i.value.data.name)
                        .icon(BitmapDescriptorFactory.fromResource(iconRes))
                        .zIndex(2f)
                        .anchor(0.5f, 0.5f)
                    newPortals[i.key] = Pair(
                        mMap.addMarker(m),
                        if (mMap.cameraPosition.zoom > 18) {
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
                    if ((old.first.tag as GameEntity.Portal).data.team != i.value.data.team) {
                        old.first.setIcon(BitmapDescriptorFactory.fromResource(iconRes))
                    }
                    if (mMap.cameraPosition.zoom > 18 && old.second == null) {
                        val oreol = CircleOptions()
                            .center(pos)
                            .radius(20.0)
                            .fillColor(0x77303030)
                            .strokeWidth(0.0F)
                        newPortals[i.key] = Pair(old.first, mMap.addCircle(oreol))
                    } else if (mMap.cameraPosition.zoom <= 18 && old.second != null) {
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
        mapViewModel.links.observe(viewLifecycleOwner, { data ->
            val newLinks = mutableMapOf<String, Polyline>()
            for (i in data) {
                val old = links.remove(i.key)
                val color = if (i.value.data.team == "E") {
                    Color.argb(255, 0, 255, 0)
                } else {
                    Color.argb(255, 0, 140, 255)
                }

                if (old == null) {
                    val m = PolylineOptions()
                        .add(
                            LatLng(i.value.data.points.first.lat, i.value.data.points.first.lng),
                            LatLng(i.value.data.points.second.lat, i.value.data.points.second.lng)
                        )
                        .width(2f)
                        .zIndex(3f)
                        .color(color)

                    newLinks[i.key] = mMap.addPolyline(m)
                } else {
                    if (old.color != color) {
                        old.color = color
                    }
                    if (old.points[0].latitude != i.value.data.points.first.lat ||
                        old.points[0].longitude != i.value.data.points.first.lng
                    ) {
                        old.points[0] =
                            LatLng(i.value.data.points.first.lat, i.value.data.points.first.lng)
                    }
                    if (old.points[1].latitude != i.value.data.points.second.lat ||
                        old.points[1].longitude != i.value.data.points.second.lng
                    ) {
                        old.points[1] =
                            LatLng(i.value.data.points.second.lat, i.value.data.points.second.lng)
                    }
                    newLinks[i.key] = old
                }
            }
            for (l in links) {

                l.value.remove()
            }
            links = newLinks
        })
        mapViewModel.fields.observe(viewLifecycleOwner, { data ->
            val newFields = mutableMapOf<String, Polygon>()
            for (i in data) {
                val old = fields.remove(i.key)
                if (old == null) {
                    val color = if (i.value.data.team == "E") {
                        Color.argb(100, 0, 255, 0)
                    } else {
                        Color.argb(100, 0, 0, 255)
                    }
                    val strokeColor = if (i.value.data.team == "E") {
                        Color.argb(255, 0, 255, 0)
                    } else {
                        Color.argb(255, 0, 140, 255)
                    }
                    val m = PolygonOptions()
                        .add(
                            i.value.data.points.first.toLatLng(),
                            i.value.data.points.second.toLatLng(),
                            i.value.data.points.third.toLatLng(),
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
        mapViewModel.cellLines.observe(viewLifecycleOwner, { data ->
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

        mapViewModel.status.observe(viewLifecycleOwner, {
            statusView.text = if (it.requestsInProgress == 0) {
                getString(R.string.status_ok)
            } else {
                String.format(getString(R.string.status_in_progress), it.requestsInProgress)
            }
        })

        mapViewModel.customLines.observe(viewLifecycleOwner, { data ->
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

        mapViewModel.selectedPoint.observe(viewLifecycleOwner, { data ->
            selectedPoint?.remove()
            if (data != null) {
                selectedPoint = mMap.addMarker(
                    MarkerOptions()
                        .position(data)
                )
            }
        })

        mapViewModel.customFields.observe(viewLifecycleOwner, { data ->
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

        floatingActionButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    String.format(
                        "http://intel.ingress.com/intel?ll=%f,%f&z=%d",
                        mMap.cameraPosition.target.latitude,
                        mMap.cameraPosition.target.longitude,
                        mMap.cameraPosition.zoom.toInt()
                    )
                )
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
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
        if (this::mMap.isInitialized) {
            mMap.isMyLocationEnabled = true
        }
    }

    override fun onCameraIdle() {
        mapViewModel.updatePosition(
            mMap.cameraPosition.target,
            mMap.projection.visibleRegion.latLngBounds,
            mMap.cameraPosition.zoom.toInt()
        )
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        if (p0?.tag is GameEntity.Portal) {
            mapViewModel.selectPortal(p0.tag as GameEntity.Portal)
            return true
        }

        return true
    }

    override fun onPolylineClick(p0: Polyline) {
        mapViewModel.removeCustomLine(p0.tag as String)
        return
    }


}