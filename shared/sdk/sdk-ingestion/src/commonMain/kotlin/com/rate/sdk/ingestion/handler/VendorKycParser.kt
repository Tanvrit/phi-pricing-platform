package com.rate.sdk.ingestion.handler

import com.rate.sdk.catalog.model.Vendor
import com.rate.sdk.catalog.model.VendorBank
import com.rate.sdk.catalog.model.VendorCategory
import com.rate.sdk.catalog.model.VendorDocType

/**
 * PURE-KMP parser for the "New Vendor Registration / KYC" form
 * (`New_Vendor_KYC_Form/KYC.csv`) into a single [Vendor] entity.
 *
 * Unlike the benefit-table CSVs (rows of records), the KYC export is a SINGLE-RECORD
 * label:value FORM: each line carries a field LABEL in one column and the operator-entered
 * VALUE in the next column. The label sits in col 2 and the value (if any) in col 3 for most
 * fields; the trailing "Documents Required :" block lists the required-document checklist
 * across col 3 of several consecutive rows (e.g. "1. Copy of PAN Card").
 *
 * Because the blank source template (`KYC.csv`) carries labels with no values, this parser is
 * tolerant — missing values default to empty strings (Vendor's own field defaults), and the
 * required-documents block is recognised by the document descriptions regardless of whether the
 * identity fields were filled in. When values ARE present, ids are validated:
 *  - PAN  : 10 chars, 5 letters + 4 digits + 1 letter (e.g. ABCDE1234F)
 *  - GST  : 15 chars, 2 digits + 10-char PAN + 1 alnum + 'Z' + 1 alnum
 *  - IFSC : 11 chars, 4 letters + '0' + 6 alnum
 * Invalid values are DROPPED (left blank) rather than throwing, matching the ingestion contract
 * (skip + the caller warns). [validatePan] / [validateGst] / [validateIfsc] are exposed so the
 * seeder can surface validation warnings on filled-in forms.
 *
 * Output is exactly ONE [Vendor]; the seeder upserts it. The Vendor is the GLOBAL (no
 * productLine) onboarding entity shared across RETAIL and GROUP.
 */
class VendorKycParser {

    /**
     * Parse the KYC form text into a single [Vendor]. The form is label-in-one-col,
     * value-in-the-next-col; we scan every cell of each row to find the label, then read the
     * first non-blank cell strictly to its right as the value. The "Documents Required :" block
     * (and its numbered "1. Copy of …" continuation rows) maps onto [VendorDocType]s.
     */
    fun parseVendor(csv: String): Vendor {
        val rows = Csv.parse(csv)

        var vendorName = ""
        var officeAddress = ""
        var contactPerson = ""
        var contactPhoneOffice = ""
        var contactPhoneMobile = ""
        var email = ""
        var website = ""
        var category = VendorCategory.SERVICE
        var pan = ""
        var gst = ""
        var msme: String? = null
        var accountNo = ""
        var bankName = ""
        var bankAddress = ""
        var ifsc = ""
        val docs = LinkedHashSet<VendorDocType>()
        var inDocsBlock = false

        for (r in rows) {
            if (Csv.isBlankRecord(r)) continue
            // The label is the first non-blank cell on the row; its value is the next non-blank cell.
            val (labelIdx, label) = firstNonBlankCell(r) ?: continue
            val labelKey = label.normaliseLabel()
            val value = firstNonBlankAfter(r, labelIdx)

            // The "Documents Required :" cell may itself carry the first document in its value
            // column ("Documents Required :","1. Copy of PAN Card"); subsequent numbered lines
            // are continuation rows of the block.
            if (labelKey.startsWith("documents required")) {
                inDocsBlock = true
                docTypeFor(value)?.let { docs.add(it) }
                continue
            }
            if (inDocsBlock && isDocLine(label)) {
                docTypeFor(label)?.let { docs.add(it) }
                continue
            }
            // Any non-doc labelled row ends the documents block.
            inDocsBlock = false

            when {
                labelKey.startsWith("vendor name") -> vendorName = value
                labelKey.startsWith("vendor office address") || labelKey.startsWith("office address") ->
                    officeAddress = value
                labelKey.startsWith("contact person") -> contactPerson = value
                labelKey.startsWith("contact no office") || labelKey.startsWith("contact no. office") ->
                    contactPhoneOffice = value
                labelKey.startsWith("contact no mobile") || labelKey.startsWith("contact no. mobile") ->
                    contactPhoneMobile = value
                labelKey.startsWith("email") -> email = value
                labelKey.startsWith("website") -> website = value
                labelKey.startsWith("category") -> category = parseCategory(value)
                labelKey.startsWith("pan") -> pan = if (validatePan(value)) value.normalisePan() else ""
                labelKey.startsWith("gst") -> gst = if (validateGst(value)) value.normaliseGst() else ""
                labelKey.startsWith("msme") -> msme = value.ifBlank { null }
                // "Bank Details" is a section heading with no value — skip it.
                labelKey == "bank details" -> {}
                labelKey.startsWith("account no") -> accountNo = value
                labelKey.startsWith("bank name") -> bankName = value
                labelKey.startsWith("bank address") -> bankAddress = value
                labelKey.startsWith("ifs code") || labelKey.startsWith("ifsc") ->
                    ifsc = if (validateIfsc(value)) value.normaliseIfsc() else ""
            }
        }

        return Vendor(
            vendorName = vendorName,
            officeAddress = officeAddress,
            contactPerson = contactPerson,
            contactPhoneOffice = contactPhoneOffice,
            contactPhoneMobile = contactPhoneMobile,
            email = email,
            website = website,
            category = category,
            panNumber = pan,
            gstNumber = gst,
            msmeNumber = msme,
            bank = VendorBank(
                accountNo = accountNo,
                bankName = bankName,
                bankAddress = bankAddress,
                ifscCode = ifsc,
            ),
            requiredDocuments = docs.toList(),
        )
    }

    // ── Validation (exposed for seeder warnings) ────────────────────────────

    /** PAN: 5 letters + 4 digits + 1 letter (10 chars), e.g. ABCDE1234F. */
    fun validatePan(raw: String): Boolean =
        Regex("""^[A-Za-z]{5}[0-9]{4}[A-Za-z]$""").matches(raw.trim())

    /** GST: 2 digits + 10-char PAN + 1 alnum + 'Z' + 1 alnum (15 chars). */
    fun validateGst(raw: String): Boolean =
        Regex("""^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z][Zz][0-9A-Za-z]$""").matches(raw.trim())

    /** IFSC: 4 letters + '0' + 6 alnum (11 chars), e.g. HDFC0001234. */
    fun validateIfsc(raw: String): Boolean =
        Regex("""^[A-Za-z]{4}0[0-9A-Za-z]{6}$""").matches(raw.trim())

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseCategory(value: String): VendorCategory = when {
        value.contains("both", ignoreCase = true) -> VendorCategory.BOTH
        value.contains("product", ignoreCase = true) -> VendorCategory.PRODUCT
        value.contains("service", ignoreCase = true) -> VendorCategory.SERVICE
        else -> VendorCategory.SERVICE
    }

    /** Map a "Copy of PAN Card" / "Banker's certificate …" description onto a [VendorDocType]. */
    private fun docTypeFor(raw: String): VendorDocType? {
        val v = raw.lowercase()
        return when {
            v.contains("pan") -> VendorDocType.PAN_CARD
            v.contains("gst") -> VendorDocType.GST_CERT
            v.contains("msme") -> VendorDocType.MSME_CERT
            v.contains("cancelled cheque") || v.contains("cancel cheque") -> VendorDocType.CANCELLED_CHEQUE
            v.contains("banker") || v.contains("bank account details") -> VendorDocType.BANKERS_CERT
            else -> null
        }
    }

    /** A required-documents continuation line: "1. Copy of …", "2. Copy of …". */
    private fun isDocLine(raw: String): Boolean =
        Regex("""^\s*\d+[.)]\s*""").containsMatchIn(raw)

    /** First non-blank cell in a record, with its column index, or null if all blank. */
    private fun firstNonBlankCell(record: List<String>): Pair<Int, String>? {
        for (i in record.indices) {
            val v = Csv.cell(record, i)
            if (v.isNotEmpty()) return i to v
        }
        return null
    }

    /** First non-blank cell strictly after column [idx] (the value of a label:value row). */
    private fun firstNonBlankAfter(record: List<String>, idx: Int): String {
        for (i in (idx + 1) until record.size) {
            val v = Csv.cell(record, i)
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /** Normalise a label for matching: lowercase, strip a trailing ':' and "(if applicable)". */
    private fun String.normaliseLabel(): String =
        trim()
            .removeSuffix(":")
            .trim()
            .lowercase()
            .replace(Regex("""\(if applicable\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .removeSuffix(":")
            .trim()

    private fun String.normalisePan(): String = trim().uppercase()
    private fun String.normaliseGst(): String = trim().uppercase()
    private fun String.normaliseIfsc(): String = trim().uppercase()
}
