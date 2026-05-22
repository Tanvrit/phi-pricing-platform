package com.rate.aegis.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rate.aegis.components.*
import com.rate.aegis.theme.*

/**
 * Aegis gallery — a runnable Compose Desktop window that showcases every Aegis
 * component in every state. Designers / PMs / engineers use this for visual review
 * without booting the full dashboard. CI uses it as the visual-regression target.
 *
 * Per `DASHBOARD_REDESIGN_PLAN.md` Pass 6 §6.7 the gallery is a separate module in
 * the long term; for Ship-1 it's an inline `gallery/Main.kt` in `:aegis`.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
        title = "Aegis Component Gallery — PRUHealth"
    ) {
        AegisTheme { GalleryRoot() }
    }
}

private val galleryUser = AegisUser(
    displayName = "Vivek Singh",
    initials = "VS",
    role = "Engineer"
)

@Composable
private fun GalleryRoot() {
    var active by remember { mutableStateOf(AegisSurface.HOME) }
    AegisShell(
        activeSurface = active,
        onSurfaceChange = { active = it },
        user = galleryUser
    ) {
        when (active) {
            AegisSurface.HOME -> GalleryHome()
            else -> SurfacePlaceholder(active)
        }
    }
}

/**
 * Gallery home — renders every Aegis component in every state on one scrollable page.
 */
@Composable
private fun GalleryHome() {
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas)
            .verticalScroll(rememberScrollState()).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s6)
    ) {
        Text("Aegis Component Gallery",
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        Text("Pass 4 visual system · Pass 6 §6.7 — gallery is the visual-regression target. " +
                "Every Aegis component, every state, on one page.",
            fontSize = 14.sp, color = AegisColors.textSecondary)

        AegisCard(title = "Buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(label = "Primary",   onClick = {})
                    AegisButton(label = "Secondary", onClick = {}, variant = AegisButtonVariant.Secondary)
                    AegisButton(label = "Ghost",     onClick = {}, variant = AegisButtonVariant.Ghost)
                    AegisButton(label = "Danger",    onClick = {}, variant = AegisButtonVariant.Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(label = "Loading",  onClick = {}, loading = true)
                    AegisButton(label = "Disabled", onClick = {}, enabled = false)
                    AegisButton(label = "Small",    onClick = {}, size = AegisButtonSize.Sm)
                    AegisButton(label = "Large",    onClick = {}, size = AegisButtonSize.Lg)
                }
            }
        }

        AegisCard(title = "Status pills") {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisStatusPill(AegisStatus.Draft)
                AegisStatusPill(AegisStatus.InReview)
                AegisStatusPill(AegisStatus.Scheduled)
                AegisStatusPill(AegisStatus.Approved)
                AegisStatusPill(AegisStatus.Live)
                AegisStatusPill(AegisStatus.Retired)
                AegisStatusPill(AegisStatus.Rejected)
            }
        }

        AegisCard(title = "Callouts") {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisCallout(kind = CalloutKind.INFO,    title = "Info",    body = "Plain informational note.")
                AegisCallout(kind = CalloutKind.SUCCESS, title = "Success", body = "Plan v3.3 was published to LIVE.")
                AegisCallout(kind = CalloutKind.WARN,    title = "Warning", body = "Stale draft — refresh before editing.")
                AegisCallout(kind = CalloutKind.DANGER,  title = "Danger",  body = "Audit chain integrity check failed.")
            }
        }

        var demo by remember { mutableStateOf("") }
        AegisCard(title = "Inputs") {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(value = demo, onValueChange = { demo = it },
                    label = "Plan name", helper = "What customers see on the quote.")
                AegisInput(value = "ABCDE", onValueChange = {},
                    label = "PAN (with error)", error = "PAN must be 10 chars: AAAAA1234A.")
                AegisInput(value = "9876543210", onValueChange = {},
                    label = "Mobile", prefix = "+91")
            }
        }

        AegisCard(title = "Misc") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AegisBadge(count = 3)
                AegisBadge(count = 42, tone = AegisBadgeTone.Brand)
                AegisBadge(label = "NEW", tone = AegisBadgeTone.Success)
                AegisKbd(label = "⌘")
                AegisKbd(label = "⌘K")
                AegisKbd(label = "Enter")
                AegisChip(label = "Active",   selected = true,  onClick = {})
                AegisChip(label = "Inactive", selected = false, onClick = {})
                AegisChip(label = "Success",  selected = true,  onClick = {}, tone = AegisChipTone.Success)
            }
            Spacer(Modifier.height(AegisSpacing.s3))
            AegisHDivider()
        }

        AegisCard(title = "Empty state") {
            AegisEmptyState(
                title = "No quotes match your filters",
                helper = "Adjust the filters above or clear them to see all quotes."
            )
        }
    }
}

@Composable
private fun SurfacePlaceholder(surface: AegisSurface) {
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(surface.displayName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Ship-N surface — coming next iteration per DASHBOARD_REDESIGN_PLAN §6.1.",
            fontSize = 14.sp, color = AegisColors.textSecondary)
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "What lands here",
            body = "Aegis foundation (theme + 18 core components) is live. The 13 surfaces are " +
                    "the next ships — start with Home, Quotes, Plan Configurator (read mode), " +
                    "Cover Catalog. See Pass 6 §6.1 for the order."
        )
    }
}
