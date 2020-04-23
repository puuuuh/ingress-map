package com.puuuuh.ingressmap.map.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.map.model.*

data class CellData(val portals: Map<String, Entity>, val links: Map<String, Link>, val fields: Map<String, Field>) {

}

class MapViewModel(userData: UserData) : ViewModel(), OnDataReadyCallback, OnCellsReadyCallback {
    private val _user = MutableLiveData<UserData>()
    val user: LiveData<UserData> = _user
    private val model: MapModel = MapModel()

    // All cached entities
    private val allPortals = mutableMapOf<String, Entity>()
    private val allLinks = mutableMapOf<String, Link>()
    private val allFields = mutableMapOf<String, Field>()

    // Visibility
    private var showPortal = false
    private var showLinks = false
    private var showFields = false

    // Entities in current viewport
    private var viewport = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    private var zoom = 21

    private val _portals = MutableLiveData<Map<String, Entity>>()
    val portals: LiveData<Map<String, Entity>> = _portals

    private val _cellLines = MutableLiveData<Map<S2CellId, PolylineOptions>>()
    val cellLines: LiveData<Map<S2CellId, PolylineOptions>> = _cellLines

    private val _links = MutableLiveData<Map<String, Link>>()
    val links: LiveData<Map<String, Link>> = _links

    private val _fields = MutableLiveData<Map<String, Field>>()
    val fields: LiveData<Map<String, Field>> = _fields

    private val _cellCache = mutableMapOf<String, CellData>()

    init {
        _user.value = userData
    }

    fun updateRegion(r: LatLngBounds, z: Int) {
        viewport = r
        zoom = z
        model.updateCells(z, r, this)
        model.refreshData(user.value!!, r, z, this)
        updateVisibleLinks()
        updateVisiblePortals()
        updateVisibleFields()
    }

    private fun updateVisibleFields() {
        _fields.postValue(
            if (!showFields || zoom < 13) {
                mapOf()
            } else {
                allFields.filter { field -> field.value.points.any { viewport.contains(it.LatLng) } }
            })
    }

    private fun updateVisibleLinks() {
        _links.postValue(
            if (!showLinks || zoom < 14) {
                mapOf()
            } else {
                allLinks.filter { link -> link.value.points.any { viewport.contains(it.LatLng) } }
            })
    }

    private fun updateVisiblePortals() {
        _portals.postValue(
            if (!showPortal || zoom < 14.9) {
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

    fun setUser(user: UserData) {
        _user.postValue(user)
    }

    override fun onAuthNeeded() {
        _user.postValue(
            UserData("", "")
        )
    }

    override fun onCellDataReceived(
        cellId: String,
        portals: Map<String, Entity>,
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
        _cellCache[cellId] = CellData(portals, links, fields)
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
}
