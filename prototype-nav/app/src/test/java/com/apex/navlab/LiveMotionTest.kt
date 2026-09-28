package com.apex.navlab

import org.junit.Assert.*
import org.junit.Test

class LiveMotionTest {
    @Test fun headingCrossesNorthWithoutFullRotation() {
        val m=LiveMotion()
        m.offer(LiveMotion.Pose(40.0,-74.0,359.0),1000,1000)
        m.offer(LiveMotion.Pose(40.0001,-74.0,1.0),2000,2000)
        val middle=m.at(2225)!!
        assertTrue(middle.bearing<1.0 || middle.bearing>359.0)
        assertEquals(40.00005,middle.latitude,0.0000001)
    }
    @Test fun lostGpsNeverExtrapolatesAndBecomesStale() {
        val m=LiveMotion()
        m.offer(LiveMotion.Pose(40.0,-74.0,90.0),1000,1000)
        val end=LiveMotion.Pose(40.0,-73.9999,90.0)
        m.offer(end,2000,2000)
        assertEquals(end.latitude,m.at(30000)!!.latitude,0.0)
        assertEquals(end.longitude,m.at(30000)!!.longitude,0.0000001)
        assertFalse(m.fresh(10001))
    }
    @Test fun ignoresOutOfOrderOldFutureAndInvalidFixes() {
        val m=LiveMotion()
        val good=LiveMotion.Pose(40.0,-74.0,0.0)
        assertTrue(m.offer(good,10000,10000))
        assertFalse(m.offer(good.copy(latitude=41.0),9999,10001))
        assertFalse(m.offer(good,10001,20002))
        assertFalse(m.offer(good,20000,10000))
        assertFalse(m.offer(good.copy(latitude=Double.NaN),10001,10001))
        assertFalse(m.offer(good.copy(latitude=91.0),10001,10001))
        assertEquals(40.0,m.at(10001)!!.latitude,0.0)
    }
    @Test fun gpsJumpIsNotAnimatedThroughUnrelatedRoads() {
        val m=LiveMotion()
        m.offer(LiveMotion.Pose(40.0,-74.0,0.0),1000,1000)
        m.offer(LiveMotion.Pose(41.0,-74.0,0.0),2000,2000)
        assertEquals(41.0,m.at(2000)!!.latitude,0.0)
    }
    @Test fun datelineDoesNotSweepAcrossWorld() {
        val m=LiveMotion()
        m.offer(LiveMotion.Pose(0.0,179.9999,90.0),1000,1000)
        m.offer(LiveMotion.Pose(0.0,-179.9999,90.0),2000,2000)
        assertEquals(180.0,kotlin.math.abs(m.at(2225)!!.longitude),0.000001)
    }
}
