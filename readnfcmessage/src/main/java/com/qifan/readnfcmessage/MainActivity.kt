package com.qifan.readnfcmessage

import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qifan.readnfcmessage.APDUCommand.APDU_SELECT_CARD_ACCESS
import com.qifan.readnfcmessage.utils.PACEHandler
import com.qifan.readnfcmessage.utils.PassportUtils
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement


class MainActivity : AppCompatActivity(), ReaderCallback {
    private var mNfcAdapter: NfcAdapter? = null
    private lateinit var mTvView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        println("isoDep ${isoDep.isConnected}")
        val response = isoDep.transceive(APDU_SELECT_CARD_ACCESS)
        println(response.toHex())
        val mrzKey = PassportUtils.getMRZKey(
            passportNumber = "097013808",
            dateOfBirth = "161197",
            dateOfExpiry = "161137"
        )
        println("mrzKey: $mrzKey")
        val paceKey = PACEHandler.createPaceKey(mrzKey)
        println("paceKey: ${paceKey.toHex()}")
        val cardAccessData =
            "3134300d060804007f0007020202020101300f060a04007f000702020302020201013012060a04007f0007020204020202010202010d".toByteArray() ?:
            readFile(
            isoDep = isoDep,
            offset = 0x0000,
            expectedResponseLength = 0x100
        )

        val asn1Element = ASN1Parser.parseIcaoData(cardAccessData)
        println()

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
        println("canKey: $canKey")

        isoDep.close()
    }

    fun generateCANPassword(can: String): ByteArray {
        // Chuyển số CAN thành mảng byte theo chuẩn ISO/IEC 8859-1
        return can.toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Tạo cặp khóa ECDH cho ứng dụng.
     * @return Cặp khóa (PrivateKey và PublicKey).
     */
    fun generateAppKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC") // Elliptic Curve
        val ecParameterSpec = ECGenParameterSpec("brainpoolP256r1")
        keyPairGenerator.initialize(ecParameterSpec)
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
}