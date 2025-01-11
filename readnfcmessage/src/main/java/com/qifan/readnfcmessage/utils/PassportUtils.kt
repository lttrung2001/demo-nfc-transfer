package com.qifan.readnfcmessage.utils

object PassportUtils {
    fun getMRZKey(
        passportNumber: String,
        dateOfBirth: String,
        dateOfExpiry: String
    ): String {
        val passportNumberPadded = pad(
            value = passportNumber,
            fieldLength = 9
        )
        val dateOfBirthPadded = pad(
            value = dateOfBirth,
            fieldLength = 6
        )
        val dateOfExpiryPadded = pad(
            value = dateOfExpiry,
            fieldLength = 6
        )
        val passportCheckSum = calcCheckSum(passportNumberPadded)
        val dateOfBirthCheckSum = calcCheckSum(dateOfBirthPadded)
        val dateOfExpiryCheckSum = calcCheckSum(dateOfExpiryPadded)
        val mrzKey = "$passportNumberPadded$passportCheckSum$dateOfBirthPadded$dateOfBirthCheckSum$dateOfExpiryPadded$dateOfExpiryCheckSum"
        return mrzKey
    }
    fun pad(
        value: String,
        fieldLength: Int
    ): String {
        return value.padEnd(
            length = fieldLength - value.length,
            padChar = '<'
        )
    }

    fun calcCheckSum(checkString: String): Int {
        // Bảng tra cứu ký tự và giá trị tương ứng
        val characterDict = mapOf(
            '0' to 0, '1' to 1, '2' to 2, '3' to 3, '4' to 4, '5' to 5, '6' to 6,
            '7' to 7, '8' to 8, '9' to 9, '<' to 0, ' ' to 0,
            'A' to 10, 'B' to 11, 'C' to 12, 'D' to 13, 'E' to 14, 'F' to 15,
            'G' to 16, 'H' to 17, 'I' to 18, 'J' to 19, 'K' to 20, 'L' to 21,
            'M' to 22, 'N' to 23, 'O' to 24, 'P' to 25, 'Q' to 26, 'R' to 27,
            'S' to 28, 'T' to 29, 'U' to 30, 'V' to 31, 'W' to 32, 'X' to 33,
            'Y' to 34, 'Z' to 35
        )

        var sum = 0
        var m = 0
        val multipliers = listOf(7, 3, 1)

        // Duyệt từng ký tự trong chuỗi
        for (c in checkString) {
            val number = characterDict[c] ?: return 0 // Nếu không tìm thấy, trả về 0
            val product = number * multipliers[m]
            sum += product
            m = (m + 1) % 3
        }

        return sum % 10
    }
}