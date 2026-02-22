package com.rate.buyonline.ui.payment

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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Premium", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹%,.0f".format(vm.totalPremium), fontWeight = FontWeight.ExtraBold,
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
                }
            }

            Spacer(Modifier.weight(1f))
            Text("🔒 Your payment is protected by 256-bit SSL encryption", fontSize = 12.sp, color = PruSubtext)
        }

        Surface(shadowElevation = 8.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                PRUButton("Pay ₹%,.0f".format(vm.totalPremium), { vm.onPaymentComplete() }, enabled = !vm.loading)
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
