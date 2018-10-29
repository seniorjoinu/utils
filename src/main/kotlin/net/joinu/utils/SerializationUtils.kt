package net.joinu.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.math.BigInteger
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
    fun dump(obj: Any, buffer: ByteBuffer) {
        val fields = obj::class.java.declaredFields
        fields.forEach { it.isAccessible = true }
        val values = fields.map { it.get(obj) }

        values.forEach { addValue(it, buffer) }

        buffer.flip()
    }

    private fun addValue(value: Any?, buffer: ByteBuffer) {
        if (value == null)
            buffer.putInt(0)

        when (value) {
            is Collection<*> -> {
                buffer.putInt(20).putInt(value.size)
                value.forEach { addValue(it, buffer) }
            }
            is Map<*, *> -> {
                buffer.putInt(21).putInt(value.size)
                value.entries.forEach {
                    addValue(it.key, buffer)
                    addValue(it.value, buffer)
                }
            }
            // TODO: add user types
            else -> addPrimitiveValue(value, buffer)
        }
    }

    private fun addPrimitiveValue(value: Any?, buffer: ByteBuffer) {
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
            is ByteArray -> buffer.putInt(10).putInt(value.size).put(value)
            is BigInteger -> {
                val bytes = value.toByteArray()
                buffer.putInt(11).putInt(bytes.size).put(bytes)
            }
        }
    }

    fun <T : Any> load(buffer: ByteBuffer, clazz: KClass<T>): T {
        val args = mutableListOf<Any?>()

        while (buffer.hasRemaining()) {
            args.add(parseValue(buffer))
        }

        return clazz.constructors.first().call(*args.toTypedArray())
    }

    private fun parseValue(buffer: ByteBuffer): Any? {
        val type = buffer.int

        when (type) {
            20 -> {
                val size = buffer.int
                if (size == 0) return emptyList<Any?>()

                val genericList = (1..size).map { parseValue(buffer) }
                val genericNonNullList = genericList.filterNotNull()

                if (genericNonNullList.isEmpty()) return genericList

                // there is a bug with nulls, i can feel it -,-
                return if (genericNonNullList.all { it::class.java.isAssignableFrom(genericNonNullList.first()::class.java) }) {
                    genericList.filterIsInstance(genericNonNullList.first()::class.java)
                } else {
                    genericList
                }
            }
            21 -> {
                val size = buffer.int
                if (size == 0) return emptyMap<Any?, Any?>()

                val genericMap = (1..size).associate { parseValue(buffer) to parseValue(buffer) }
                val genericNonNullKeyList = genericMap.keys.filterNotNull()
                val genericNonNullValueList = genericMap.values.filterNotNull()

                if (genericNonNullKeyList.isEmpty() or genericNonNullValueList.isEmpty()) return genericMap

                val keyType = if (genericNonNullKeyList.all { it::class.java.isAssignableFrom(genericNonNullKeyList.first()::class.java) })
                    genericNonNullKeyList.first()::class
                else
                    Any::class

                val valueType = if (genericNonNullValueList.all { it::class.java.isAssignableFrom(genericNonNullValueList.first()::class.java) })
                    genericNonNullValueList.first()::class
                else
                    Any::class

                val typedKeyList = genericMap.keys.filterIsInstance(keyType.java)
                val typedValueList = genericMap.values.filterIsInstance(valueType.java)

                return typedKeyList.associate { it to typedValueList[typedKeyList.indexOf(it)] }
            }
            else -> return parsePrimitiveValue(type, buffer)
        }
    }

    @Throws(DeserializationException::class)
    private fun parsePrimitiveValue(type: Int, buffer: ByteBuffer): Any? {
        return when (type) {
            0 -> null
            1 -> buffer.get()
            2 -> buffer.short
            3 -> buffer.int
            4 -> buffer.long
            5 -> buffer.float
            6 -> buffer.double
            7 -> buffer.char
            8 -> buffer.int == 1
            9 -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                bytes.toString(StandardCharsets.UTF_16)
            }
            10 -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                bytes
            }
            11 -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                BigInteger(bytes)
            }
            else -> {
                throw DeserializationException("Unable to find deserialization strategy for type: $type")
            }
        }
    }
}

data class DeserializationException(override val message: String) : RuntimeException(message)
data class SerializationException(override val message: String) : RuntimeException(message)

data class SimpleClass(
    val a: Byte? = 1,
    val b: Short? = null,
    val c: Int = 3,
    val d: Long = 4,
    val e: Float = 5F,
    val f: Double = 6.0,
    val g: Char = 'a',
    val h: Boolean = false,
    val j: String = "Hello, world!",
    val k: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val l: Map<Int, Int> = k.associate { it to it }
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
        .map {
            val buffer = ByteBuffer.allocateDirect(10000)
            SerializationUtils1.dump(it, buffer)
            buffer
        }
        .map { SerializationUtils1.load(it, SimpleClass::class) }
    val endMine = System.nanoTime()

    val jacksonTime = endJackson - startJackson
    val mineTime = endMine - startMine

    println("Benchmark finished!")
    println("\tJackson time: ${jacksonTime.toFloat() / 1000000} ms")
    println("\tMine time: ${mineTime.toFloat() / 1000000} ms")
    println("\tMine is ${jacksonTime.toFloat() / mineTime.toFloat()} times faster")
}