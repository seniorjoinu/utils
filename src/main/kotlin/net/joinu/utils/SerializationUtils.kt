package net.joinu.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

/**
 * Object that makes serialization easier
 */
object SerializationUtils {
    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    fun anyToJSON(obj: Any): String = mapper.writeValueAsString(obj)
    fun anyToBytes(obj: Any): ByteArray = mapper.writeValueAsBytes(obj)

    inline fun <reified T : Any> jSONToAny(json: String): T = jSONToAny(json, T::class.java)
    inline fun <reified T : Any> bytesToAny(bytes: ByteArray): T = bytesToAny(bytes, T::class.java)

    fun <T : Any> jSONToAny(json: String, clazz: Class<T>): T = mapper.readValue(json, clazz)
    fun <T : Any> bytesToAny(bytes: ByteArray, clazz: Class<T>): T = mapper.readValue(bytes, clazz)
}

object SerializationUtils1 {
    fun dump(obj: Any, maxSize: Int = 10000): ByteBuffer {
        val fields = obj::class.java.declaredFields
        fields.forEach { it.isAccessible = true }
        val values = fields.map { it.get(obj) }

        val buffer = ByteBuffer.allocate(maxSize) // allocate direct maybe?

        values.forEach { addValue(it, buffer) }

        return buffer
    }

    fun <T : Any> load(buffer: ByteBuffer, clazz: KClass<T>): T {
        buffer.flip()
        val args = mutableListOf<Any?>()

        while (buffer.hasRemaining()) {
            val type = buffer.int
            when (type) {
                0 -> args.add(null)
                1 -> args.add(buffer.get())
                2 -> args.add(buffer.short)
                3 -> args.add(buffer.int)
                4 -> args.add(buffer.long)
                5 -> args.add(buffer.float)
                6 -> args.add(buffer.double)
                7 -> args.add(buffer.char)
                8 -> args.add(buffer.int == 1)
                9 -> {
                    val size = buffer.int
                    val bytes = ByteArray(size)
                    buffer.get(bytes)
                    args.add(bytes.toString(StandardCharsets.UTF_16))
                }
            }
        }

        return clazz.constructors.first().call(*args.toTypedArray())
    }

    private fun addValue(value: Any?, buffer: ByteBuffer) {
        if (value == null)
            buffer.putInt(0)

        when (value) {
            is Byte -> buffer.putInt(1).put(value)
            is Short -> buffer.putInt(2).putShort(value)
            is Int -> buffer.putInt(3).putInt(value)
            is Long -> buffer.putInt(4).putLong(value)
            is Float -> buffer.putInt(5).putFloat(value)
            is Double -> buffer.putInt(6).putDouble(value)
            is Char -> buffer.putInt(7).putShort(value.toShort())
            is Boolean -> buffer.putInt(8).putInt(if (value) 1 else 0)
            is String -> {
                val bytes = value.toByteArray(StandardCharsets.UTF_16)
                buffer.putInt(9).putInt(bytes.size).put(bytes)
            }
        }
    }
}

data class SimpleClass(
        val a: Byte? = 1,
        val b: Short? = null,
        val c: Int = 3,
        val d: Long = 4,
        val e: Float = 5F,
        val f: Double = 6.0,
        val g: Char = 'a',
        val h: Boolean = false,
        val j: String = "Hello, world!"
)

data class MyCustomClass(val a: Int, val b: String, val c: InetSocketAddress, val d: Array<Int> = arrayOf(1, 2, 3))

fun main(args: Array<String>) {
    val objects = (0..9999).map { SimpleClass() }

    val startJackson = System.nanoTime()
    objects
            .map { SerializationUtils.anyToBytes(it) }
            .map { SerializationUtils.bytesToAny(it, SimpleClass::class.java) }
    val endJackson = System.nanoTime()

    val startMine = System.nanoTime()
    objects
            .map { SerializationUtils1.dump(it) }
            .map { SerializationUtils1.load(it, SimpleClass::class) }
    val endMine = System.nanoTime()

    val jacksonTime = endJackson - startJackson
    val mineTime = endMine - startMine

    println("Benchmark finished!")
    println("\tJackson time: ${jacksonTime.toFloat() / 1000000} ms")
    println("\tMine time: ${mineTime.toFloat() / 1000000} ms")
    println("\tMine is ${jacksonTime.toFloat() / mineTime.toFloat()} times faster")
}