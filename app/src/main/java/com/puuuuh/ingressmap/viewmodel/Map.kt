package com.puuuuh.ingressmap.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.repository.*
import kotlin.math.*

data class CellData(val portals: Map<String, Portal>, val links: Map<String, Link>, val fields: Map<String, Field>)

private fun getXYTile(lat : Double, lng: Double, zoom : Int) : Pair<Int, Int> {
    val tileCounts = arrayOf(1,1,1,40,40,80,80,320,1000,2000,2000,4000,8000,16000,16000,32000)
    val latRad = Math.toRadians(lat)

    val cnt = if (zoom >= tileCounts.size) {tileCounts[tileCounts.size - 1]} else {tileCounts[zoom]}
    val x = (((lng + 180.0) / 360.0) * cnt).toInt()
    val y = ((1.0 - log(tan(latRad) + (1 / cos(latRad)), E) / PI) / 2.0 * cnt).toInt()
    return Pair(x, y)
}

class MapViewModel(val context: Context) : ViewModel(), OnDataReadyCallback, OnCellsReadyCallback {
    private val ingressRepo = IngressApiRepo(context)
    private val cellsRepo = S2CellsRepo()

    // All cached entities
    private val allPortals = mutableMapOf<String, Portal>()
    private val allLinks = mutableMapOf<String, Link>()
    private val allFields = mutableMapOf<String, Field>()

    // Visibility
    private var showPortal = false
    private var showLinks = false
    private var showFields = false

    // Entities in current viewport
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

    private val _cellCache = mutableMapOf<String, CellData>()

    fun updateRegion(r: LatLngBounds, z: Int) {
        viewport = r
        zoom = z
        updateCellsInRegion(r, z, this)
        cellsRepo.getCells(r, z, this)
        updateVisibleLinks()
        updateVisiblePortals()
        updateVisibleFields()
    }

    private fun updateVisibleFields() {
        _fields.postValue(
            if (!showFields || zoom < 13) {
                mapOf()
            } else {
                allFields.filter { field -> field.value.bounds.intersects(viewport) }
            })
    }

    private fun updateVisibleLinks() {
        _links.postValue(
            if (!showLinks || zoom < 14) {
                mapOf()
            } else {
                allLinks.filter { link ->  link.value.bounds.intersects(viewport) }
            })
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
        _portals.postValue(
            if (!showPortal || zoom < 13.5) {
                mapOf()
            } else {
                allPortals.filter { viewport.contains(LatLng(it.value.lat, it.value.lng)) }
            }
        )
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

    override fun onCellDataReceived(
        cellId: String,
        portals: Map<String, Portal>,
        links: Map<String, Link>,
        fields: Map<String, Field>
    ) {
        val cachedVersion = _cellCache[cellId]
        if (cachedVersion != null) {
            val removedPortals = cachedVersion.portals.keys.minus(portals.keys)
            removedPortals.map { allPortals.remove(it) }
            for (p in portals) {
                val old = cachedVersion.portals[p.key]
                if (old != p.value) {
                    allPortals[p.key] = p.value
                }
            }

            val removedLinks = cachedVersion.links.keys.minus(links.keys)
            removedLinks.map { allLinks.remove(it) }
            for (p in links) {
                val old = cachedVersion.links[p.key]
                if (old != p.value) {
                    allLinks[p.key] = p.value
                }
            }

            val removedFields = cachedVersion.fields.keys.minus(fields.keys)
            removedFields.map { allFields.remove(it) }
            for (p in fields) {
                val old = cachedVersion.fields[p.key]
                if (old != p.value) {
                    allFields[p.key] = p.value
                }
            }
        } else {
            for (p in portals) {
                allPortals[p.key] = p.value
            }
            for (p in links) {
                allLinks[p.key] = p.value
            }
            for (p in fields) {
                allFields[p.key] = p.value
            }
        }
        _cellCache[cellId] =
            CellData(portals, links, fields)
        updateVisibleFields()
        updateVisibleLinks()
        updateVisiblePortals()
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

    fun updateCellsInRegion(region: LatLngBounds, zoom: Int, callback: OnDataReadyCallback) {
        val zoomToLevel = arrayOf(8,8,8,8,7,7,7,6,6,5,4,4,3,2,2,1,1)
        val level = if(zoom >= zoomToLevel.size) { 0 } else { zoomToLevel[zoom] }
        val ne = getXYTile(
            region.northeast.latitude,
            region.northeast.longitude,
            zoom
        )
        val sw = getXYTile(
            region.southwest.latitude,
            region.southwest.longitude,
            zoom
        )
        val tiles = mutableListOf<String>()
        for (y in ne.second..sw.second) {
            for (x in sw.first..ne.first) {
                tiles.add("${zoom}_${x}_${y}_${level}_8_100")
            }
        }
        tiles.withIndex()
            .groupBy { it.index / 30 }
            .map { ingressRepo.getTilesInfo(tiles, callback) }
    }
}
