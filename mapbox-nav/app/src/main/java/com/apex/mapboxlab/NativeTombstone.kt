package com.apex.mapboxlab

/** Reads only diagnostic fields from AOSP debuggerd/proto/tombstone.proto.
 * Memory contents, registers, logs, command line and open files are never reported.
 */
object NativeTombstone {
    const val MAX_BYTES = 4 * 1024 * 1024
    private data class Field(val number: Int, val integer: Long = 0, val bytes: ByteArray = byteArrayOf())
    private fun fields(data: ByteArray, keep: Set<Int>): List<Field> {
        require(data.size <= MAX_BYTES)
        var pos = 0
        fun varint(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(pos < data.size) { "Truncated protobuf" }
                val b = data[pos++].toInt() and 255
                if (shift == 63) require(b <= 1) { "Invalid varint" }
                value = value or ((b and 127).toLong() shl shift)
                if (b and 128 == 0) return value
            }
            error("Invalid varint")
        }
        val result = mutableListOf<Field>()
        while (pos < data.size) {
            val tag = varint()
            require(tag > 0 && tag <= Int.MAX_VALUE)
            val number = (tag ushr 3).toInt()
            require(number > 0)
            when ((tag and 7).toInt()) {
                0 -> { val n = varint(); if (number in keep) result.add(Field(number, n)) }
                1 -> { require(data.size - pos >= 8); pos += 8 }
                2 -> {
                    val size = varint()
                    require(size >= 0 && size <= data.size - pos)
                    val end = pos + size.toInt()
                    if (number in keep) result.add(Field(number, bytes = data.copyOfRange(pos, end)))
                    pos = end
                }
                5 -> { require(data.size - pos >= 4); pos += 4 }
                else -> error("Unsupported wire type")
            }
        }
        return result
    }
    private fun List<Field>.n(id: Int) = firstOrNull { it.number == id }?.integer ?: 0L
    private fun List<Field>.b(id: Int) = firstOrNull { it.number == id }?.bytes ?: byteArrayOf()
    private fun List<Field>.s(id: Int) = b(id).toString(Charsets.UTF_8).take(2000)

    fun summarize(data: ByteArray): String {
        val top = fields(data, setOf(6, 10, 14, 15, 16, 22))
        val signal = fields(top.b(10), setOf(1, 2, 3, 4))
        return buildString {
            appendLine("Native signal: ${signal.s(2)} (${signal.n(1)}), ${signal.s(4)}")
            appendLine("Page size: ${top.n(22)}")
            appendLine("Abort: ${top.s(14).ifBlank { "Not supplied" }}")
            top.filter { it.number == 15 }.take(3).forEach {
                appendLine("Cause: ${fields(it.bytes, setOf(1)).s(1)}")
            }
            val entry = top.filter { it.number == 16 }.map { fields(it.bytes, setOf(1, 2)) }
                .firstOrNull { it.n(1) == top.n(6) }
            if (entry == null) appendLine("Crashing thread not present in trace")
            else {
                val thread = fields(entry.b(2), setOf(1, 2, 4))
                appendLine("Crashing thread: ${thread.s(2)} (${thread.n(1)})")
                thread.filter { it.number == 4 }.take(18).forEachIndexed { index, field ->
                    val frame = fields(field.bytes, setOf(1, 4, 5, 6, 8))
                    appendLine("#$index pc ${frame.n(1).toString(16)} ${frame.s(6)} ${frame.s(4)}+${frame.n(5)} build=${frame.s(8)}")
                }
            }
        }
    }
}
