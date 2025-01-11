package com.qifan.readnfcmessage

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun decodeResponseApdu(responseApdu: ByteArray): String {
    // Kiểm tra độ dài tối thiểu của response
    if (responseApdu.size < 2) return "Response APDU không hợp lệ"

    // Phân tách phần dữ liệu và mã trạng thái
    val data = responseApdu.copyOfRange(0, responseApdu.size - 2)
    val sw1 = responseApdu[responseApdu.size - 2].toInt() and 0xFF
    val sw2 = responseApdu[responseApdu.size - 1].toInt() and 0xFF
    val status = (sw1 shl 8) or sw2

    // Giải mã mã trạng thái
    val statusMeaning = when (status) {
        0x9000 -> "Thành công"
        0x6A82 -> "File không tồn tại"
        0x6985 -> "Điều kiện không thỏa mãn"
        0x6700 -> "Độ dài không đúng"
        0x6A84 -> "Không đủ bộ nhớ trên thẻ"
        0x6D00 -> "Lệnh APDU không hỗ trợ"
        else -> "Lỗi không xác định với mã trạng thái: %04X".format(status)
    }

    // Kết quả giải mã
    println("Dữ liệu: ${data.joinToString(" ")}\nTrạng thái: $statusMeaning")
    return data.decodeToString()
}