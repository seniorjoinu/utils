package net.joinu.utils

import java.util.zip.Deflater
import java.util.zip.Inflater


/**
 * Object that makes compression easier
 */
object CompressionUtils {
    fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(data)
        deflater.finish()

        val output = ByteArray(data.size)
        val size = deflater.deflate(output)
        deflater.end()

        return output.copyOfRange(0, size)
    }

    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)

        val output = ByteArray(data.size * 10)
        val size = inflater.inflate(output)
        inflater.end()

        return output.copyOfRange(0, size)
    }
}