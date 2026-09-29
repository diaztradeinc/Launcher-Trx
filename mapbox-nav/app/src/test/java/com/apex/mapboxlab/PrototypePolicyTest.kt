package com.apex.mapboxlab

import org.junit.Assert.*
import org.junit.Test

class PrototypePolicyTest {
    @Test fun rejectsSecretsWhitespaceAndEmptyTokens() {
        assertFalse(PrototypePolicy.publicTokenValid(""))
        assertFalse(PrototypePolicy.publicTokenValid("sk." + "x".repeat(40)))
        assertFalse(PrototypePolicy.publicTokenValid("pk." + "x".repeat(40) + "\n"))
        assertTrue(PrototypePolicy.publicTokenValid("pk." + "x".repeat(40)))
    }
    @Test fun staleMissingAndFutureFixesCannotEnableLiveGuidance() {
        assertFalse(PrototypePolicy.freshFix(100_000, 0))
        assertFalse(PrototypePolicy.freshFix(100_000, 91_999))
        assertFalse(PrototypePolicy.freshFix(100_000, 100_001))
        assertTrue(PrototypePolicy.freshFix(100_000, 92_000))
    }
    @Test fun endingOrReplacingARequestRejectsLateRouteAndSearchCallbacks() {
        val requests = RequestEpoch()
        val old = requests.next()
        assertTrue(requests.current(old))
        val replacement = requests.next()
        assertFalse(requests.current(old))
        assertTrue(requests.current(replacement))
        requests.next()
        assertFalse(requests.current(replacement))
    }
}
