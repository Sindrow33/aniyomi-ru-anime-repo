package eu.kanade.tachiyomi.animeextension.all.xhamster

/**
 * xHamster hides its CDN links behind a hex blob in `window.initials`.
 *
 * Layout: byte 0 selects the PRNG, bytes 1..4 are the little-endian seed and
 * the rest is the UTF-8 url XOR-ed with the generator output. The generators
 * below are a 1:1 port of the int32 arithmetic used by their `xplayer.js`.
 */
object UrlDeobfuscator {

    fun decode(payload: String): String? {
        if (payload.length < 12 || payload.length % 2 != 0) return null

        val bytes = payload.hexToBytes() ?: return null
        val algorithm = bytes[0].toInt() and 0xFF
        if (algorithm !in 1..7) return null

        val seed = (bytes[1].toInt() and 0xFF) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            ((bytes[3].toInt() and 0xFF) shl 16) or
            ((bytes[4].toInt() and 0xFF) shl 24)

        val state = IntArray(1) { seed }
        val decoded = ByteArray(bytes.size - 5) { index ->
            (bytes[index + 5].toInt() xor next(algorithm, state)).toByte()
        }

        return decoded.toString(Charsets.UTF_8).takeIf { it.startsWith("http") }
    }

    private fun next(algorithm: Int, state: IntArray): Int {
        var i = state[0]
        var e: Int

        when (algorithm) {
            1 -> {
                i = i * 1664525 + 0x3C6EF35F
                e = i
            }
            2 -> {
                i = i xor (i shl 13)
                i = i xor (i ushr 17)
                i = i xor (i shl 5)
                e = i
            }
            3 -> {
                i += 0x9E3779B9.toInt()
                e = i
                e = e xor (e ushr 16)
                e *= 0x85EBCA77.toInt()
                e = e xor (e ushr 13)
                e *= 0xC2B2AE3D.toInt()
                e = e xor (e ushr 16)
            }
            4 -> {
                i += 0x6D2B79F5
                e = (i shl 7) or (i ushr 25)
                e += 0x9E3779B9.toInt()
                e = e xor (e ushr 11)
                e *= 0x27D4EB2D
            }
            5 -> {
                i = i xor (i shl 7)
                i = i xor (i ushr 9)
                i = i xor (i shl 8)
                i += 0xA5A5A5A5.toInt()
                e = i
            }
            6 -> {
                i = i * 0x2C9277B5 + 0xAC564B05.toInt()
                e = (i xor (i ushr 18)) ushr ((i ushr 27) and 31)
            }
            else -> {
                i += 0x9E3779B9.toInt()
                e = i xor (i shl 5)
                e *= 0x7FEB352D
                e = e xor (e ushr 15)
                e *= 0x846CA68B.toInt()
            }
        }

        state[0] = i

        return e and 0xFF
    }

    private fun String.hexToBytes(): ByteArray? = runCatching {
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}
