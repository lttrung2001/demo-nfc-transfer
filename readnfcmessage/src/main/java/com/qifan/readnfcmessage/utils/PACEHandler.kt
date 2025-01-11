package com.qifan.readnfcmessage.utils

import java.security.MessageDigest

object PACEHandler {
    fun createPaceKey(
        mrzKey: String
    ): ByteArray {
        val buf = mrzKey.toByteArray()
        val hash = calcSHA1Hash(buf)
        val smskg = SecureMessagingSessionKeyGenerator()
        val key = smskg.deriveKey(
            keySeed = hash,
            cipherAlgName = "AES",
            keyLength = 128,
            nonce = null,
            mode = SecureMessagingSessionKeyGenerator.SMSMode.PACE_MODE,
            paceKeyReference = 0x01 // MRZ type
        )
        return key
    }

    private fun calcSHA1Hash(buf: ByteArray): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        return messageDigest.digest(buf)
    }
}