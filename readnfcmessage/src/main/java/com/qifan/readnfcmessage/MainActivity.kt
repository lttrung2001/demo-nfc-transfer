package com.qifan.readnfcmessage

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.qifan.readnfcmessage.parser.NdefMessageParser


class MainActivity : AppCompatActivity(), ReaderCallback {
    private var mNfcAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private lateinit var mTvView: TextView

    @RequiresApi(Build.VERSION_CODES.M)
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
        println("onCreate")
    }

    override fun onResume() {
        super.onResume()
//        mPendingIntent =
//            PendingIntent.getActivity(
//                this,
//                0,
//                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//                    .setAction(NfcAdapter.ACTION_TAG_DISCOVERED),
//                PendingIntent.FLAG_IMMUTABLE
//            )
//        mNfcAdapter?.enableForegroundDispatch(this, mPendingIntent, null, null)
        println("onResume")
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.disableForegroundDispatch(this)
    }

//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
//            println("NfcAdapter.ACTION_NDEF_DISCOVERED")
//            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMessages ->
//                val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
//                // Process the messages array.
//                parserNDEFMessage(messages)
//            }
//        }
//    }

    private fun parserNDEFMessage(messages: List<NdefMessage>) {
        val builder = StringBuilder()
        val records = NdefMessageParser.parse(messages[0])
        val size = records.size

        for (i in 0 until size) {
            val record = records[i]
            val str = record.str()
            builder.append(str).append("\n")
        }
        mTvView.text = builder.toString()
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
        isoDep.connect()
        println(isoDep.isConnected)
        val APDU_SELECT = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xA4.toByte(), // INS	- Instruction - Instruction code
            0x04.toByte(), // P1	- Parameter 1 - Instruction parameter 1
            0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
            0x07.toByte(), // Lc field	- Number of bytes present in the data field of the command
            0xD2.toByte(),
            0x76.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x85.toByte(),
            0x01.toByte(),
            0x01.toByte(), // NDEF Tag Application name
            0x00.toByte(), // Le field	- Maximum number of bytes expected in the data field of the response to the command
        )

        val GET_TEXT_APDU = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xb0.toByte(), // INS	- Instruction - Instruction code
            0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
            0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
            0x00.toByte(), // Lc field	- Number of bytes present in the data field of the command
        )

        var response = isoDep.transceive(APDU_SELECT)
        println(response.toHex())
        response = isoDep.transceive(GET_TEXT_APDU)
        decodeResponseApdu(response).let {
            runOnUiThread {
                mTvView.text = it
            }
        }
//            nfcA.close()
    }

    private fun ByteArray.toHex(): String {
        val HEX_CHARS = "0123456789ABCDEF".toCharArray()

        val result = StringBuffer()

        forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }

        return result.toString()
    }

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
}
