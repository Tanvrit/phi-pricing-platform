package com.rate.aegis.customer.buyonline.ui.loading

import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EligibilityScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        PRUTopBar(onBack = { vm.navigateBack() })

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("🎉", fontSize = 48.sp)
            Text("Great news!", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = PruText)
            Text("We can provide health coverage for the following members",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            if (vm.coveredMembers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.coveredMembers.forEach { member ->
                        Card(
                            shape  = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = PruSuccess.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, PruSuccess)
                        ) {
                            Text("✓ $member", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = PruSuccess, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
            }

            PRUButton("Check out the plans", { vm.proceedFromEligibility() })

            if (vm.uncoveredMembers.isNotEmpty()) {
                HorizontalDivider()
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Members not covered:", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.uncoveredMembers.forEach { member ->
                            Card(
                                shape  = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(member, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                    Text("Our team will reach out to explore suitable options for uncovered members.",
                        fontSize = 12.sp, color = PruSubtext, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
