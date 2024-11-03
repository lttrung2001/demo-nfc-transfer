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
            offset = byteArrayOf(0x00, 0x00),
            expectedResponseLength = 0x04
        ).let {
            println(it.toHex())
            ASN1Parser.parse(it)
        }

        isoDep.close()
    }

    fun readFile(
        isoDep: IsoDep,
        offset: ByteArray,
        expectedResponseLength: Int,
    ): ByteArray {
        var response = ByteArray(2)
        println(response.toHex())
        var totalBytesRead = 0
        var isEndOfFile = false
        val currentOffset = offset.copyOf()
        var totalData = byteArrayOf()
        while (!isEndOfFile) {
            response = isoDep.transceive(
                byteArrayOf(
                    0x00.toByte(), // CLA (Class byte, mặc định 0x00)
                    0xB0.toByte(), // INS (Instruction byte cho SELECT)
                    currentOffset[0], // offset
                    currentOffset[1], // offset
                    expectedResponseLength.toByte()
                )
            )
            println(response.toHex())
            totalBytesRead += response.size - 2
            currentOffset[1] = (currentOffset[1] + response.size - 2).toByte()
            val data = response.copyOfRange(0, response.size - 2)
            val status = response.copyOfRange(response.size - 2, response.size)
            isEndOfFile = status.contentEquals(byteArrayOf(0x6A.toByte(), 0x86.toByte()))
            totalData += data
        }
        return totalData
    }
}
