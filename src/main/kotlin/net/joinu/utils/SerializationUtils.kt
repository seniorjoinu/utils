package net.joinu.utils

import java.math.BigInteger
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

    /**
     * Dumps object to [ByteBuffer].
     * Warning! You should manually flip the buffer.
     *
     * @param obj: [Any]                <optional> object to dump
     * @param buffer: [ByteBuffer]      <by link> result buffer
     */
    @JvmStatic
    fun dump(obj: Any?, buffer: ByteBuffer) {
        if (obj == null) {
            buffer.put(TYPE_NULL)
            return
        }

        addValue(obj, buffer)
    }

    /**
     * Loads object of specific class from [ByteBuffer]
     * Warning! You should manually flip the buffer.
     *
     * @param buffer: [ByteBuffer]      <by link> buffer with serialized object
     * @param clazz: [KClass]           KClass of the target object
     *
     * @return                          instance of [KClass]
     */
    @JvmStatic
    fun <T : Any> load(buffer: ByteBuffer, clazz: KClass<T>): T? {
        val value = parseValue(buffer)

        return if (value == null)
            null
        else
            (clazz::safeCast)(value)
    }

    inline fun <reified T : Any> load(buffer: ByteBuffer) = load(buffer, T::class)

    @JvmStatic
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

    @JvmStatic
    private fun addPrimitiveValue(value: Any, buffer: ByteBuffer) {
        when (value) {
            is Byte -> buffer.put(TYPE_BYTE).put(value)
            is Short -> buffer.put(TYPE_SHORT).putShort(value)
            is Int -> buffer.put(TYPE_INT).putInt(value)
            is Long -> buffer.put(TYPE_LONG).putLong(value)
            is Float -> buffer.put(TYPE_FLOAT).putFloat(value)
            is Double -> buffer.put(TYPE_DOUBLE).putDouble(value)
            is Char -> buffer.put(TYPE_CHAR).putChar(value)
            is Boolean -> buffer.put(TYPE_BOOLEAN).putInt(if (value) 1 else 0)
            is String -> {
                buffer.put(TYPE_STRING).putInt(value.length)
                value.forEach { buffer.putChar(it) }
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

    @JvmStatic
    private fun parseValue(buffer: ByteBuffer): Any? {
        val type = buffer.get()

        return when (type) {
            TYPE_COLLECTION -> parseCollection(buffer)
            TYPE_MAP -> parseMap(buffer)
            else -> parsePrimitiveValue(type, buffer)
        }
    }

    @JvmStatic
    private fun parseCollection(buffer: ByteBuffer): Collection<*> {
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

    @JvmStatic
    private fun parseMap(buffer: ByteBuffer): Map<*, *> {
        val size = buffer.int
        if (size == 0) return emptyMap<Any?, Any?>()

        val genericMap = (1..size).associate { parseValue(buffer) to parseValue(buffer) }
        val genericNonNullKeyList = genericMap.keys.filterNotNull()
        val genericNonNullValueList = genericMap.values.filterNotNull()

        if (genericNonNullKeyList.isEmpty() or genericNonNullValueList.isEmpty()) return genericMap

        val keyType =
            if (genericNonNullKeyList.all { it::class.java.isAssignableFrom(genericNonNullKeyList.first()::class.java) })
                genericNonNullKeyList.first()::class
            else
                Any::class

        val valueType =
            if (genericNonNullValueList.all { it::class.java.isAssignableFrom(genericNonNullValueList.first()::class.java) })
                genericNonNullValueList.first()::class
            else
                Any::class

        val typedKeyList = genericMap.keys.filterIsInstance(keyType.java)
        val typedValueList = genericMap.values.filterIsInstance(valueType.java)

        return typedKeyList.associate { it to typedValueList[typedKeyList.indexOf(it)] }
    }

    @Throws(DeserializationException::class)
    @JvmStatic
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
                (0 until size)
                    .map { buffer.char }
                    .joinToString(separator = "")
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
