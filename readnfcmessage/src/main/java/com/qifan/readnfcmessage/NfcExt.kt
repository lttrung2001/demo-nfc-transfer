package com.qifan.readnfcmessage

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }