package com.puuuuh.ingressmap.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ViewmodelFactory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass == MapViewModel::class.java) {
            return MapViewModel(context) as T
        }
        if (modelClass == PortalInfo::class.java) {
            return PortalInfo(context) as T
        }
        return super.create(modelClass)
    }
}