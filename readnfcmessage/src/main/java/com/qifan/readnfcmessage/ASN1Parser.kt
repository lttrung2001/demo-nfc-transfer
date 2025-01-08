package com.qifan.readnfcmessage

data class Asn1Element(
    val tag: Int,
    val length: Int,
    val value: ByteArray,
    val children: List<Asn1Element> = emptyList()
)

object ASN1Parser {

    fun isPACEInfo(element: Asn1Element): Boolean {
        // Check if the element is a SEQUENCE
        if (element.tag != 0x30) return false

        // Decode children elements (PACEInfo is a SEQUENCE with specific child structure)
        val children = decodeChildren(element)
        if (children.size < 2 || children.size > 3) return false

        // Validate version (INTEGER)
        val versionElement = children[0]
        if (versionElement.tag != 0x02) return false // INTEGER tag
        val version = versionElement.value.toIntOrNull()
        if (version == null || version !in 0..255) return false

        // Validate protocol (OBJECT IDENTIFIER)
        val protocolElement = children[1]
        if (protocolElement.tag != 0x06) return false // OBJECT IDENTIFIER tag
        val protocolOID = decodeOid(protocolElement.value)
        if (protocolOID !in listOf("1.3.6.1.4.1.3029.1.2" /* Add other OIDs if needed */)) return false

        // Validate parameterId (optional INTEGER)
        if (children.size == 3) {
            val parameterIdElement = children[2]
            if (parameterIdElement.tag != 0x02) return false // INTEGER tag
            val parameterId = parameterIdElement.value.toIntOrNull()
            if (parameterId == null || parameterId !in 0..255) return false
        }

        return true
    }

    fun decodeChildren(element: Asn1Element): List<Asn1Element> {
        val children = mutableListOf<Asn1Element>()
        var index = 0
        while (index < element.value.size) {
            val (child, newIndex) = decodeAsn1(element.value, index, element.value.size)
            children.add(child)
            index = newIndex
        }
        return children
    }

    fun decodeOid(value: ByteArray): String {
        // Decode OBJECT IDENTIFIER as a string (e.g., "1.3.6.1.4.1.3029.1.2")
        val result = mutableListOf<Int>()
        val firstByte = value[0].toInt()
        result.add(firstByte / 40)
        result.add(firstByte % 40)
        var current = 0
        for (i in 1 until value.size) {
            val byte = value[i].toInt() and 0xFF
            current = (current shl 7) or (byte and 0x7F)
            if ((byte and 0x80) == 0) {
                result.add(current)
                current = 0
            }
        }
        return result.joinToString(".")
    }

    fun ByteArray.toIntOrNull(): Int? {
        return try {
            this.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeAsn1(
        byteArray: ByteArray,
        startIndex: Int = 0,
        endIndex: Int = byteArray.size
    ): Pair<Asn1Element, Int> {
        var index = startIndex

        // Read Tag
        val tag = byteArray[index].toInt() and 0xFF
        index++

        // Read Length
        var length = byteArray[index].toInt() and 0xFF
        index++
        if (length > 127) {
            // Long form length
            val lengthBytes = length and 0x7F
            length = 0
            repeat(lengthBytes) {
                length = (length shl 8) or (byteArray[index].toInt() and 0xFF)
                index++
            }
        }

        // Read Value
        val valueStart = index
        val valueEnd = index + length
        val value = byteArray.copyOfRange(valueStart, valueEnd)
        index = valueEnd

        // Decode children if constructed (constructed tags have the MSB of the tag set to 1)
        val isConstructed = (tag and 0x20) != 0
        val children = if (isConstructed) {
            val childElements = mutableListOf<Asn1Element>()
            var childIndex = valueStart
            while (childIndex < valueEnd) {
                val (childElement, newIndex) = decodeAsn1(byteArray, childIndex, valueEnd)
                childElements.add(childElement)
                childIndex = newIndex
            }
            childElements
        } else {
            emptyList()
        }

        // Return element and updated index
        return Asn1Element(tag, length, value, children) to index
    }

    fun parseIcaoData(byteArray: ByteArray): Asn1Element? {
        return try {
            decodeAsn1(byteArray).first
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}