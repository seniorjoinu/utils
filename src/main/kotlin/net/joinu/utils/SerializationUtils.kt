package net.joinu.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

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
