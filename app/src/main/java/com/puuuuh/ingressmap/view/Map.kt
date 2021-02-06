package com.puuuuh.ingressmap.view

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.getbase.floatingactionbutton.FloatingActionButton
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.settings.ColorType
import com.puuuuh.ingressmap.settings.FullPosition
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.PortalIcons
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
    private var hideTeams: Boolean = true
    private var saveLinksIntent =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            if (it != null)
                mapViewModel.saveLinks(it)
        }
    private lateinit var icons: HashMap<String, BitmapDescriptor>
    private var loadLinksIntent = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null)
            mapViewModel.loadLinks(it)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mapViewModel.moveCamera(Settings.lastPosition)
        if (savedInstanceState != null)
            return null

        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Settings.myLocation) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                    Settings.myLocation = false
                }
            }.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }

        val map = childFragmentManager.findFragmentById(R.id.map)
        (map as SupportMapFragment).getMapAsync(this)

        view.findViewById<FloatingActionButton>(R.id.saveFab).setOnClickListener {
            saveLinksIntent.launch("links.txt")
        }
        view.findViewById<FloatingActionButton>(R.id.openFab).setOnClickListener {
            loadLinksIntent.launch("*/*")
        }

        view.findViewById<FloatingActionButton>(R.id.drawFab).backgroundTintMode =
            if (Settings.drawMode) {
                PorterDuff.Mode.XOR
            } else {
                null
            }

        view.findViewById<FloatingActionButton>(R.id.drawFab).setOnClickListener {
            Settings.drawMode = !Settings.drawMode

            view.findViewById<FloatingActionButton>(R.id.drawFab).backgroundTintMode =
                if (Settings.drawMode) {
                    PorterDuff.Mode.XOR
                } else {
                    null
                }
        }
    }

    private fun getIcon(portal: GameEntity.Portal): BitmapDescriptor {
        val team = if (!hideTeams) {
            portal.data.team +
                    if (portal.data.specials.contains("sc5_p")) {
                        "-Marked"
                    } else {
                        ""
                    } +
                    if (portal.data.unique) {
                        "-Unique"
                    } else {
                        ""
                    }
        } else {
            "N"
        }
        return icons[team]!!
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMaxZoomPreference(21f)
        mMap.setOnMarkerClickListener(this)
        mMap.setOnPolylineClickListener(this)
        mMap.setMinZoomPreference(3f)
        mMap.setOnCameraIdleListener(this)
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        enableMyLocation()

        icons = hashMapOf(
            Pair("E", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(ColorType.Center, "E"), Color.TRANSPARENT))),
            Pair("R", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(ColorType.Center, "R"), Color.TRANSPARENT))),
            Pair("N", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(ColorType.Center, "N"), Color.TRANSPARENT))),
            Pair("E-Marked", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(ColorType.Center, "E"), Settings.getColor(ColorType.Volatile, "E")))),
            Pair("R-Marked", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(ColorType.Center, "R"), Settings.getColor(ColorType.Volatile, "R")))),
            Pair("N-Marked", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(ColorType.Center, "N"), Settings.getColor(ColorType.Volatile, "N")))),

            Pair("E-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(ColorType.CenterUnique, "E"), Color.TRANSPARENT))),
            Pair("R-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(ColorType.CenterUnique, "R"), Color.TRANSPARENT))),
            Pair("N-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(ColorType.CenterUnique, "N"), Color.TRANSPARENT))),

            Pair("E-Marked-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "E"), Settings.getColor(ColorType.CenterUnique, "E"), Settings.getColor(ColorType.Volatile, "E")))),
            Pair("R-Marked-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "R"), Settings.getColor(ColorType.CenterUnique, "R"), Settings.getColor(ColorType.Volatile, "R")))),
            Pair("N-Marked-Unique", BitmapDescriptorFactory.fromBitmap(PortalIcons.createIcon(Settings.getColor(ColorType.Main, "N"), Settings.getColor(ColorType.CenterUnique, "N"), Settings.getColor(ColorType.Volatile, "N")))),
        )

        mapViewModel.targetPosition.observe(viewLifecycleOwner, {
            if (it.lat != 0.0 && it.lng != 0.0) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), it.zoom))
                mapViewModel.moveCamera(FullPosition(0.0, 0.0, 0.0f))
            }
        })

        mapViewModel.portals.observe(viewLifecycleOwner, {
            val newPortals = mutableMapOf<String, Pair<Marker, Circle?>>()
            for (i in it) {
                val old = portals.remove(i.key)

                val iconRes = getIcon(i.value)

                val pos = LatLng(i.value.data.lat, i.value.data.lng)
                if (old == null) {
                    val m = MarkerOptions()
                        .position(pos)
                        .title(i.value.data.name)
                        .icon(iconRes)
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
                        old.first.setIcon(iconRes)
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

        Settings.liveHideTeams.observe(viewLifecycleOwner, { data ->
            hideTeams = data
            portals.forEach { portal ->
                portal.value.first.setIcon(getIcon(portal.value.first.tag as GameEntity.Portal))
            }
        })

        /*floatingActionButton.setOnClickListener {
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
        }*/
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