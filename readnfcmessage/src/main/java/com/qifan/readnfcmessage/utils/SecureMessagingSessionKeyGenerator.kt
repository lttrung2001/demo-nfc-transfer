package com.qifan.readnfcmessage.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

class SecureMessagingSessionKeyGenerator {

    companion object {
        const val NO_PACE_KEY_REFERENCE: Byte = 0x00
    }

    enum class SMSMode(val value: Byte) {
        ENC_MODE(0x1),
        MAC_MODE(0x2),
        PACE_MODE(0x3)
    }

    @Throws(Exception::class)
    fun deriveKey(keySeed: ByteArray, mode: SMSMode): ByteArray {
        return deriveKey(keySeed, "DESede", 128, null, mode, NO_PACE_KEY_REFERENCE)
    }

    @Throws(Exception::class)
    fun deriveKey(keySeed: ByteArray, cipherAlgName: String, keyLength: Int, mode: SMSMode): ByteArray {
        return deriveKey(keySeed, cipherAlgName, keyLength, null, mode, NO_PACE_KEY_REFERENCE)
    }

    @Throws(Exception::class)
    fun deriveKey(
        keySeed: ByteArray,
        cipherAlgName: String,
        keyLength: Int,
        nonce: ByteArray? = null,
        mode: SMSMode
    ): ByteArray {
        return deriveKey(keySeed, cipherAlgName, keyLength, nonce, mode, NO_PACE_KEY_REFERENCE)
    }

    @Throws(Exception::class)
    fun deriveKey(
        keySeed: ByteArray,
        cipherAlgName: String,
        keyLength: Int,
        nonce: ByteArray?,
        mode: SMSMode,
        paceKeyReference: Byte
    ): ByteArray {
        val digestAlgo = inferDigestAlgorithmFromCipherAlgorithmForKeyDerivation(cipherAlgName, keyLength)

        val modeArr = byteArrayOf(0x00, 0x00, 0x00, mode.value)
        val dataEls = mutableListOf<ByteArray>()
        dataEls.add(keySeed)
        nonce?.let { dataEls.add(it) }
        dataEls.add(modeArr)

        val hashResult = getHash(digestAlgo, dataEls)

        return when {
            cipherAlgName.equals("DESede", ignoreCase = true) || cipherAlgName.equals("3DES", ignoreCase = true) -> {
                when (keyLength) {
                    112, 128 -> hashResult.copyOfRange(0, 16) + hashResult.copyOfRange(0, 8)
                    else -> throw IllegalArgumentException("Can only use DESede with 128-bit key length")
                }
            }

            cipherAlgName.equals("AES", ignoreCase = true) || cipherAlgName.lowercase().startsWith("aes") -> {
                when (keyLength) {
                    128 -> hashResult.copyOfRange(0, 16)
                    192 -> hashResult.copyOfRange(0, 24)
                    256 -> hashResult.copyOfRange(0, 32)
                    else -> throw IllegalArgumentException("Can only use AES with 128-bit, 192-bit, or 256-bit length")
                }
            }

            else -> throw IllegalArgumentException("Unsupported cipher algorithm used")
        }
    }

    @Throws(Exception::class)
    private fun inferDigestAlgorithmFromCipherAlgorithmForKeyDerivation(cipherAlg: String, keyLength: Int): String {
        return when {
            cipherAlg.equals("DESede", ignoreCase = true) || cipherAlg.equals("AES-128", ignoreCase = true) -> "SHA-1"
            cipherAlg.equals("AES", ignoreCase = true) && keyLength == 128 -> "SHA-1"
            cipherAlg.equals("AES-256", ignoreCase = true) || cipherAlg.equals("AES-192", ignoreCase = true) -> "SHA-256"
            cipherAlg.equals("AES", ignoreCase = true) && (keyLength == 192 || keyLength == 256) -> "SHA-256"
            else -> throw IllegalArgumentException("Unsupported cipher algorithm or key length")
        }
    }

    @Throws(Exception::class)
    private fun getHash(algo: String, dataElements: List<ByteArray>): ByteArray {
        val digest = try {
            MessageDigest.getInstance(algo)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalArgumentException("Unsupported hash algorithm specified")
        }

        dataElements.forEach { digest.update(it) }
        return digest.digest()
    }
}
