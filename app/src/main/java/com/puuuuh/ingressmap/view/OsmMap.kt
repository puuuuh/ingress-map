package com.puuuuh.ingressmap.view

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.transition.Visibility
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.puuuuh.ingressmap.BuildConfig
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.PortalData
import com.puuuuh.ingressmap.settings.ColorType
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.PortalIcons
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
    private var hideTeams: Boolean = true
    private lateinit var icons: HashMap<String, Drawable>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mapViewModel.moveCamera(Settings.lastPosition)
        if (savedInstanceState != null)
            return null

        return inflater.inflate(R.layout.fragment_osmmap, container, false)
    }

    private fun getIcon(portal: GameEntity.Portal): Drawable {
        val team = if (!hideTeams) {
            portal.data.team +
                    if (portal.data.specials.contains("sc5_p")) {
                        "-Marked"
                    } else {
                        ""
                    }
        } else {
            "N"
        }
        return icons[team]!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Settings.myLocation) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
                    Settings.myLocation = false
                } else {
                    myLocationBtn.isVisible = true

                    mMyLocation = MyLocationNewOverlay(GpsMyLocationProvider(context), mMap)
                    mMyLocation.enableMyLocation()
                    myLocationBtn.setOnClickListener {
                        mMyLocation.enableFollowLocation()
                    }

                    mMap.overlays.add(mMyLocation)
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

        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.addMapListener(DelayedMapListener(this, 500))

        var prev: FolderOverlay? = null

        icons = hashMapOf(
            Pair("E", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(
                ColorType.Center, "E"), Color.TRANSPARENT).toDrawable(resources)),
            Pair("R", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(
                ColorType.Center, "R"), Color.TRANSPARENT).toDrawable(resources)),
            Pair("N", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(
                ColorType.Center, "N"), Color.TRANSPARENT).toDrawable(resources)),
            Pair("E-Marked", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(
                ColorType.Center, "E"), Settings.getColor(ColorType.Volatile, "E")).toDrawable(resources)),
            Pair("R-Marked", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(
                ColorType.Center, "R"), Settings.getColor(ColorType.Volatile, "R")).toDrawable(resources)),
            Pair("N-Marked", PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(
                ColorType.Center, "N"), Settings.getColor(ColorType.Volatile, "N")).toDrawable(resources)),
        )

        Settings.liveHideTeams.observe(viewLifecycleOwner) {
            hideTeams = it
        }

        mapViewModel.targetPosition.observe(viewLifecycleOwner, {
            if (it.lat != 0.0 && it.lng != 0.0) {
                mMap.controller.setCenter(GeoPoint(it.lat, it.lng))
                mMap.controller.setZoom(it.zoom.toDouble())
            }
        })

        mapViewModel.portals.observe(viewLifecycleOwner, {
            val next = FolderOverlay()
            for (i in it) {
                val point = Marker(mMap)
                point.relatedObject = i.value
                point.setOnMarkerClickListener(this)
                point.icon = getIcon(i.value)
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
        if (marker?.relatedObject is GameEntity.Portal) {
            mapViewModel.selectPortal(marker.relatedObject as GameEntity.Portal)
            return true
        }
        return true
    }
}