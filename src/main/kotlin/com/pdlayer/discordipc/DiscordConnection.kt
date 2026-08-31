package com.pdlayer.discordipc

import java.io.Closeable
import java.io.IOException

interface DiscordConnection : Closeable {
    @Throws(IOException::class)
    fun write(opcode: Opcode, data: String)

    @Throws(IOException::class)
    fun read(): Response

    data class Response(
        val opcode: Opcode,
        val data: String,
    )
}

internal object FrameFormat {
    const val HEADER_SIZE = 8
    const val MAX_PAYLOAD_SIZE = 1 shl 20

    @Throws(IOException::class)
    fun resolveOpcode(wireValue: Int): Opcode =
        Opcode.fromWire(wireValue)
            ?: throw IOException("Unknown RPC opcode: $wireValue")

    @Throws(IOException::class)
    fun checkPayloadSize(length: Int) {
        if (length < 0) {
            throw IOException("Negative RPC frame length: $length")
        }

        if (length > MAX_PAYLOAD_SIZE) {
            throw IOException("RPC frame too large: $length bytes (limit $MAX_PAYLOAD_SIZE)")
        }
    }
}
