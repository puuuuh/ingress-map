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

interface TaskListener {
    fun onFinished(result: HashMap<S2CellId, Polyline>)
}

class MapActivity : AppCompatActivity(), OnMapReadyCallback,  GoogleMap.OnCameraIdleListener,
    TaskListener {
    private lateinit var mMap: GoogleMap
    private var updateTask: RedrawTask? = null
    private lateinit var lines: HashMap<S2CellId, Polyline>
    private lateinit var portals: HashMap<String, Marker>
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
            for (p in portals) {
                p.value.isVisible = viewport.contains(p.value.position)
            }
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
        lines = HashMap()
        portals = HashMap()
        mMap.setMaxZoomPreference(21f)
        mMap.setMinZoomPreference(3f)
        mMap.setOnCameraIdleListener(this)
        mMap.isMyLocationEnabled = true
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
    }

    override fun onCameraIdle() {
        updateTask?.cancel(true)
        updateTask = RedrawTask(
            mMap,
            this
        )
        updateTask!!.execute(
            TaskArg(
                mMap.cameraPosition,
                mMap.projection.visibleRegion,
                lines
            )
        )

        mapViewModel.updateRegion(mMap.projection.visibleRegion.latLngBounds, mMap.cameraPosition.zoom.toInt())
    }

    override fun onFinished(result: HashMap<S2CellId, Polyline>) {
        lines = result
        val portalsEnabled = mMap.cameraPosition.zoom > 12
        val viewport = mMap.projection.visibleRegion.latLngBounds
        for (p in portals) {
            p.value.isVisible = portalsEnabled && viewport.contains(p.value.position)
        }
    }

    class TaskArg(val pos: CameraPosition, val viewport: VisibleRegion, var lines: HashMap<S2CellId, Polyline>) {}

    class TaskRes {
        var new14 = HashMap<S2CellId, PolylineOptions>()
        var new17 = HashMap<S2CellId, PolylineOptions>()
        var lines = HashMap<S2CellId, Polyline>()
        var rm = HashMap<S2CellId, Polyline>()
    }

    class RedrawTask(map: GoogleMap, onResult: TaskListener) :
        AsyncTask<TaskArg, Any?, TaskRes>() {


        override fun doInBackground(params: Array<TaskArg>): TaskRes {
            val arg = params[0]
            val lines = arg.lines
            val r = TaskRes()
            r.lines = lines.clone() as HashMap<S2CellId, Polyline>

            val bounds = arg.viewport.latLngBounds
            val northeast = S2LatLng.fromDegrees(bounds.northeast.latitude, bounds.northeast.longitude)
            val southwest = S2LatLng.fromDegrees(bounds.southwest.latitude, bounds.southwest.longitude)
            val rect = S2LatLngRect.fromPointPair(northeast, southwest)

            val cells14 = ArrayList<S2CellId>()
            val cells17 = ArrayList<S2CellId>()

            if (arg.pos.zoom > 12)
                S2RegionCoverer.getSimpleCovering(rect, rect.center.toPoint(), 14, cells14)
            if (arg.pos.zoom >= 14.9)
                S2RegionCoverer.getSimpleCovering(rect, rect.center.toPoint(), 17, cells17)

            val newLines = HashMap<S2CellId, Polyline>()
            for (cellId in cells17) {
                if (lines.contains(cellId)) {
                    newLines[cellId] = lines[cellId]!!
                    lines.remove(cellId)
                    continue
                }
                val line = PolylineOptions()
                val c = S2Cell(cellId)
                for (i in 0..2) {
                    val p = S2LatLng(c.getVertex(i))
                    line.add(LatLng(p.lat().degrees(), p.lng().degrees()))
                }
                r.new17[cellId] = line
            }

            for (cellId in cells14) {
                if (lines.contains(cellId)) {
                    newLines[cellId] = lines[cellId]!!
                    lines.remove(cellId)
                    continue
                }
                val line = PolylineOptions()
                val c = S2Cell(cellId)
                for (i in 0..2) {
                    val p = S2LatLng(c.getVertex(i))
                    line.add(LatLng(p.lat().degrees(), p.lng().degrees()))
                }
                r.new14[cellId] = line
            }


            r.rm = lines
            return r
        }

        override fun onPostExecute(result: TaskRes) {
            for (i in result.rm) {
                result.lines.remove(i.key)
                i.value.remove()
            }
            for (i in result.new14) {
                val l = i.value
                    .color(Color.rgb(239, 70, 15))
                    .width(8f)
                    .zIndex(2f)
                result.lines[i.key] = mMap.addPolyline(l)
            }
            for (i in result.new17) {
                val l = i.value
                    .color(Color.rgb(8, 152, 152))
                    .width(4f)
                result.lines[i.key] = mMap.addPolyline(l)
            }
            taskListener.onFinished(result.lines)
        }



        companion object {
            private lateinit var mMap: GoogleMap;
            private lateinit var taskListener: TaskListener
        }

        init {
            mMap = map
            taskListener = onResult
        }
    }
}
