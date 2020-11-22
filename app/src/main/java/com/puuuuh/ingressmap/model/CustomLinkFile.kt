package com.puuuuh.ingressmap.model

import java.util.*

class LinkFile : ArrayList<LinkFileItem>()

data class LinkFileItem(
    val color: String,
    val latLngs: List<LatLng>,
    val type: String
)

data class LatLng(
    val lat: Double,
    val lng: Double
)