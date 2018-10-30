package net.joinu.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast


const val TYPE_NULL: Byte = 0
const val TYPE_BYTE: Byte = 1
const val TYPE_SHORT: Byte = 2
const val TYPE_INT: Byte = 3
const val TYPE_LONG: Byte = 4
const val TYPE_FLOAT: Byte = 5
const val TYPE_DOUBLE: Byte = 6
const val TYPE_CHAR: Byte = 7
const val TYPE_BOOLEAN: Byte = 8
const val TYPE_STRING: Byte = 9
const val TYPE_BYTEARRAY: Byte = 10
const val TYPE_BIGINTEGER: Byte = 11

const val TYPE_COLLECTION: Byte = 20
const val TYPE_MAP: Byte = 21

const val TYPE_CUSTOM: Byte = 30

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
    fun dump(obj: Any?, buffer: ByteBuffer) {
        if (obj == null) {
            buffer.put(TYPE_NULL)
            return
        }

        addValue(obj, buffer)
    }

    private fun addValue(value: Any, buffer: ByteBuffer) {
        when (value) {
            is Collection<*> -> {
                buffer.put(TYPE_COLLECTION).putInt(value.size)
                value.forEach { dump(it, buffer) }
            }
            is Map<*, *> -> {
                buffer.put(TYPE_MAP).putInt(value.size)
                value.entries.forEach {
                    dump(it.key, buffer)
                    dump(it.value, buffer)
                }
            }
            else -> addPrimitiveValue(value, buffer)
        }
    }

    private fun addPrimitiveValue(value: Any, buffer: ByteBuffer) {
        when (value) {
            is Byte -> buffer.put(TYPE_BYTE).put(value)
            is Short -> buffer.put(TYPE_SHORT).putShort(value)
            is Int -> buffer.put(TYPE_INT).putInt(value)
            is Long -> buffer.put(TYPE_LONG).putLong(value)
            is Float -> buffer.put(TYPE_FLOAT).putFloat(value)
            is Double -> buffer.put(TYPE_DOUBLE).putDouble(value)
            is Char -> buffer.put(TYPE_CHAR).putShort(value.toShort())
            is Boolean -> buffer.put(TYPE_BOOLEAN).putInt(if (value) 1 else 0)
            is String -> {
                val bytes = value.toByteArray(StandardCharsets.UTF_16)
                buffer.put(TYPE_STRING).putInt(bytes.size).put(bytes)
            }
            is ByteArray -> buffer.put(TYPE_BYTEARRAY).putInt(value.size).put(value)
            is BigInteger -> {
                val bytes = value.toByteArray()
                buffer.put(TYPE_BIGINTEGER).putInt(bytes.size).put(bytes)
            }
            else -> { // custom class
                val fields = value::class.java.declaredFields
                fields.forEach { it.isAccessible = true }
                val values = fields.map { it.get(value) }

                buffer.put(TYPE_CUSTOM)
                val classNameBytes = value::class.java.canonicalName.toByteArray(StandardCharsets.UTF_16)
                buffer.putInt(classNameBytes.size).put(classNameBytes)

                values.forEach {
                    if (it == null)
                        buffer.put(TYPE_NULL)
                    else
                        dump(it, buffer)
                }
            }
        }
    }

    fun <T : Any> load(buffer: ByteBuffer, clazz: KClass<T>): T? {
        val value = parseValue(buffer)

        return if (value == null)
            null
        else
            (clazz::safeCast)(value)
    }

    inline fun <reified T : Any> load(buffer: ByteBuffer) = load(buffer, T::class)

    private fun parseValue(buffer: ByteBuffer): Any? {
        val type = buffer.get()

        when (type) {
            TYPE_COLLECTION -> {
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
            TYPE_MAP -> {
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
    private fun parsePrimitiveValue(type: Byte, buffer: ByteBuffer): Any? {
        return when (type) {
            TYPE_NULL -> null
            TYPE_BYTE -> buffer.get()
            TYPE_SHORT -> buffer.short
            TYPE_INT -> buffer.int
            TYPE_LONG -> buffer.long
            TYPE_FLOAT -> buffer.float
            TYPE_DOUBLE -> buffer.double
            TYPE_CHAR -> buffer.char
            TYPE_BOOLEAN -> buffer.int == 1
            TYPE_STRING -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                bytes.toString(StandardCharsets.UTF_16)
            }
            TYPE_BYTEARRAY -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                bytes
            }
            TYPE_BIGINTEGER -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                BigInteger(bytes)
            }
            TYPE_CUSTOM -> {
                val size = buffer.int
                val bytes = ByteArray(size)
                buffer.get(bytes)

                val className = bytes.toString(StandardCharsets.UTF_16)
                val clazz = Class.forName(className)

                val args = mutableListOf<Any?>()

                while (buffer.hasRemaining()) {
                    args.add(parseValue(buffer))
                }

                val constructor = clazz.constructors.first { it.parameterCount == args.size } // TODO: add intelligence

                constructor.newInstance(*args.toTypedArray())
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

fun main(args: Array<String>) = runBlocking {
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
                buffer.flip()
                buffer
            }
            .map {
                SerializationUtils1.load(it, SimpleClass::class)
            }
    val endMine = System.nanoTime()

    val jacksonTime = endJackson - startJackson
    val mineTime = endMine - startMine

    println("Benchmark finished!")
    println("\tJackson time: ${jacksonTime.toFloat() / 1000000} ms")
    println("\tMine time: ${mineTime.toFloat() / 1000000} ms")
    println("\tMine is ${jacksonTime.toFloat() / mineTime.toFloat()} times faster")
}