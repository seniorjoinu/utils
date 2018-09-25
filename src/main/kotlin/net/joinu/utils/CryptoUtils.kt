package net.joinu.utils

import java.math.BigInteger
import java.security.*
import java.security.spec.X509EncodedKeySpec


/**
 * This object provides interface to some primitive cryptographic functions
 */
object CryptoUtils {
    var KEY_GENERATION_ALGORITHM = "EC"
    var SIGNATURE_ALGORITHM = "SHA256withECDSA"
    var HASH_ALGORITHM = "SHA-256"

    private val keyGen: KeyPairGenerator by lazy { KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM) }

    /**
     * Generates key pair
     */
    fun generateKeyPair(): KeyPair {
        return keyGen.genKeyPair()
    }

    /**
     * Creates signature of specified data with provided private key
     */
    fun sign(vararg data: ByteArray?, providePrivateKey: () -> PrivateKey): ByteArray {
        val signatureGenerator = Signature.getInstance(SIGNATURE_ALGORITHM)
        signatureGenerator.initSign(providePrivateKey())

        data.forEach { if (it != null) signatureGenerator.update(it) }

        return signatureGenerator.sign()
    }

    /**
     * Verifies if specified signature of specified data is created by specified public key
     */
    fun verify(signature: ByteArray, vararg data: ByteArray?, providePublicKey: () -> PublicKey): Boolean {
        val signatureGenerator = Signature.getInstance(SIGNATURE_ALGORITHM)
        signatureGenerator.initVerify(providePublicKey())

        data.forEach { if (it != null) signatureGenerator.update(it) }

        return signatureGenerator.verify(signature)
    }

    /**
     * Creates digest of specified data
     */
    fun hash(vararg data: ByteArray?): ByteArray {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)

        data.forEach { if (it != null) md.update(it) }

        return md.digest()
    }
}

/**
 * Translates public key to big int
 */
fun PublicKey.toBigInteger(): BigInteger {
    return BigInteger(1, this.encoded)
}

/**
 * Translates big int to public key
 */
fun BigInteger.toPublicKey(): PublicKey {
    return KeyFactory.getInstance(CryptoUtils.KEY_GENERATION_ALGORITHM).generatePublic(X509EncodedKeySpec(this.toByteArray()))
}
