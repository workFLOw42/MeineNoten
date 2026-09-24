package de.workflow42.meinenoten

import de.workflow42.meinenoten.data.FileHash
import org.junit.Assert.assertEquals
import org.junit.Test

class FileHashTest {

    @Test
    fun `sha256 of known input`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileHash.of("abc".byteInputStream()),
        )
    }

    @Test
    fun `input larger than the buffer`() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(FileHash.of(bytes.inputStream()), FileHash.of(bytes.copyOf().inputStream()))
        assertEquals(64, FileHash.of(bytes.inputStream()).length)
    }
}
