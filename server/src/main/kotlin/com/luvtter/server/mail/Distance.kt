package com.luvtter.server.mail

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

data class GeoPoint(val lat: Double, val lng: Double)

data class AddressPoint(
    val kind: Kind,
    val real: GeoPoint? = null,
    val anchor: GeoPoint? = null,
    val virtualDistance: Int = 0
) {
    enum class Kind { REAL, VIRTUAL }
}

fun distanceValue(a: AddressPoint, b: AddressPoint): Int {
    val (pa, extraA) = when (a.kind) {
        AddressPoint.Kind.REAL -> a.real!! to 0
        AddressPoint.Kind.VIRTUAL -> a.anchor!! to a.virtualDistance
    }
    val (pb, extraB) = when (b.kind) {
        AddressPoint.Kind.REAL -> b.real!! to 0
        AddressPoint.Kind.VIRTUAL -> b.anchor!! to b.virtualDistance
    }
    val km = haversineKm(pa.lat, pa.lng, pb.lat, pb.lng)
    val raw = (km / 20.0).toInt() + extraA + extraB
    return min(1000, raw)
}
