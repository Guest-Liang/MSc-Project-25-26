package icu.guestliang.nfcworkflow.nfc

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import java.nio.charset.Charset
import android.content.Context
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA

fun getUriPrefix(identifierCode: Byte): String {
    return when (identifierCode.toInt()) {
        0x01 -> "http://www."
        0x02 -> "https://www."
        0x03 -> "http://"
        0x04 -> "https://"
        0x05 -> "tel:"
        0x06 -> "mailto:"
        else -> ""
    }
}

data class ParsedNfcData(
    val uidHex: String,
    val rawText: String,
    val ndefText: String?
)

private val SUPPORTED_UID_HEX_LENGTHS = setOf(14, 16, 20)

private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

private fun parseNdefTextPayload(payload: ByteArray): String? {
    if (payload.isEmpty()) return null

    val textEncoding = if ((payload[0].toInt() and 0x80) == 0) "UTF-8" else "UTF-16"
    val languageCodeLength = payload[0].toInt() and 0x3F
    val textStart = 1 + languageCodeLength

    if (textStart >= payload.size) return null

    return String(
        payload,
        textStart,
        payload.size - textStart,
        Charset.forName(textEncoding)
    )
}

fun parseNfcTagData(tag: Tag, context: Context): ParsedNfcData {
    val uidHex = tag.id.toHexString()
    val rawText = parseNfcTag(tag, context)
    var ndefText: String? = null

    if (uidHex.length !in SUPPORTED_UID_HEX_LENGTHS) {
        AppLogger.warn(context, "Unsupported NFC UID length: ${uidHex.length}", "NFC")
    }

    val ndef = Ndef.get(tag)
    if (ndef != null) {
        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            if (ndefMessage != null) {
                for (record in ndefMessage.records) {
                    if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        try {
                            val payload = record.payload
                            val parsedText = parseNdefTextPayload(payload)
                            if (parsedText != null) {
                                ndefText = parsedText
                                break
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            try {
                ndef.close()
            } catch (e: Exception) {
                AppLogger.warn(context, "Failed to close NDEF after parsing tag data: ${e.message}", "NFC")
            }
        }
    }
    return ParsedNfcData(uidHex, rawText, ndefText)
}

fun parseNfcTag(tag: Tag, context: Context): String {
    val sb = StringBuilder()
    
    val techList = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
    sb.append(context.getString(R.string.nfc_tech_supported, techList)).append("\n")
    
    val mifareClassic = MifareClassic.get(tag)
    if (mifareClassic != null) {
        sb.append(context.getString(R.string.nfc_card_type_mifare_classic)).append("\n")
        sb.append(context.getString(R.string.nfc_storage_size, mifareClassic.size)).append("\n")
        sb.append(context.getString(R.string.nfc_sector_count, mifareClassic.sectorCount)).append("\n")
        sb.append(context.getString(R.string.nfc_block_count, mifareClassic.blockCount)).append("\n")
    }

    val mifareUltralight = MifareUltralight.get(tag)
    if (mifareUltralight != null) {
        sb.append(context.getString(R.string.nfc_card_type_mifare_ultralight)).append("\n")
    }

    val nfcA = NfcA.get(tag)
    if (nfcA != null) {
        val atqa = nfcA.atqa.reversedArray().joinToString("") { "%02X".format(it) }
        sb.append(context.getString(R.string.nfc_rf_tech_type_a)).append("\n")
        sb.append(context.getString(R.string.nfc_protocol_atqa, atqa)).append("\n")
        sb.append(context.getString(R.string.nfc_sak, "%02X".format(nfcA.sak))).append("\n")
    }
    
    val idHex = tag.id.toHexString()
    sb.append(context.getString(R.string.nfc_id, idHex)).append("\n")

    val ndef = Ndef.get(tag)
    if (ndef != null) {
        val isWritableStr = if (ndef.isWritable) context.getString(R.string.nfc_yes) else context.getString(R.string.nfc_no)
        val canMakeReadOnlyStr = if (ndef.canMakeReadOnly()) context.getString(R.string.nfc_yes) else context.getString(R.string.nfc_no)
        
        sb.append(context.getString(R.string.nfc_ndef_max_size, ndef.maxSize)).append("\n")
        sb.append(context.getString(R.string.nfc_ndef_is_writable, isWritableStr)).append("\n")
        sb.append(context.getString(R.string.nfc_ndef_can_make_read_only, canMakeReadOnlyStr)).append("\n")
        
        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            if (ndefMessage != null) {
                ndefMessage.records.forEachIndexed { index, record ->
                    sb.append(context.getString(R.string.nfc_record_index, index + 1)).append(" ")
                    when (record.tnf) {
                        NdefRecord.TNF_WELL_KNOWN if record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                            try {
                                val text = parseNdefTextPayload(record.payload)
                                if (text != null) {
                                    sb.append(context.getString(R.string.nfc_record_text, text)).append("\n")
                                } else {
                                    sb.append(context.getString(R.string.nfc_record_text_fail)).append("\n")
                                }
                            } catch (_: Exception) {
                                sb.append(context.getString(R.string.nfc_record_text_fail)).append("\n")
                            }
                        }
                        NdefRecord.TNF_WELL_KNOWN if record.type.contentEquals(NdefRecord.RTD_URI) -> {
                            try {
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    val prefix = getUriPrefix(payload[0])
                                    val uri = String(payload, 1, payload.size - 1, Charset.forName("UTF-8"))
                                    sb.append(context.getString(R.string.nfc_record_uri, prefix + uri)).append("\n")
                                }
                            } catch (_: Exception) {
                                sb.append(context.getString(R.string.nfc_record_uri_fail)).append("\n")
                            }
                        }
                        else -> {
                            sb.append(context.getString(R.string.nfc_record_other, record.tnf.toInt())).append("\n")
                            val payloadHex = record.payload.joinToString("") { "%02X".format(it) }
                            val payloadStr = if (payloadHex.length > 30) payloadHex.take(30) + "..." else payloadHex
                            sb.append(context.getString(R.string.nfc_record_payload, payloadStr)).append("\n")
                        }
                    }
                }
            } else {
                sb.append(context.getString(R.string.nfc_no_ndef_data)).append("\n")
            }
        } catch (_: Exception) {
             sb.append(context.getString(R.string.nfc_read_ndef_fail)).append("\n")
        } finally {
            try {
                ndef.close()
            } catch (e: Exception) {
                AppLogger.warn(context, "Failed to close NDEF after parsing tag details: ${e.message}", "NFC")
            }
        }
    } else {
        val ndefFormatable = NdefFormatable.get(tag)
        if (ndefFormatable != null) {
             sb.append(context.getString(R.string.nfc_support_ndef_format)).append("\n")
        }
    }
    
    return sb.toString().trim()
}
