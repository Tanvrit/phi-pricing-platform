package com.rate.server.email

import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

/**
 * Minimal portable e-mail value object. Plain-text body is mandatory (acts as
 * the fallback when HTML rendering is suppressed by the recipient client);
 * `bodyHtml` is optional and, when supplied, gets emitted as the alternate
 * MIME part so well-behaved clients pick it up.
 */
data class EmailMessage(
    val to: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null
)

interface EmailSender {
    suspend fun send(message: EmailMessage): Boolean
}

/**
 * Phase-1 sender that writes each email as a `.eml` file under
 * `${user.home}/.aegis/outbox/`. The file is RFC-822-ish — the SMTP
 * integration in Phase 2 will replace this with a real javamail / SES
 * client; the rest of the codebase stays unchanged because routes only
 * see [EmailSender].
 *
 * The output is deliberately readable by a plain text editor and by
 * any `.eml` viewer (mail.app, Thunderbird, mutt -f) so operators can
 * eyeball what would have been sent without a database or log dive.
 */
class FileSystemEmailSender(
    // Public so the admin diagnostic route (`/api/admin/outbox`) can list the
    // same directory we're writing to without re-deriving the path. Phase-2's
    // SMTP sender won't have a filesystem outbox, at which point the admin
    // route + this field both go away together.
    val outboxDir: File = File(System.getProperty("user.home"), ".aegis/outbox")
) : EmailSender {
    private val log = LoggerFactory.getLogger(FileSystemEmailSender::class.java)

    override suspend fun send(message: EmailMessage): Boolean {
        return try {
            outboxDir.mkdirs()
            val now = Clock.System.now().toString()
            // 8-char content hash disambiguates two writes in the same wall-clock
            // instant (rare but possible when tests fire in parallel). Cheap to
            // compute and human-readable in the filename.
            val hash = MessageDigest.getInstance("SHA-256")
                .digest("$now${message.to}${message.subject}".toByteArray())
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                .take(8)
            val safeTo = message.to.replace("[^a-zA-Z0-9.@_-]".toRegex(), "_")
            val file = File(outboxDir, "$now-$safeTo-$hash.eml")
            file.writeText(buildEmlText(message))
            // INFO log captures the file path + recipient so operators can grep
            // the outbox. The body itself is intentionally NOT logged.
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
