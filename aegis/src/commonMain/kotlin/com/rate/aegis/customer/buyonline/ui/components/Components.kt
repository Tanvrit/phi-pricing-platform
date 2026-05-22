package com.rate.aegis.customer.buyonline.ui.components

import com.rate.domain.money.formatRupees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.ui.theme.*

@Composable
fun PRUTopBar(
    onBack: (() -> Unit)? = null,
    onSaveExit: (() -> Unit)? = null,
    progress: Int = 0,
    currentStep: String = ""
) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onBack?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PruRed)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text("PRUDENTIAL", style = MaterialTheme.typography.titleMedium,
                color = PruRed, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            onSaveExit?.let {
                TextButton(onClick = it) { Text("Save & Exit", color = PruRed, fontSize = 12.sp) }
            }
        }
        if (progress > 0 && currentStep.isNotEmpty()) {
            ProgressTabs(currentStep)
        }
        HorizontalDivider()
    }
}

@Composable
fun ProgressTabs(currentStep: String) {
    val steps = listOf("Quote", "Payment", "KYC", "Health")
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp)) {
        steps.forEach { step ->
            val isActive = step == currentStep
            Column(
                Modifier.weight(1f).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(step, fontSize = 12.sp,
                    color      = if (isActive) PruRed else PruSubtext,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
                if (isActive) Box(Modifier.fillMaxWidth().height(2.dp).background(PruRed))
            }
        }
    }
}

@Composable
fun MemberSummaryBar(membersText: String, onExpand: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF8F8F8))
            .padding(horizontal = 16.dp, vertical = 8.dp).clickable { onExpand() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Members: $membersText", fontSize = 13.sp, color = PruText, modifier = Modifier.weight(1f))
        Text("▾", color = PruSubtext)
    }
}

@Composable
fun PRUButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Boolean = false
) {
    if (outline) {
        OutlinedButton(
            onClick = onClick, enabled = enabled,
            modifier = modifier.fillMaxWidth().height(48.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) PruRed else Color.Gray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PruRed)
        ) { Text(text, fontWeight = FontWeight.SemiBold) }
    } else {
        Button(
            onClick = onClick, enabled = enabled,
            modifier = modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PruRed)
        ) {
            if (!enabled && text.startsWith("Pay")) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun InfoBanner(title: String, body: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruInfoBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, PruInfo.copy(alpha = 0.4f)),
        shape  = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ℹ", color = PruInfo, fontSize = 16.sp)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PruText)
                Text(body,  fontSize = 12.sp, color = PruSubtext)
            }
        }
    }
}

@Composable
fun PopularBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(PruBadge).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("Popular", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChipSelector(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg     = if (selected) PruRed else Color.White
    val border = if (selected) PruRed else Color(0xFFBDBDBD)
    val text   = if (selected) Color.White else PruText
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 13.sp, color = text, fontWeight = FontWeight.Medium) }
}

@Composable
fun ErrorBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape  = RoundedCornerShape(8.dp)
    ) { Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
}

@Composable
fun StickyPriceBar(annualPremium: Double, onProceed: () -> Unit, proceedText: String = "Proceed") {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${formatRupees(annualPremium)} /yr", fontWeight = FontWeight.Bold, color = PruText, fontSize = 18.sp)
                Text("+ GST", fontSize = 11.sp, color = PruSubtext)
            }
            Button(
                onClick = onProceed,
                colors  = ButtonDefaults.buttonColors(containerColor = PruRed),
                modifier = Modifier.height(44.dp)
            ) { Text(proceedText, fontWeight = FontWeight.SemiBold) }
        }
    }
}
