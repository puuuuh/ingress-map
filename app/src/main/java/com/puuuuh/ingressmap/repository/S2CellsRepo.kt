package com.puuuuh.ingressmap.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface OnCellsReadyCallback {
    fun onCellsReady(data: Map<S2CellId, PolylineOptions>)
}

class S2CellsRepo {
    fun getCells(bounds: LatLngBounds, zoom: Int, callback: OnCellsReadyCallback) {
        GlobalScope.launch {
            val r = mutableMapOf<S2CellId, PolylineOptions>()

            val northeast =
                S2LatLng.fromDegrees(bounds.northeast.latitude, bounds.northeast.longitude)
            val southwest =
                S2LatLng.fromDegrees(bounds.southwest.latitude, bounds.southwest.longitude)
            val rect = S2LatLngRect.fromPointPair(northeast, southwest)

            val cells14 = ArrayList<S2CellId>()
            val cells17 = ArrayList<S2CellId>()

            if (zoom > 12)
                S2RegionCoverer.getSimpleCovering(rect, rect.center.toPoint(), 14, cells14)
            if (zoom >= 14.9)
                S2RegionCoverer.getSimpleCovering(rect, rect.center.toPoint(), 17, cells17)

            for (cellId in cells17) {
                val line = PolylineOptions()
                val c = S2Cell(cellId)
                for (i in 0..2) {
                    val p = S2LatLng(c.getVertex(i))
                    line.add(LatLng(p.lat().degrees(), p.lng().degrees()))
                }
                r[cellId] = line
            }

            for (cellId in cells14) {
                val line = PolylineOptions()
                val c = S2Cell(cellId)
                for (i in 0..2) {
                    val p = S2LatLng(c.getVertex(i))
                    line.add(LatLng(p.lat().degrees(), p.lng().degrees()))
                }
                r[cellId] = line
            }
            callback.onCellsReady(r)
        }
    }
}