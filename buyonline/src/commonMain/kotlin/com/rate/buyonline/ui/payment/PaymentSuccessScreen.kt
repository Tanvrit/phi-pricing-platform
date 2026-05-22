package com.rate.buyonline.ui.payment

import com.rate.domain.money.formatRupees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun PaymentSuccessScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        PRUTopBar(onSaveExit = {}, progress = 2, currentStep = "Payment")

        Column(Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(24.dp))
            Text("👍", fontSize = 56.sp)
            Text("Payment successful!", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Please complete your KYC for policy approval",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
                shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✓", color = PruRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("PRUHealth ${vm.selectedTier.displayName}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("View plan details →", color = PruRed, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.allMembers.forEach { member ->
                            Box(Modifier.background(PruRed.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(member, fontSize = 12.sp, color = PruRed)
                            }
                        }
                    }
                    HorizontalDivider()
                    TxnRow("Transaction ID", vm.transactionId)
                    TxnRow("Payment type",   vm.paymentMethod)
                    TxnRow("Amount paid",    formatRupees(vm.paymentAmount))
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.proceedToKyc() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PruRed)) {
                    Text("Complete KYC", color = PruRed)
                }
                Button(onClick = { vm.navigate(com.rate.buyonline.navigation.BuyOnlineScreen.LifestyleQuestions) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PruRed)) {
                    Text("Health questions", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TxnRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PruSubtext)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
