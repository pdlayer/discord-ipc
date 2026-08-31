package com.pdlayer.discordipc

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

class UnixConnection @Throws(IOException::class) constructor() : DiscordConnection {
    private val channel: SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)

    init {
        try {
            val socketPath = findSocketPath()
                ?: throw IOException("Discord IPC socket not found (checked XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP, and /tmp)")

            connectWithTimeout(UnixDomainSocketAddress.of(socketPath))
        } catch (cause: Throwable) {
            suppress { channel.close() }
            throw cause
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(opcode: Opcode, data: String) {
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(FrameFormat.HEADER_SIZE + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode.wire)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        buffer.flip()

        while (buffer.hasRemaining()) {
            channel.write(buffer)
        }
    }

    @Throws(IOException::class)
    override fun read(): DiscordConnection.Response {
        val header = ByteBuffer.allocate(FrameFormat.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        readFully(header)
        header.flip()

        val opcode = FrameFormat.resolveOpcode(header.getInt())
        val length = header.getInt()
        FrameFormat.checkPayloadSize(length)

        val dataBuffer = ByteBuffer.allocate(length)
        readFully(dataBuffer)

        return DiscordConnection.Response(opcode, dataBuffer.array().toString(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    override fun close() {
        channel.close()
    }

    @Throws(IOException::class)
    private fun connectWithTimeout(address: UnixDomainSocketAddress) {
        channel.configureBlocking(false)

        try {
            if (channel.connect(address)) {
                return
            }

            Selector.open().use { selector ->
                channel.register(selector, SelectionKey.OP_CONNECT)

                while (true) {
                    if (selector.select(CONNECT_TIMEOUT_MILLIS) == 0) {
                        throw IOException("Timed out connecting to Discord IPC socket")
                    }

                    selector.selectedKeys().clear()

                    if (channel.finishConnect()) {
                        return
                    }
                }
            }
        } finally {
            channel.configureBlocking(true)
        }
    }

    @Throws(IOException::class)
    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) {
                throw IOException("Disconnected")
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 2000L

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
                if (isUsableSocket(path)) {
                    return path
                }
            }

            return null
        }

        private fun isUsableSocket(path: Path): Boolean {
            val attributes = try {
                Files.readAttributes(path, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (_: UnsupportedOperationException) {
                return Files.exists(path)
            } catch (_: Exception) {
                return false
            }

            if (attributes.isSymbolicLink || attributes.isRegularFile || attributes.isDirectory) {
                return false
            }

            if (!isWorldWritable(path.parent)) {
                return true
            }

            val owner = try {
                attributes.owner().name
            } catch (_: Exception) {
                return false
            }

            return owner == System.getProperty("user.name")
        }

        private fun isWorldWritable(dir: Path?): Boolean {
            if (dir == null) {
                return true
            }

            return try {
                Files.getPosixFilePermissions(dir).contains(PosixFilePermission.OTHERS_WRITE)
            } catch (_: Exception) {
                true
            }
        }

        private inline fun suppress(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
            }
        }
    }
}
