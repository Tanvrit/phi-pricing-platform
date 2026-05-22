package com.rate.aegis.customer.buyonline.ui.complete

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
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun ApplicationCompleteScreen(vm: BuyOnlineViewModel) {
    val result = vm.applicationResult

    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar()

        Column(Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(16.dp))
            Text("🎊", fontSize = 56.sp)
            Text("Application complete!", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Your proposal is in review. We will get in touch with you shortly.",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            if (result != null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.background(PruRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("PRUHealth ${result.planTier}", color = PruRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        HorizontalDivider()
                        ProposalRow("Proposal No.",  result.proposalNumber)
                        ProposalRow("Plan",          "PRUHealth ${result.planTier}")
                        ProposalRow("Sum Insured",   vm.formatSI(result.sumInsured))
                        ProposalRow("Premium/year",  formatRupees(result.annualPremium))
                        ProposalRow("Members",       "${vm.allMembers.size}")
                        ProposalRow("Tenure",        "${vm.selectedTenure} year(s)")
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Status", fontSize = 14.sp, color = PruSubtext)
                            Box(Modifier.background(Color(0xFFFFF9C4), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(result.status, color = Color(0xFFF57F17),
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // IRDAI footer surfaced on the final screen so the customer leaves with
            // grievance + ombudsman + free-look + UIN visible alongside their proposal.
            com.rate.aegis.customer.buyonline.ui.components.IrdaiComplianceFooter()

            Spacer(Modifier.weight(1f))
            PRUButton("Track proposal", { vm.proceedToSatisfaction() })
            TextButton(onClick = { vm.navigate(com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen.Landing) }) {
                Text("Back to home", color = PruSubtext)
            }
        }
    }
}

@Composable
private fun ProposalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = PruSubtext)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
