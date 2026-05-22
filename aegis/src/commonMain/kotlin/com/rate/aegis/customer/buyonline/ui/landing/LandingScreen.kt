package com.rate.aegis.customer.buyonline.ui.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.model.MemberType
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun LandingScreen(vm: BuyOnlineViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PRUDENTIAL", style = MaterialTheme.typography.titleLarge,
                    color = PruRed, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("☰", fontSize = 20.sp, color = PruRed)
            }

            // Hero
            Box(
                Modifier.fillMaxWidth().background(PruRed).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "India's health insurance\nthat puts your family first",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Find the right plan in a few seconds",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            // Form card
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Who are you insuring?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                    // Member chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberChip("Self",   MemberType.SELF,   vm)
                        MemberChip("Spouse", MemberType.SPOUSE, vm)
                        MemberChip("Kids",   MemberType.KIDS,   vm)
                    }

                    if (MemberType.KIDS in vm.selectedMembers) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("No. of kids:", fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { if (vm.kidsCount > 0) vm.kidsCount-- },
                                    modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("−", fontSize = 18.sp, color = PruRed)
                                }
                                Text("${vm.kidsCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                TextButton(onClick = { if (vm.kidsCount < 4) vm.kidsCount++ },
                                    modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("+", fontSize = 18.sp, color = PruRed)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = vm.eldestAge,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) vm.eldestAge = it },
                        label = { Text("Age of eldest member") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vm.mobile,
                        onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) vm.mobile = it },
                        label = { Text("Mobile number") },
                        prefix = { Text("+91 ", color = PruSubtext) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = vm.consentGiven,
                            onCheckedChange = { vm.consentGiven = it },
                            colors = CheckboxDefaults.colors(checkedColor = PruRed)
                        )
                        Column {
                            Text("I authorise Prudential Health India to Call, SMS, Email & WhatsApp for information & services.",
                                fontSize = 12.sp, color = PruSubtext)
                            Text("Learn more", fontSize = 12.sp, color = PruRed,
                                modifier = Modifier.clickable { vm.showTerms = true })
                        }
                    }

                    PRUButton(
                        text    = "Get the best offer",
                        onClick = { vm.submitMobileForOtp() },
                        enabled = vm.mobile.length == 10 && vm.eldestAge.isNotEmpty() && vm.consentGiven
                    )
                }
            }

            // Feature pills
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                // Trust pills — these are the customer-visible claims.
                // The "99% Claim Approval" pill was removed pre-launch: any displayed
                // claim ratio must cite IRDAI Annual Report. Pass 2 of the audit calls
                // this out; until we have a real ratio we display only IRDAI registration.
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    FeaturePill("🏥", "PRU Network")
                    FeaturePill("🛡️", "IRDAI Registered")
                    FeaturePill("📜", "15-day Free-Look")
                }
            }
            Spacer(Modifier.height(16.dp))
            IrdaiComplianceFooter(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(32.dp))
        }

        if (vm.showTerms) {
            AlertDialog(
                onDismissRequest = { vm.showTerms = false },
                title = { Text("Terms and Conditions") },
                text  = { Text("By using this website, you agree to be bound by these terms. Prudential Health Insurance Company Limited is registered with IRDAI. All policies are subject to terms and conditions.", fontSize = 13.sp) },
                confirmButton = { TextButton(onClick = { vm.showTerms = false }) { Text("Accept", color = PruRed) } }
            )
        }
    }
}

@Composable
private fun MemberChip(label: String, type: MemberType, vm: BuyOnlineViewModel) {
    ChipSelector(
        label    = label,
        selected = type in vm.selectedMembers,
        onClick  = {
            vm.selectedMembers = if (type in vm.selectedMembers)
                vm.selectedMembers - type else vm.selectedMembers + type
        }
    )
}

@Composable
private fun FeaturePill(icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Text(label, fontSize = 11.sp, color = PruSubtext, textAlign = TextAlign.Center)
    }
}
