package com.puuuuh.ingressmap.map.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.puuuuh.ingressmap.map.model.Entity
import com.puuuuh.ingressmap.map.model.MapModel
import com.puuuuh.ingressmap.map.model.OnDataReadyCallback
import com.google.android.gms.maps.model.LatLngBounds

class MapViewModel(userData: UserData) : ViewModel(), OnDataReadyCallback {
    private val _user = MutableLiveData<UserData>()
    val user: LiveData<UserData> = _user
    private val model: MapModel = MapModel()

    private val _portals = MutableLiveData<Map<String, Entity>>()
    val portals: LiveData<Map<String, Entity>> = _portals

    init {
        _user.value = userData
    }

    fun updateRegion(r: LatLngBounds, zoom: Int) {
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
}
