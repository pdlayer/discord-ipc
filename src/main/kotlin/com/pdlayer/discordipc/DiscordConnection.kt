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
