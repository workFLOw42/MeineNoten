package de.workflow42.meinenoten.data

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 checksums, streamed so a large PDF never has to fit into memory. */
object FileHash {

    fun of(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun of(file: File): String = file.inputStream().use { of(it) }
}
