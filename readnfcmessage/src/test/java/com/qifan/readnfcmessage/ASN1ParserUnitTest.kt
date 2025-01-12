package com.qifan.readnfcmessage

import org.junit.Assert
import org.junit.Test

class ASN1ParserUnitTest {

    @Test
    fun testParseByteArrayToASN1() {
        val cardAccessData = "3134300d060804007f0007020202020101300f060a04007f000702020302020201013012060a04007f0007020204020202010202010d".toByteArray()
        val asn1Element = ASN1Parser.parseIcaoData(cardAccessData)
        Assert.assertTrue(asn1Element != null)
    }
}