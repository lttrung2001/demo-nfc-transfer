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
        readFile(
            isoDep = isoDep,
            offset = 0x0000,
            expectedResponseLength = 0x100
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
}
