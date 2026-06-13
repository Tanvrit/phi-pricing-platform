package com.rate.server.email

import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

/**
 * Minimal portable e-mail value object. Plain-text body is mandatory (the fallback when HTML is
 * suppressed); `bodyHtml` is the optional alternate MIME part.
 *
 * RELOCATED verbatim from the monolith's `server.email` — it is already JVM-app-layer infra
 * (no Mongo / no domain coupling), so it survives the re-arch as a thin server package.
 */
data class EmailMessage(
    val to: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
)

interface EmailSender {
    suspend fun send(message: EmailMessage): Boolean
}

/**
 * Phase-1 sender that writes each email as a `.eml` file under `${user.home}/.aegis/outbox/`. The
 * SMTP/SES integration in Phase 2 swaps this behind the same [EmailSender] interface — routes only
 * see the interface.
 */
class FileSystemEmailSender(
    /** Public so the admin diagnostic route can list the same directory we write to. */
    val outboxDir: File = File(System.getProperty("user.home"), ".aegis/outbox"),
) : EmailSender {
    private val log = LoggerFactory.getLogger(FileSystemEmailSender::class.java)

    override suspend fun send(message: EmailMessage): Boolean {
        return try {
            outboxDir.mkdirs()
            val now = Clock.System.now().toString()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest("$now${message.to}${message.subject}".toByteArray())
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                .take(8)
            val safeTo = message.to.replace("[^a-zA-Z0-9.@_-]".toRegex(), "_")
            val file = File(outboxDir, "$now-$safeTo-$hash.eml")
            file.writeText(buildEmlText(message))
            log.info("email.outbox.write file={} to={}", file.name, message.to)
            true
        } catch (t: Throwable) {
            log.warn("email.outbox.write_failed reason={}", t.message)
            false
        }
    }

    private fun buildEmlText(m: EmailMessage): String = buildString {
        append("From: aegis@example.com\n")
        append("To: ${m.to}\n")
        append("Subject: ${m.subject}\n")
        append("Date: ${Clock.System.now()}\n")
        append("Content-Type: ${if (m.bodyHtml != null) "multipart/alternative; boundary=\"aegis-boundary\"" else "text/plain; charset=utf-8"}\n")
        append("\n")
        if (m.bodyHtml != null) {
            append("--aegis-boundary\n")
            append("Content-Type: text/plain; charset=utf-8\n\n")
            append(m.bodyText).append("\n\n")
            append("--aegis-boundary\n")
            append("Content-Type: text/html; charset=utf-8\n\n")
            append(m.bodyHtml).append("\n\n")
            append("--aegis-boundary--\n")
        } else {
            append(m.bodyText)
        }
    }
}
