package com.puuuuh.ingressmap.utils

import com.google.android.gms.maps.model.LatLng
import com.puuuuh.ingressmap.model.Point
import org.osmdroid.util.GeoPoint

fun Point.toLatLng(): LatLng {
    return LatLng(this.lat, this.lng)
}

fun Point.toGeoPoint(): GeoPoint {
    return GeoPoint(this.lat, this.lng)
}