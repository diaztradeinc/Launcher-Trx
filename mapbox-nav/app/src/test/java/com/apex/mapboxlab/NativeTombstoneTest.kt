package com.apex.mapboxlab

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class NativeTombstoneTest {
    private fun varint(number: Long): ByteArray {
        var n = number
        val out = ByteArrayOutputStream()
        do { val b = (n and 127).toInt(); n = n ushr 7; out.write(b or if (n != 0L) 128 else 0) } while (n != 0L)
        return out.toByteArray()
    }
    private fun n(id: Int, value: Long) = varint(id.toLong() shl 3) + varint(value)
    private fun b(id: Int, bytes: ByteArray) = varint((id.toLong() shl 3) or 2) + varint(bytes.size.toLong()) + bytes
    private fun s(id: Int, value: String) = b(id, value.toByteArray())
    @Test fun selectsCrashingThreadAndExcludesMemoryAndLogs() {
        val frame = n(1, 0x1234) + s(4, "abort") + s(6, "/system/lib64/libc.so") + s(8, "build-id")
        val thread = n(1, 42) + s(2, "Navigator") + b(4, frame) + s(5, "MEMORY_SECRET")
        val other = n(1, 7) + s(2, "Other thread")
        val trace = n(6, 42) + b(10, n(1, 6) + s(2, "SIGABRT")) + s(14, "assertion failed") +
            b(16, n(1, 7) + b(2, other)) + b(16, n(1, 42) + b(2, thread)) + s(18, "LOG_SECRET") + n(22, 4096)
        val text = NativeTombstone.summarize(trace)
        assertTrue(text.contains("SIGABRT (6)"))
        assertTrue(text.contains("assertion failed"))
        assertTrue(text.contains("Navigator (42)"))
        assertTrue(text.contains("pc 1234 /system/lib64/libc.so abort"))
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("Other thread"))
    }
    @Test fun unknownFieldsAreSkipped() {
        val trace = n(1001, 42) + s(1002, "unknown") + s(14, "known abort")
        assertTrue(NativeTombstone.summarize(trace).contains("known abort"))
    }
    @Test fun malformedAndOversizedTracesAreRejected() {
        for (trace in listOf(byteArrayOf(114, 100, 1), byteArrayOf(0), ByteArray(NativeTombstone.MAX_BYTES + 1))) {
            try { NativeTombstone.summarize(trace); fail("Malformed trace accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
