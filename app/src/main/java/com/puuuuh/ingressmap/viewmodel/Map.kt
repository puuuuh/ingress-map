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
import com.puuuuh.ingressmap.settings.Settings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap
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
    private val customPointRepo = CustomPointsRepo()
    private val portalsRepo = PortalsRepo()
    private val cellsRepo = S2CellsRepo()
    private val handler = Handler(context.mainLooper)

    // All cached entities
    private val allPortals = mutableMapOf<String, Portal>()
    private val allLinks = mutableMapOf<String, Link>()
    private val allFields = mutableMapOf<String, Field>()

    // Current viewport
    private var viewport = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    private var zoom = 21

    private val _targetPosition = MutableLiveData<LatLng>()
    val targetPosition: LiveData<LatLng> = _targetPosition

    private val _portals = MutableLiveData<Map<String, Portal>>()
    val portals: LiveData<Map<String, Portal>> = _portals

    private val _cellLines = MutableLiveData<Map<S2CellId, PolylineOptions>>()
    val cellLines: LiveData<Map<S2CellId, PolylineOptions>> = _cellLines

    private val _links = MutableLiveData<Map<String, Link>>()
    val links: LiveData<Map<String, Link>> = _links

    private val _fields = MutableLiveData<Map<String, Field>>()
    val fields: LiveData<Map<String, Field>> = _fields

    private val _customFields = MutableLiveData<Map<String, List<LatLng>>>()
    val customFields: LiveData<Map<String, List<LatLng>>> = _customFields

    private val _status = MutableLiveData<Status>()
    val status: LiveData<Status> = _status

    private val _customPointLinks = mutableMapOf<LatLng, MutableList<LatLng>>()

    private val _customLines = MutableLiveData<Map<String, PolylineOptions>>()
    val customLines: LiveData<Map<String, PolylineOptions>> = _customLines

    private val _selectedPoint = MutableLiveData<LatLng?>()
    val selectedPoint: LiveData<LatLng?> = _selectedPoint

    private val _selectedPortal = MutableLiveData<Portal?>()
    val selectedPortal: LiveData<Portal?> = _selectedPortal

    private val _cellCache = mutableMapOf<String, CellData>()

    init {
        _customLines.value = emptyMap()
        _targetPosition.value = Settings.lastPosition
        val links = customPointRepo.getAll()
        links.observeForever { list ->
            list.map {
                addCustomLink(it.id, Pair(it.points[0].LatLng, it.points[1].LatLng))
            }
        }
    }

    fun updatePosition(pos: LatLng, r: LatLngBounds, z: Int) {
        Settings.lastPosition = pos
        Settings.lastZoom = z.toFloat()

        viewport = r
        zoom = z

        GlobalScope.launch {
            updateCellsInRegion(r, z)
            if (Settings.showCells) {
                cellsRepo.getCells(r, z, this@MapViewModel)
            } else if (cellLines.value?.isNotEmpty() != false) {
                _cellLines.postValue(emptyMap())
            }
            updateVisibleLinks()
            updateVisiblePortals()
            updateVisibleFields()
        }
    }

    fun moveCamera(r: LatLng) {
        _targetPosition.postValue(r)
    }

    private fun updateVisibleFields() {
        val new = if (!Settings.showFields || zoom < 13) {
            mapOf()
        } else {
            allFields.filter { field -> field.value.bounds.intersects(viewport) }
        }
        if (new != _fields.value)
            _fields.postValue(new)
    }

    private fun updateVisibleLinks() {
        val new = if (!Settings.showLinks || zoom < 14) {
            mapOf()
        } else {
            allLinks.filter { link -> link.value.bounds.intersects(viewport) }
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
        val new = if (!Settings.showPortals || zoom < 13.5) {
            mapOf()
        } else {
            allPortals.filter { viewport.contains(LatLng(it.value.lat, it.value.lng)) }
        }
        if (new != _portals.value)
            _portals.postValue(new)
    }

    fun selectPortal(value: Portal?) {
        if (Settings.drawMode && value != null) {
            addCustomPoint(LatLng(value.lat, value.lng))
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
            val link = addCustomLink(UUID.randomUUID().toString(), Pair(prev, point))
            if (link != null) {
                customPointRepo.add(link)
            }
        }
    }

    private fun addCustomLink(id: String, points: Pair<LatLng, LatLng>): Link? {
        if (points.first == points.second || _customPointLinks[points.first]?.contains(points.second) == true) {
            return null
        }
        var prevLines = this._customLines.value
        if (prevLines == null) {
            prevLines = emptyMap()
        }
        val poly = PolylineOptions().add(points.first).add(points.second)
        val newLines = HashMap(prevLines)
        newLines[id] = poly
        this._customLines.value = newLines
        findNewFields(points.first, points.second)
        if (_customPointLinks[points.first] == null) {
            _customPointLinks[points.first] = mutableListOf(points.second)
        } else {
            _customPointLinks[points.first]!!.add(points.second)
        }
        if (_customPointLinks[points.second] == null) {
            _customPointLinks[points.second] = mutableListOf(points.first)
        } else {
            _customPointLinks[points.second]!!.add(points.first)
        }
        return Link(id, "C", (poly.points.map {
            Point(LatLng(it.latitude, it.longitude))
        }).toTypedArray())
    }

    private fun addCustomField(points: List<LatLng>) {
        val prev = _customFields.value
        _customFields.value = (if (prev == null) {
            mapOf(UUID.randomUUID().toString() to points)
        } else {
            prev + (UUID.randomUUID().toString() to points)
        })
    }

    private fun findNewFields(p1: LatLng, p2: LatLng) {
        val p1Links = _customPointLinks[p1]
        val p2Links = _customPointLinks[p2]
        if (p1Links != null && p2Links != null) {
            val p3List = p1Links.intersect(p2Links)
            p3List.map {
                addCustomField(listOf(p1, p2, it))
            }
        }
    }

    fun removeCustomLine(id: String) {
        val l = this._customLines.value?.get(id) ?: return
        customPointRepo.delete(id)
        var prevLines = this._customLines.value
        if (prevLines == null) {
            prevLines = emptyMap()
        }
        this._customLines.value = prevLines.filter {
            it.key != id
        }
        this._customPointLinks[l.points[0]]?.removeIf {
            it == l.points[1]
        }
        this._customPointLinks[l.points[1]]?.removeIf {
            it == l.points[0]
        }
        this._customFields.value = this._customFields.value?.filter {
            !it.value.containsAll(l.points)
        }.orEmpty()
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
            removedPortals.map {
                portalsRepo.delete(it)
                allPortals.remove(it)
            }
            updates += removedPortals.size
            for (p in portals) {
                val old = allPortals[p.key]
                if (old != p.value) {
                    portalsRepo.add(PortalDto(p.key, p.value.name, p.value.lat, p.value.lng))
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
                    portalsRepo.add(PortalDto(p.key, p.value.name, p.value.lat, p.value.lng))
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
            _cellLines.postValue(
                if (Settings.showCells) {
                    data
                } else {
                    mapOf()
                }
            )
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
