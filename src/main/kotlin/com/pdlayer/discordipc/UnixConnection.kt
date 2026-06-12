package com.pdlayer.discordipc

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class UnixConnection @Throws(IOException::class) constructor() : DiscordConnection {
    private val channel: SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)

    init {
        val socketPath = findSocketPath()
            ?: throw IOException("Discord IPC socket not found (checked XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP, and /tmp)")

        channel.connect(UnixDomainSocketAddress.of(socketPath))
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(opcode: Opcode, data: String) {
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode.ordinal)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        buffer.flip()

        while (buffer.hasRemaining()) {
            channel.write(buffer)
        }
    }

    @Throws(IOException::class)
    override fun read(): DiscordConnection.Response {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        readFully(header)
        header.flip()

        val opcode = Opcode.fromInt(header.getInt())
        val length = header.getInt()

        val dataBuffer = ByteBuffer.allocate(length)
        readFully(dataBuffer)

        return DiscordConnection.Response(opcode, dataBuffer.array().toString(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    override fun close() {
        channel.close()
    }

    @Throws(IOException::class)
    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) == -1) {
                throw IOException("Disconnected")
            }
        }
    }

    companion object {
        private val envVars = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")

        private fun findSocketPath(): Path? {
            val env = System.getenv()

            for (name in envVars) {
                val dir = env[name] ?: continue
                findSocketPath(Path.of(dir))?.let { return it }
            }

            return findSocketPath(Path.of("/tmp"))
        }

        private fun findSocketPath(dir: Path): Path? {
            for (index in 0 until 10) {
                val path = dir.resolve("discord-ipc-$index")
                if (Files.exists(path)) {
                    return path
                }
            }

            return null
        }
    }
}
