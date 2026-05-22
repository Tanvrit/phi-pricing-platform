package com.rate.buyonline.ui.payment

import com.rate.domain.money.formatRupees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun PaymentScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 2, currentStep = "Payment")

        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Plan Summary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Review the summary before proceeding to payment.", fontSize = 13.sp, color = PruSubtext)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryItem("Plan",        "PRUHealth ${vm.selectedTier.displayName}")
                    SummaryItem("Sum Insured", vm.formatSI(vm.selectedSumInsured))
                    SummaryItem("Tenure",      "${vm.selectedTenure} year(s)")
                    SummaryItem("Members",     vm.allMembers.joinToString(", "))
                    HorizontalDivider()
                    // Premium ledger — every component shown so the customer can reconcile
                    // the figure they're about to pay.
                    SummaryItem("Sub-total (pre-tax)", formatRupees(vm.totalAnnualPreTax, 2))
                    SummaryItem("GST (18%)",           formatRupees(vm.gstAmount, 2))
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total payable", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(formatRupees(vm.totalAnnualWithGst), fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp, color = PruRed)
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PruInfoBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You will be redirected to a secure payment gateway", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Accepted: UPI, Debit Card, Credit Card, Net Banking", fontSize = 12.sp, color = PruSubtext)
                    Text("Payment gateway integration is Phase 2; in this build the Pay button " +
                            "simulates a successful transaction.", fontSize = 11.sp, color = PruSubtext)
                }
            }

            // IRDAI-style T&C acceptance required before payment. Defence-in-depth: the
            // server-side proposal endpoint should also require this in Phase 2.
            var tcAccepted by remember { mutableStateOf(false) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = tcAccepted, onCheckedChange = { tcAccepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = PruRed))
                Text(
                    "I confirm the information provided is accurate and accept the Terms & " +
                            "Conditions, Privacy Policy, and the 15-day free-look period.",
                    fontSize = 12.sp, color = PruText
                )
            }

            Spacer(Modifier.weight(1f))
            Text("🔒 Your payment is protected by 256-bit SSL encryption", fontSize = 12.sp, color = PruSubtext)

            Surface(shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    PRUButton(
                        text = "Pay ${formatRupees(vm.totalAnnualWithGst)}",
                        onClick = { vm.onPaymentComplete() },
                        enabled = tcAccepted && !vm.loading && vm.totalAnnualWithGst > 0.0
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = PruSubtext)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
