package net.joinu.utils

import kotlinx.coroutines.runBlocking
import net.joinu.utils.CryptoUtils.KEY_GENERATION_ALGORITHM
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*


class UtilsTests {
    @Test
    fun `serialization and deserialization works`() {
        val data = SampleDataClass()

        val buffer = ByteBuffer.allocateDirect(10000)
        SerializationUtils.dump(data, buffer)

        buffer.flip()
        val deserializedDataFromBytes = SerializationUtils.load<SampleDataClass>(buffer)

        assert(data == deserializedDataFromBytes)
    }

    @Test
    fun `compression and decompression works`() {
        val data = SampleDataClass()

        val buffer = ByteBuffer.allocateDirect(10000)
        SerializationUtils.dump(data, buffer)
        buffer.flip()
        val bytes = byteArrayFrom(buffer)

        val compressedAndSerializedData = CompressionUtils.compress(bytes)
        val decompressedAndSerializedData = CompressionUtils.decompress(compressedAndSerializedData)

        val decompressedBuffer = ByteBuffer.wrap(decompressedAndSerializedData)
        val decompressedAndDeserializedData =
            SerializationUtils.load<SampleDataClass>(decompressedBuffer)

        assert(decompressedAndDeserializedData == data)
    }

    @Test
    fun `key pair generated correctly`() {
        val keyPair = CryptoUtils.generateKeyPair()

        assert(keyPair.public.algorithm == KEY_GENERATION_ALGORITHM) { "public key algorithm is not valid" }
        assert(keyPair.public.encoded.isNotEmpty()) { "public key is empty" }
        assert(keyPair.private.algorithm == KEY_GENERATION_ALGORITHM) { "private key algorithm is not valid" }
        assert(keyPair.private.encoded.isNotEmpty()) { "private key is empty" }
    }

    @Test
    fun `complex data signed and verified correctly`() {
        val dataSlice1 = "test"
        val dataSlice2 = 123
        val dataSlice3 = ByteArray(0)

        val keyPair = CryptoUtils.generateKeyPair()
        val signature = CryptoUtils.sign(
            dataSlice1.toByteArray(StandardCharsets.UTF_8),
            dataSlice2.toBigInteger().toByteArray(),
            dataSlice3
        ) { keyPair.private }

        assert(signature.isNotEmpty()) { "signature is empty" }

        val verified = CryptoUtils.verify(
            signature,
            dataSlice1.toByteArray(StandardCharsets.UTF_8),
            dataSlice2.toBigInteger().toByteArray()
            // ,dataSlice3 // also works
        ) { keyPair.public }

        assert(verified) { "signature is not verified" }
    }

    @Test
    fun `public key to bigint translation work properly`() {
        val keyPair = CryptoUtils.generateKeyPair()

        val publicKeyTranslated = keyPair.public.toBigInteger()
        val publicKeyDetranslated = publicKeyTranslated.toPublicKey()

        assert(publicKeyDetranslated == keyPair.public)
    }

    @Test
    fun `hashing works properly`() {
        val data = "some random data"
        val hash = CryptoUtils.hash(data.toByteArray(StandardCharsets.UTF_8))

        assert(hash.isNotEmpty()) { "hash is empty" }
        assert(hash.size == 32) { "hash length is not 256 bits" }
    }

    @Test
    fun `classpath scan works properly`() {
        runBlocking {
            val classes = ClasspathUtils.getClassesOfPackage(listOf("net.joinu.utils"))

            assert(classes.contains(ClasspathUtils::class.java)) { "Classpath scanner doesn't work" }
        }
    }
}

data class NestedDataClass(val value: Int = 10)

data class SampleDataClass(
    val muchText: String = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc tempor tortor ipsum, a tempor ante efficitur ac. Curabitur blandit libero a ex pretium, vitae faucibus ipsum suscipit. Ut posuere fringilla consequat. Suspendisse a risus vulputate, ultricies ante et, porttitor mi. Nam feugiat mi eget orci volutpat finibus. Curabitur sodales accumsan aliquam. Duis sollicitudin aliquet urna, vel commodo diam pulvinar id.\n" +
            "\n" +
            "Ut facilisis tempus eleifend. Proin et elementum est, sit amet semper risus. Mauris sed nisi sit amet justo dapibus fringilla. Praesent sed auctor odio. Vivamus vitae odio vitae lacus posuere tempus. Proin sit amet porta lectus. Pellentesque gravida ultricies felis, eget rhoncus erat pellentesque ut. Ut magna orci, pellentesque at ante vel, volutpat ornare nunc. Vestibulum sed ante pellentesque, pellentesque orci eget, sollicitudin eros. Proin ut arcu odio. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.\n" +
            "\n" +
            "Morbi dictum mattis erat, ut tristique nisl. Nulla id dolor ultrices, sollicitudin ante vel, vestibulum nisi. Curabitur quis quam non nibh luctus interdum. In quis turpis ornare, posuere odio in, egestas odio. Maecenas vestibulum est quis tellus tempor, sed consequat nulla vestibulum. In eu fringilla dui. Fusce imperdiet laoreet vulputate. Sed at ligula nulla. Duis quis bibendum magna. Praesent posuere aliquet enim eu bibendum. Mauris ac volutpat lacus. Morbi pretium quam neque, ultrices viverra eros ultrices sed. Donec viverra luctus imperdiet. Duis mattis turpis in justo pellentesque, eget consequat nibh luctus.\n" +
            "\n" +
            "Maecenas placerat nisl eget neque rhoncus cursus vitae mollis augue. Donec sed egestas ex. Proin a suscipit magna. Nulla et porta dui. Duis nec nulla ac justo rutrum ultrices in nec velit. Ut maximus maximus massa nec facilisis. Integer eu volutpat odio. Quisque malesuada a turpis ac vehicula. Quisque elit ante, posuere eget libero non, consequat ultrices ante. Aliquam in lectus mauris.\n" +
            "\n" +
            "Nunc lorem magna, dignissim non magna eget, accumsan pretium ipsum. Integer at tortor vitae sapien porttitor sagittis id nec diam. Aenean nec orci non metus placerat fringilla. Nunc venenatis dictum porttitor. Vivamus ut metus a libero aliquet sollicitudin. Donec est ligula, iaculis quis nisi a, scelerisque gravida turpis. Sed imperdiet purus ut consectetur dictum. Praesent luctus efficitur finibus. Pellentesque et commodo augue. Praesent sed orci vitae nisl pellentesque rhoncus.",
    val long: Long = 123123L,
    val int: Int = 123,
    val randomBytes: ByteArray = muchText.toByteArray(StandardCharsets.UTF_8),
    val nestedDataClass: NestedDataClass = NestedDataClass()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SampleDataClass

        if (muchText != other.muchText) return false
        if (long != other.long) return false
        if (int != other.int) return false
        if (!Arrays.equals(randomBytes, other.randomBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = muchText.hashCode()
        result = 31 * result + long.hashCode()
        result = 31 * result + int
        result = 31 * result + Arrays.hashCode(randomBytes)
        return result
    }
}

fun byteArrayFrom(buffer: ByteBuffer): ByteArray {
    val byteArray = ByteArray(buffer.limit())
    buffer.get(byteArray)

    return byteArray
}