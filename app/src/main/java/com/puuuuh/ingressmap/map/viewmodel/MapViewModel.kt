package com.puuuuh.ingressmap.map.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.puuuuh.ingressmap.map.model.Entity
import com.puuuuh.ingressmap.map.model.MapModel
import com.puuuuh.ingressmap.map.model.OnDataReadyCallback
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.map.model.OnCellsReadyCallback

class MapViewModel(userData: UserData) : ViewModel(), OnDataReadyCallback, OnCellsReadyCallback {
    private val _user = MutableLiveData<UserData>()
    private val user: LiveData<UserData> = _user
    private val model: MapModel = MapModel()

    private val _portals = MutableLiveData<Map<String, Entity>>()
    val portals: LiveData<Map<String, Entity>> = _portals

    private val _cellLines = MutableLiveData<Map<S2CellId, PolylineOptions>>()
    val cellLines: LiveData<Map<S2CellId, PolylineOptions>> = _cellLines

    init {
        _user.value = userData
    }

    fun updateRegion(r: LatLngBounds, zoom: Int) {
        model.updateCells(zoom, r, this)
        model.refreshData(user.value!!, r, zoom, this)
    }

    override fun onDataReady(data: List<Entity>) {
        if (data.isEmpty()) {
            return
        }
        val old = _portals.value

        val new = old?.toMutableMap() ?: mutableMapOf()
        for (i in data) {
            new[i.guid] = i
        }
        _portals.postValue(new)
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
