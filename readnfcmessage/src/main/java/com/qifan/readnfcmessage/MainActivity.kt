package com.qifan.readnfcmessage

import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.qifan.readnfcmessage.APDUCommand.APDU_SELECT_CARD_ACCESS
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement


class MainActivity : AppCompatActivity(), ReaderCallback {
    private var mNfcAdapter: NfcAdapter? = null
    private lateinit var mTvView: TextView

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("onCreate")
        val tag: Tag? = intent.extras?.getParcelable(NfcAdapter.EXTRA_TAG)
        println(tag)
        setContentView(R.layout.activity_main)
        initView()
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (checkNFCEnable()) {
            mNfcAdapter!!.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        } else {
            mTvView.text = getString(R.string.tv_noNfc)
        }
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.disableForegroundDispatch(this)
    }

    private fun initView() {
        mTvView = findViewById<View>(R.id.nfc_activity_tv_info) as TextView
    }

    private fun checkNFCEnable(): Boolean {
        return if (mNfcAdapter == null) {
            mTvView.text = getString(R.string.tv_noNfc)
            false
        } else {
            mNfcAdapter?.isEnabled == true
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag)
        isoDep.timeout = 5000
        isoDep.connect()

        var response = isoDep.transceive(APDU_SELECT_CARD_ACCESS)
        println(response.toHex())
        val cardAccessData = readFile(
            isoDep = isoDep,
            offset = 0x0000,
            expectedResponseLength = 0x100
        )
        performPACE(
            isoDep = isoDep
        )

        isoDep.close()
    }

    fun readFile(
        isoDep: IsoDep,
        offset: Int,
        expectedResponseLength: Int
    ): ByteArray {
        val totalData = mutableListOf<Byte>()
        var currentOffset = offset
        var isEndOfFile = false

        while (!isEndOfFile) {
            // Tính toán offset byte cao (P1) và byte thấp (P2)
            val p1 = (currentOffset shr 8).toByte()
            val p2 = (currentOffset and 0xFF).toByte()

            // Gửi lệnh APDU READ BINARY
            val command = byteArrayOf(
                0x00.toByte(), // CLA
                0xB0.toByte(), // INS (READ BINARY)
                p1, // P1 (MSB của offset)
                p2, // P2 (LSB của offset)
                expectedResponseLength.toByte() // Le (Độ dài dữ liệu mong muốn)
            )
            val response = isoDep.transceive(command)
            println(response.toHex())

            // Kiểm tra trạng thái trả về
            val status = response.takeLast(2).toByteArray()
            if (status.contentEquals(byteArrayOf(0x90.toByte(), 0x00.toByte()))) {
                // Đọc dữ liệu hợp lệ
                val data = response.dropLast(2).toByteArray()
                totalData.addAll(data.asList())
                currentOffset += data.size

                // Kiểm tra nếu dữ liệu đọc nhỏ hơn yêu cầu thì đã đến cuối file
                if (data.size < expectedResponseLength) {
                    isEndOfFile = true
                }
            } else if (status.contentEquals(byteArrayOf(0x6A.toByte(), 0x82.toByte()))) {
                // File không tồn tại
                throw Exception("File not found")
            } else {
                // Các lỗi khác
                throw Exception("APDU command failed with status: ${status.toHex()}")
            }
        }

        return totalData.toByteArray()
    }

    fun performPACE(isoDep: IsoDep) {

        // 1. Select PACE
        var response = isoDep.transceive(APDUCommand.APDU_SELECT_PACE)
        println("PACE selected: ${response.toHex()}")

        // 2. Set Algorithm (MSE:SET AT)
        response = isoDep.transceive(APDUCommand.APDU_MSE_SET_AT)
        println("MSE:SET AT response: ${response.toHex()}")

        // 3. Get Challenge
        response = isoDep.transceive(APDUCommand.APDU_GET_CHALLENGE)
        val nonce = response.copyOfRange(0, response.size - 2) // Exclude SW1 SW2
        println("Challenge APDU response: ${response.toHex()}")
        println("Challenge: ${nonce.toHex()}")

        val last6CANNumber = "013808"
        val canKey = generateCANPassword(last6CANNumber)

        val appKeyPair = generateAppKeyPair()
        val appPublicKey = appKeyPair.public
        val appPrivateKey = appKeyPair.private
        val cardPublicKey = sendAppPublicKeyAndGetCardPublicKey(
            isoDep = isoDep,
            appPublicKey = appPublicKey
        )
        // shared secret
        val baseKey = calculateBaseKey(
            privateKey = appPrivateKey,
            cardPublicKey = cardPublicKey
        )
        println("baseKey: ${baseKey.toHex()}")


        isoDep.close()
    }

    // Extension function for ByteArray to Hex String
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun generateCANPassword(can: String): ByteArray {
        // Chuyển số CAN thành mảng byte theo chuẩn ISO/IEC 8859-1
        return can.toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Tạo cặp khóa ECDH cho ứng dụng.
     * @return Cặp khóa (PrivateKey và PublicKey).
     */
    fun generateAppKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC") // EC: Elliptic Curve
        keyPairGenerator.initialize(256) // Độ dài khóa (256-bit curve)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Tính toán Base Key (Kπ) bằng ECDH.
     * @param privateKey Khóa riêng tư của ứng dụng (K_APP).
     * @param cardPublicKey Khóa công khai từ thẻ (P_CARD).
     * @return Base Key (Shared Secret).
     */
    fun calculateBaseKey(privateKey: PrivateKey, cardPublicKey: ByteArray): ByteArray {
        // Tạo đối tượng PublicKey từ mảng byte P_CARD
        val keyFactory = KeyFactory.getInstance("EC")
        val pubKeySpec = X509EncodedKeySpec(cardPublicKey)
        val publicKey: PublicKey = keyFactory.generatePublic(pubKeySpec)

        // Khởi tạo KeyAgreement với Private Key (K_APP)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)

        // Trao đổi khóa để tính toán shared secret
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret() // Shared Secret (Base Key)
    }

    fun sendAppPublicKeyAndGetCardPublicKey(isoDep: IsoDep, appPublicKey: PublicKey): ByteArray {
        // Mã hóa khóa công khai của ứng dụng (\(P_{\text{APP}}\)) theo chuẩn X.509
        val appPublicKeyEncoded = appPublicKey.encoded

        // Tạo lệnh APDU chứa khóa công khai ứng dụng
        val apdu = byteArrayOf(
            0x00.toByte(), // CLA
            0x86.toByte(), // INS (General Authenticate)
            0x00.toByte(), // P1
            0x00.toByte(), // P2
            (appPublicKeyEncoded.size + 4).toByte(), // Lc
            0x7C.toByte(), // Tag for Dynamic Authentication Data
            (appPublicKeyEncoded.size + 2).toByte(), // Length of Sub-tag + Public Key
            0x83.toByte()  // Sub-tag for Public Key
        ) + appPublicKeyEncoded

        // Gửi lệnh APDU và nhận phản hồi từ thẻ
        val response = isoDep.transceive(apdu)

        // Loại bỏ 2 byte trạng thái (SW1, SW2) để lấy dữ liệu phản hồi
        return response.copyOf(response.size - 2)
    }
}