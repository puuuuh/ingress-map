package com.puuuuh.ingressmap.map.model

import android.os.AsyncTask
import com.google.android.gms.maps.model.*
import com.google.common.geometry.*

interface OnCellsReadyCallback {
    fun onCellsReady(data: Map<S2CellId, PolylineOptions>)
}

class TaskArg(val zoom: Int, val bounds: LatLngBounds)

class CalcCells(private val callback: OnCellsReadyCallback): AsyncTask<TaskArg, Any?, Map<S2CellId, PolylineOptions>>() {
    override fun doInBackground(params: Array<TaskArg>): Map<S2CellId, PolylineOptions> {
        val arg = params[0]
        val r = mutableMapOf<S2CellId, PolylineOptions>()

        val northeast = S2LatLng.fromDegrees(arg.bounds.northeast.latitude, arg.bounds.northeast.longitude)
        val southwest = S2LatLng.fromDegrees(arg.bounds.southwest.latitude, arg.bounds.southwest.longitude)
        val rect = S2LatLngRect.fromPointPair(northeast, southwest)

        val cells14 = ArrayList<S2CellId>()
        val cells17 = ArrayList<S2CellId>()

        if (arg.zoom > 12)
            S2RegionCoverer.getSimpleCovering(rect, rect.center.toPoint(), 14, cells14)
        if (arg.zoom >= 14.9)
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
        return r
    }
}