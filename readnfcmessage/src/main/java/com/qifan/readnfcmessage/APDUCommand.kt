package com.qifan.readnfcmessage

object APDUCommand {
    val LMK_OID = byteArrayOf(
        0x04.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x00.toByte(),
        0x07.toByte(), 0x02.toByte(), 0x02.toByte(), 0x03.toByte(),
        0x02.toByte(), 0x02.toByte() // OID
    )
    val APDU_SELECT = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xA4.toByte(), // INS	- Instruction - Instruction code
        0x04.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x0C.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x07.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xA0.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x02.toByte(),
        0x47.toByte(),
        0x10.toByte(),
        0x01.toByte(), // NDEF Tag Application name
        0x00.toByte(), // Le field	- Maximum number of bytes expected in the data field of the response to the command
    )
    val APDU_SELECT_CARD_ACCESS = byteArrayOf(
        0x00.toByte(), // CLA (Class byte, mặc định 0x00)
        0xA4.toByte(), // INS (Instruction byte cho SELECT)
        0x02.toByte(), // P1 (Select by file identifier)
        0x0C.toByte(), // P2 (Select EF without returning FCI)
        0x02.toByte(), // Lc (Độ dài của File Identifier là 2 byte)
        0x01.toByte(), // File Identifier (FID) byte cao cho Card Access
        0x1C.toByte(),
    )

    val APDU_SELECT_PACE = byteArrayOf(
        0x00.toByte(), // CLA
        0xA4.toByte(), // INS
        0x04.toByte(), // P1: Select by AID
        0x0C.toByte(), // P2: No FCI
        0x07.toByte(), // Lc: Length of AID
        0xA0.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x02.toByte(),
        0x47.toByte(),
        0x10.toByte(),
        0x01.toByte() // AID of PACE
    )
    val APDU_MSE_SET_AT = byteArrayOf(
        0x00.toByte(), // CLA
        0x22.toByte(), // INS
        0x41.toByte(), // P1
        0xA4.toByte(), // P2
        0x0C.toByte(), // Lc (Length of Data Field, 14 bytes)
        0x80.toByte(), // Tag (Algorithm identifier)
        0x0A.toByte(), // Length of OID (12 bytes)
    ) + LMK_OID
    val APDU_GET_CHALLENGE = byteArrayOf(
        0x00,
        0x84.toByte(),
        0x00,
        0x00,
        0x08
    )
}