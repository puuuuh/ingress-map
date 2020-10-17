package com.puuuuh.ingressmap.view

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.puuuuh.ingressmap.BuildConfig
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.repository.Portal
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.viewmodel.MapViewModel
import com.puuuuh.ingressmap.viewmodel.ViewmodelFactory
import kotlinx.android.synthetic.main.fragment_osmmap.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.security.InvalidParameterException

class OsmMap : Fragment(), MapListener, Marker.OnMarkerClickListener {
    private val mapViewModel: MapViewModel by activityViewModels { ViewmodelFactory(this.requireContext()) }
    private lateinit var mMyLocation: MyLocationNewOverlay
    private lateinit var mMap: MapView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_osmmap, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Settings.myLocation) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
                    Settings.myLocation = false
                }
            }.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        mMap = map
        mMap.tilesScaleFactor = 2.3f
        mMap.maxZoomLevel = 21.toDouble()
        mMap.minZoomLevel = 3.toDouble()
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(Settings.lastZoom.toDouble())
        mMyLocation = MyLocationNewOverlay(GpsMyLocationProvider(context), mMap)
        mMap.overlays.add(mMyLocation)
        mMyLocation.enableMyLocation()
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.addMapListener(DelayedMapListener(this, 500))

        var prev: FolderOverlay? = null

        mapViewModel.targetPosition.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            mMap.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            mMap.controller.setZoom(17.toDouble())
        })

        mapViewModel.portals.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            val next = FolderOverlay()
            for (i in it) {
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
                val point = Marker(mMap)
                point.relatedObject = i.value
                point.setOnMarkerClickListener(this)
                point.icon = ResourcesCompat.getDrawable(resources, iconRes, null)
                point.setAnchor(0.5f, 0.5f)
                point.position = GeoPoint(i.value.lat, i.value.lng)
                point.infoWindow = null
                next.add(point)
            }

            if (prev != null) {
                mMap.overlays.remove(prev)
            }
            mMap.overlays.add(next)
            prev = next
            mMap.invalidate()
        })

        var prevLinks: FolderOverlay? = null
        mapViewModel.links.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            val next = FolderOverlay()
            for (i in it) {
                val line = Polyline(mMap)
                line.infoWindow = null
                line.color = if (i.value.team == "E") {
                    Color.GREEN
                } else {
                    Color.BLUE
                }
                line.width = 2f
                line.addPoint(
                    GeoPoint(
                        i.value.points[0].LatLng.latitude,
                        i.value.points[0].LatLng.longitude
                    )
                )
                line.addPoint(
                    GeoPoint(
                        i.value.points[1].LatLng.latitude,
                        i.value.points[1].LatLng.longitude
                    )
                )
                next.add(line)
            }

            if (prevLinks != null) {
                mMap.overlays.remove(prevLinks)
            }
            mMap.overlays.add(next)
            prevLinks = next
            mMap.invalidate()
        })

        var prevFields: FolderOverlay? = null
        mapViewModel.fields.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            val next = FolderOverlay()
            for (i in it) {

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
                val polygon = Polygon(mMap)
                polygon.infoWindow = null
                val color = if (i.value.team == "E") {
                    Color.argb(100, 0, 255, 0)
                } else {
                    Color.argb(100, 0, 0, 255)
                }
                val strokeColor = if (i.value.team == "E") {
                    Color.GREEN
                } else {
                    Color.BLUE
                }

                polygon.fillColor = color
                polygon.strokeColor = strokeColor
                polygon.strokeWidth = 2f
                polygon.addPoint(
                    GeoPoint(
                        i.value.points[0].LatLng.latitude,
                        i.value.points[0].LatLng.longitude
                    )
                )
                polygon.addPoint(
                    GeoPoint(
                        i.value.points[1].LatLng.latitude,
                        i.value.points[1].LatLng.longitude
                    )
                )
                polygon.addPoint(
                    GeoPoint(
                        i.value.points[2].LatLng.latitude,
                        i.value.points[2].LatLng.longitude
                    )
                )

                next.add(polygon)
            }

            if (prevFields != null) {
                mMap.overlays.remove(prevFields)
            }
            mMap.overlays.add(next)
            prevFields = next
            mMap.invalidate()
        })

        var prevCells: FolderOverlay? = null
        mapViewModel.cellLines.observe(viewLifecycleOwner, androidx.lifecycle.Observer { data ->
            val next = FolderOverlay()

            for ((i, opts) in data) {
                val line = Polyline(mMap)
                line.infoWindow = null
                when (i.level()) {
                    14 -> {
                        line.color = Color.rgb(239, 70, 15)
                        line.width = 8f
                    }
                    17 -> {
                        line.color = Color.rgb(8, 152, 152)
                        line.width = 4f
                    }
                    else -> {
                        throw InvalidParameterException()
                    }
                }
                line.addPoint(GeoPoint(opts.points[0].latitude, opts.points[0].longitude))
                line.addPoint(GeoPoint(opts.points[1].latitude, opts.points[1].longitude))
                line.addPoint(GeoPoint(opts.points[2].latitude, opts.points[2].longitude))
                next.add(line)
            }

            if (prevCells != null) {
                mMap.overlays.remove(prevCells)
            }
            mMap.overlays.add(next)
            prevCells = next
            mMap.invalidate()
        })
    }

    override fun onScroll(e: ScrollEvent?): Boolean {
        mapViewModel.updatePosition(
            LatLng(mMap.mapCenter.latitude, mMap.mapCenter.longitude),
            LatLngBounds(
                LatLng(mMap.boundingBox.latSouth, mMap.boundingBox.lonWest),
                LatLng(mMap.boundingBox.latNorth, mMap.boundingBox.lonEast)
            ),
            mMap.zoomLevel
        )
        return false
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        mapViewModel.updatePosition(
            LatLng(mMap.mapCenter.latitude, mMap.mapCenter.longitude),
            LatLngBounds(
                LatLng(mMap.boundingBox.latSouth, mMap.boundingBox.lonWest),
                LatLng(mMap.boundingBox.latNorth, mMap.boundingBox.lonEast)
            ),
            mMap.zoomLevel
        )
        return false
    }

    override fun onMarkerClick(marker: Marker?, mapView: MapView?): Boolean {
        if (marker?.relatedObject is Portal) {
            mapViewModel.selectPortal(marker.relatedObject as Portal)
            return true
        }
        return true
    }
}