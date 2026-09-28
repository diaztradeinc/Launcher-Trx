package com.apex.navlab

import kotlin.math.*

/** Bounded interpolation of measured fixes. Never extrapolates after GPS stops. */
internal class LiveMotion {
    data class Pose(val latitude: Double, val longitude: Double, val bearing: Double)
    private var from: Pose? = null
    private var target: Pose? = null
    private var started = 0L
    private var duration = 0L
    var fixTime = 0L
        private set

    fun offer(pose: Pose, measuredAt: Long, now: Long): Boolean {
        if (!pose.latitude.isFinite() || !pose.longitude.isFinite() || !pose.bearing.isFinite() ||
            pose.latitude !in -90.0..90.0 || pose.longitude !in -180.0..180.0 ||
            measuredAt <= fixTime || now - measuredAt !in 0..8000) return false
        val previous = at(now)
        from = previous ?: pose
        target = pose
        // Do not animate a GPS relocation through unrelated roads.
        duration = if (previous != null && distance(previous, pose) < 100.0 && now - fixTime < 8000) 450L else 0L
        started = now
        fixTime = measuredAt
        return true
    }
    fun at(now: Long): Pose? {
        val b = target ?: return null
        val a = from ?: return b
        val t = if (duration == 0L) 1.0 else ((now - started).toDouble() / duration).coerceIn(0.0, 1.0)
        return Pose(a.latitude + (b.latitude-a.latitude)*t,
            wrapLongitude(a.longitude + angleDelta(a.longitude,b.longitude)*t),
            ((a.bearing + angleDelta(a.bearing,b.bearing)*t)%360.0+360.0)%360.0)
    }
    fun fresh(now: Long) = target != null && now - fixTime in 0..8000
    companion object {
        fun angleDelta(a: Double,b: Double) = ((b-a+540.0)%360.0)-180.0
        fun wrapLongitude(v: Double) = ((v+540.0)%360.0)-180.0
        fun distance(a: Pose,b: Pose): Double {
            val lat = Math.toRadians(b.latitude-a.latitude)
            val lon = Math.toRadians(angleDelta(a.longitude,b.longitude))
            val h = sin(lat/2).pow(2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(lon/2).pow(2)
            return 12742000.0*asin(sqrt(h.coerceIn(0.0,1.0)))
        }
    }
}
