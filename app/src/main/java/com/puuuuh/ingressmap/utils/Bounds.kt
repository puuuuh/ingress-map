package com.puuuuh.ingressmap.utils

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.absoluteValue

fun LatLngBounds.intersects(viewport: LatLngBounds): Boolean {
    val thisNorthWest = LatLng(this.northeast.latitude, this.southwest.longitude)
    val thisSouthEast = LatLng(this.southwest.latitude, this.northeast.longitude)
    val vpNorthWest = LatLng(viewport.northeast.latitude, viewport.southwest.longitude)
    val vpSouthEast = LatLng(viewport.southwest.latitude, viewport.northeast.longitude)


    if (viewport.contains(this.northeast) || viewport.contains(this.southwest) ||
        viewport.contains(thisNorthWest) || viewport.contains(thisSouthEast) ||
        this.contains(viewport.northeast) || this.contains(viewport.southwest) ||
        this.contains(vpNorthWest) || this.contains(vpSouthEast)
    ) {
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
        myDown > vpDown
    ) {
        return myLeft < vpLeft &&
                myRight > vpRight
    }

    if (myLeft > vpLeft &&
        myRight < vpRight
    ) {
        return myUp > vpUp &&
                myDown < vpDown
    }

    return false
}

fun LatLngBounds.area(): Double {
    return (this.northeast.latitude - this.southwest.latitude).absoluteValue *
            (this.northeast.longitude - this.southwest.longitude).absoluteValue * 1000000000000
}