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
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.toGeoPoint
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

        mapViewModel.targetPosition.observe(viewLifecycleOwner, {
            if (it.longitude != 0.0 && it.latitude != 0.0) {
                mMap.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                mMap.controller.setZoom(17.toDouble())
            }
        })

        mapViewModel.portals.observe(viewLifecycleOwner, {
            val next = FolderOverlay()
            for (i in it) {
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
                val point = Marker(mMap)
                point.relatedObject = i.value
                point.setOnMarkerClickListener(this)
                point.icon = ResourcesCompat.getDrawable(resources, iconRes, null)
                point.setAnchor(0.5f, 0.5f)
                point.position = GeoPoint(i.value.data.lat, i.value.data.lng)
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
        mapViewModel.links.observe(viewLifecycleOwner, {
            val next = FolderOverlay()
            for (i in it) {
                val line = Polyline(mMap)
                line.infoWindow = null
                line.color = if (i.value.data.team == "E") {
                    Color.GREEN
                } else {
                    Color.BLUE
                }
                line.width = 2f
                line.addPoint(
                    i.value.data.points.first.toGeoPoint()
                )
                line.addPoint(
                    i.value.data.points.second.toGeoPoint()
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
        mapViewModel.fields.observe(viewLifecycleOwner, {
            val next = FolderOverlay()
            for (i in it) {

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
                val polygon = Polygon(mMap)
                polygon.infoWindow = null
                val color = if (i.value.data.team == "E") {
                    Color.argb(100, 0, 255, 0)
                } else {
                    Color.argb(100, 0, 0, 255)
                }
                val strokeColor = if (i.value.data.team == "E") {
                    Color.GREEN
                } else {
                    Color.BLUE
                }

                polygon.fillColor = color
                polygon.strokeColor = strokeColor
                polygon.strokeWidth = 2f
                polygon.addPoint(
                    i.value.data.points.first.toGeoPoint()
                )
                polygon.addPoint(
                    i.value.data.points.second.toGeoPoint()
                )
                polygon.addPoint(
                    i.value.data.points.third.toGeoPoint()
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
        mapViewModel.cellLines.observe(viewLifecycleOwner, { data ->
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

        mapViewModel.status.observe(viewLifecycleOwner, {
            statusView.text = if (it.requestsInProgress == 0) {
                getString(R.string.status_ok)
            } else {
                String.format(getString(R.string.status_in_progress), it.requestsInProgress)
            }
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
        if (marker?.relatedObject is PortalData) {
            mapViewModel.selectPortal(marker.relatedObject as GameEntity.Portal)
            return true
        }
        return true
    }
}