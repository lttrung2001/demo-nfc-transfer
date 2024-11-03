package com.qifan.readnfcmessage

object APDUCommand {
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
}