package com.qifan.readnfcmessage

import java.io.ByteArrayInputStream
import java.io.DataInputStream

sealed class ASN1Element {
    data class ASN1Integer(val value: Int) : ASN1Element()
    data class ASN1OctetString(val value: ByteArray) : ASN1Element()
    data class ASN1Sequence(val elements: List<ASN1Element>) : ASN1Element()
}

object ASN1Parser {

    fun parse(data: ByteArray): ASN1Element {
        val inputStream = ByteArrayInputStream(data)
        val dataInputStream = DataInputStream(inputStream)

        return parseElement(dataInputStream)
    }

    private fun parseElement(input: DataInputStream): ASN1Element {
        val tag = input.readUnsignedByte()
        val length = input.readUnsignedByte()

        return when (tag) {
            0x02 -> { // INTEGER
                val value = input.readInt() // assuming INTEGER fits in 4 bytes
                ASN1Element.ASN1Integer(value)
            }
            0x04 -> { // OCTET STRING
                val octetData = ByteArray(length)
                input.readFully(octetData)
                ASN1Element.ASN1OctetString(octetData)
            }
            0x30 -> { // SEQUENCE
                val elements = mutableListOf<ASN1Element>()
                for (i in 0 until length) {
                    elements.add(parseElement(input))
                }
                ASN1Element.ASN1Sequence(elements)
            }
            else -> throw IllegalArgumentException("Unsupported ASN.1 tag: $tag")
        }
    }
}