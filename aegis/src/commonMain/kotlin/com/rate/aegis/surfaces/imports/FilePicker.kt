package com.rate.aegis.surfaces.imports

/**
 * Result of asking the host to pick + read a single file. Null = user cancelled.
 *
 * `bytes` is the full file contents — we eagerly slurp because both the JVM
 * AWT FileDialog path and the WASM `FileReader` path naturally produce a full
 * buffer, and the downstream consumer ([ApiClient.uploadExcelBytes]) expects a
 * `ByteArray` it can hand to Ktor's multipart `formData { append(...) }`.
 * Rate workbooks are small (single-digit MB) so we don't bother streaming.
 */
data class PickedFile(val name: String, val bytes: ByteArray) {
    // Data-class equality on a ByteArray field would compare by reference. We
    // never actually need value-equality on PickedFile (it's a one-shot
    // result-bag, not a Compose state key), but override for correctness so
    // accidental `==` doesn't silently mislead a future caller.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * Open the host file-picker, return chosen bytes + name. Suspending because the
 * WASM actual must await the `change` event on the hidden `<input type="file">`;
 * the JVM actual is blocking-ish (AWT FileDialog) but we keep the signature
 * unified so callers don't have to fork.
 *
 * @param filterDescription human-readable label (e.g. "Excel workbooks") —
 *   ignored on WASM where the `<input accept="…">` attribute drives filtering.
 * @param extensions allowed extensions, lowercase, no leading dot
 *   (e.g. `listOf("xls", "xlsx", "xlsm")`).
 * @return the picked file, or `null` if the user dismissed the dialog.
 */
expect suspend fun pickFile(filterDescription: String, extensions: List<String>): PickedFile?
