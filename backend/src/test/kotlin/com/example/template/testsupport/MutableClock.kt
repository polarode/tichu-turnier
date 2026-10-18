package com.example.template.testsupport

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Clock

/** A [Clock] the test can move forward, for windows and expiry semantics. */
class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: java.time.Duration) {
        now = now.plus(duration)
    }
}