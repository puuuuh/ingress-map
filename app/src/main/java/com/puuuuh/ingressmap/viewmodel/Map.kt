package com.puuuuh.ingressmap.viewmodel

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.repository.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.*

data class CellData(val portals: Map<String, Portal>, val links: Map<String, Link>, val fields: Map<String, Field>)

data class Status(val requestsInProgress: Int)

private fun getXYTile(lat : Double, lng: Double, zoom : Int) : Pair<Int, Int> {
    val tileCounts = arrayOf(1,1,1,40,40,80,80,320,1000,2000,2000,4000,8000,16000,16000,32000)
    val latRad = Math.toRadians(lat)

    val cnt = if (zoom >= tileCounts.size) {tileCounts[tileCounts.size - 1]} else {tileCounts[zoom]}
    val x = (((lng + 180.0) / 360.0) * cnt).toInt()
    val y = ((1.0 - log(tan(latRad) + (1 / cos(latRad)), E) / PI) / 2.0 * cnt).toInt()
    return Pair(x, y)
}

class MapViewModel(val context: Context) : ViewModel(), OnDataReadyCallback, OnCellsReadyCallback {
    private val ingressRepo = IngressApiRepo()
    private val cellsRepo = S2CellsRepo()
    private val handler = Handler(context.mainLooper)

    // All cached entities
    private val allPortals = mutableMapOf<String, Portal>()
    private val allLinks = mutableMapOf<String, Link>()
    private val allFields = mutableMapOf<String, Field>()

    // Visibility
    private var showPortal = false
    private var showLinks = false
    private var showFields = false
    private var drawMode = false

    // Current viewport
    private var viewport = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    private var zoom = 21

    private val _portals = MutableLiveData<Map<String, Portal>>()
    val portals: LiveData<Map<String, Portal>> = _portals

    private val _cellLines = MutableLiveData<Map<S2CellId, PolylineOptions>>()
    val cellLines: LiveData<Map<S2CellId, PolylineOptions>> = _cellLines

    private val _links = MutableLiveData<Map<String, Link>>()
    val links: LiveData<Map<String, Link>> = _links

    private val _fields = MutableLiveData<Map<String, Field>>()
    val fields: LiveData<Map<String, Field>> = _fields

    private val _status = MutableLiveData<Status>()
    val status: LiveData<Status> = _status

    private val _customLines = MutableLiveData<Set<PolylineOptions>>()
    val customLines: LiveData<Set<PolylineOptions>> = _customLines

    private val _selectedPoint = MutableLiveData<LatLng?>()
    val selectedPoint: LiveData<LatLng?> = _selectedPoint

    private val _selectedPortal = MutableLiveData<Portal>()
    val selectedPortal: LiveData<Portal?> = _selectedPortal

    private val _cellCache = mutableMapOf<String, CellData>()

    fun updateRegion(r: LatLngBounds, z: Int) {
        viewport = r
        zoom = z

        GlobalScope.launch {
            updateCellsInRegion(r, z)
            cellsRepo.getCells(r, z, this@MapViewModel)
            updateVisibleLinks()
            updateVisiblePortals()
            updateVisibleFields()
        }
    }

    private fun updateVisibleFields() {
        val new = if (!showFields || zoom < 13) {
            mapOf()
        } else {
            allFields.filter { field -> field.value.bounds.intersects(viewport) }
        }
        if (new != _fields.value)
            _fields.postValue(new)
    }

    private fun updateVisibleLinks() {
        val new = if (!showLinks || zoom < 14) {
            mapOf()
        } else {
            allLinks.filter { link ->  link.value.bounds.intersects(viewport) }
        }
        if (new != _links.value)
            _links.postValue(new)
    }

    private fun LatLngBounds.intersects(viewport: LatLngBounds): Boolean {
        val thisNorthWest = LatLng(this.northeast.latitude, this.southwest.longitude)
        val thisSouthEast = LatLng(this.southwest.latitude, this.northeast.longitude)
        val vpNorthWest = LatLng(viewport.northeast.latitude, viewport.southwest.longitude)
        val vpSouthEast = LatLng(viewport.southwest.latitude, viewport.northeast.longitude)


        if (viewport.contains(this.northeast) || viewport.contains(this.southwest) ||
                viewport.contains(thisNorthWest) || viewport.contains(thisSouthEast) ||
                this.contains(viewport.northeast) || this.contains(viewport.southwest) ||
                this.contains(vpNorthWest) || this.contains(vpSouthEast)) {
            return true
        }

        val vpLeft = viewport.southwest.longitude
        val myLeft = this.southwest.longitude
        var vpRight = viewport.northeast.longitude
        var myRight = this.northeast.longitude

        val vpUp = viewport.northeast.latitude
        val myUp = this.northeast.latitude
        val vpDown = viewport.southwest.latitude
        val myDown = this.southwest.latitude

        if (myRight < myLeft) {
            myRight += 360
        }
        if (vpRight < vpLeft) {
            vpRight += 360
        }

        if (myUp < vpUp &&
            myDown > vpDown) {
            return myLeft < vpLeft &&
                    myRight > vpRight
        }

        if (myLeft > vpLeft &&
            myRight < vpRight) {
            return myUp > vpUp &&
                    myDown < vpDown
        }

        return false
    }

    private fun updateVisiblePortals() {
        val new = if (!showPortal || zoom < 13.5) {
            mapOf()
        } else {
            allPortals.filter { viewport.contains(LatLng(it.value.lat, it.value.lng)) }
        }
        if (new != _portals.value)
            _portals.postValue(new)
    }

    fun setPortalsVisible(value: Boolean) {
        if (showPortal != value) {
            showPortal = value
            updateVisiblePortals()
        }
    }
    fun setFieldsVisible(value: Boolean) {
        if (showFields != value) {
            showFields = value
            updateVisibleFields()
        }
    }
    fun setLinksVisible(value: Boolean) {
        if (showLinks != value) {
            showLinks = value
            updateVisibleLinks()
        }
    }
    fun setDrawMode(value: Boolean) {
        if (drawMode != value) {
            drawMode = value
        }
    }
    fun selectPortal(value: Portal?) {
        if (drawMode && value != null) {
            addCustomPoint(LatLng(value.lat,value.lng))
        } else {
            _selectedPortal.value = value
        }
    }
    private fun addCustomPoint(point: LatLng) {
        val prev = this._selectedPoint.value
        if (prev == null) {
            this._selectedPoint.value = point
        } else {
            this._selectedPoint.value = null
            if (prev != point) {
                var prevLines = this._customLines.value
                if (prevLines == null) {
                    prevLines = emptySet()
                }
                this._customLines.value = setOf(*(prevLines.toTypedArray()), PolylineOptions().add(prev).add(point))
            }
        }
    }

    fun removeCustomLine(line: Array<LatLng>) {
        var prevLines = this._customLines.value
        if (prevLines == null) {
            prevLines = emptySet()
        }
        this._customLines.value = prevLines.filter {
            !(it.points[0] == line[0] && it.points[1] == line[1])
        }.toSet()
    }

    override fun onCellDataReceived(
        cellId: String,
        portals: Map<String, Portal>,
        links: Map<String, Link>,
        fields: Map<String, Field>
    ) {
        val cachedVersion = _cellCache[cellId]
        var updates = 0
        if (cachedVersion != null) {
            val removedPortals = cachedVersion.portals.keys.minus(portals.keys)
            removedPortals.map { allPortals.remove(it) }
            updates += removedPortals.size
            for (p in portals) {
                val old = allPortals[p.key]
                if (old != p.value) {
                    allPortals[p.key] = p.value
                    updates++
                }
            }

            val removedLinks = cachedVersion.links.keys.minus(links.keys)
            removedLinks.map { allLinks.remove(it) }
            updates += removedLinks.size
            for (p in links) {
                val old = allLinks[p.key]
                if (old != p.value) {
                    allLinks[p.key] = p.value
                    updates++
                }
            }

            val removedFields = cachedVersion.fields.keys.minus(fields.keys)
            removedFields.map { allFields.remove(it) }
            updates += removedFields.size
            for (p in fields) {
                val old = allFields[p.key]
                if (old != p.value) {
                    allFields[p.key] = p.value
                    updates++
                }
            }
        } else {
            for (p in portals) {
                val old = allPortals[p.key]
                if (old != p.value) {
                    allPortals[p.key] = p.value
                    updates++
                }
            }
            for (p in links) {
                val old = allLinks[p.key]
                if (old != p.value) {
                    allLinks[p.key] = p.value
                    updates++
                }
            }
            for (p in fields) {
                val old = allFields[p.key]
                if (old != p.value) {
                    allFields[p.key] = p.value
                    updates++
                }
            }
        }
        _cellCache[cellId] = CellData(portals, links, fields)
        if (updates > 0) {
            updateVisibleFields()
            updateVisibleLinks()
            updateVisiblePortals()
        }
    }

    override fun onRequestStart() {
        handler.post {
            _status.value = Status((_status.value?.requestsInProgress ?: 0) + 1)
        }
    }

    override fun onRequestEnd() {
        handler.post {
            _status.value = Status((_status.value?.requestsInProgress ?: 0) - 1 )
        }
    }

    override fun onCellsReady(data: Map<S2CellId, PolylineOptions>) {
        synchronized(_cellLines) {
            val new = mutableMapOf<S2CellId, PolylineOptions>()
            for (i in data) {
                if (new.containsKey(i.key).and(new.containsValue(i.value))) {
                    continue
                }
                new[i.key] = i.value
            }
            _cellLines.postValue(new)
        }
    }

    private fun updateCellsInRegion(region: LatLngBounds, zoom: Int) {

        val targetZoom = if (zoom < 13) zoom else 21
        val zoomToLevel = arrayOf(8,8,8,8,7,7,7,6,6,5,4,4,3,2,2,1,1)
        val level = if(targetZoom >= zoomToLevel.size) { 0 } else { zoomToLevel[targetZoom] }
        val ne = getXYTile(
            region.northeast.latitude,
            region.northeast.longitude,
            targetZoom
        )
        val sw = getXYTile(
            region.southwest.latitude,
            region.southwest.longitude,
            targetZoom
        )
        val tiles = mutableListOf<String>()
        for (y in ne.second..sw.second) {
            for (x in sw.first..ne.first) {
                tiles.add("${targetZoom}_${x}_${y}_${level}_8_100")
            }
        }
        tiles.withIndex()
            .groupBy { it.index / 20 }
            .map {
                ingressRepo.getTilesInfo(it.value.map { it.value }, this)
            }
    }
}
