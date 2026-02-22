package com.rate.buyonline.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreExistingDiseaseScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() })
        MemberSummaryBar(if (vm.kidsCount > 0) "2 Adults | ${vm.kidsCount} Children" else "2 Adults")

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Does any member(s) have any pre-existing disease? E.g., Diabetes, High cholesterol, Hypertension etc?",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(true to "Yes", false to "No").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RadioButton(
                                selected = vm.hasPED == value,
                                onClick  = { vm.hasPED = value },
                                colors   = RadioButtonDefaults.colors(selectedColor = PruRed)
                            )
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }

                if (vm.hasPED) {
                    Text("Please select applicable members:", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.extendedMembers.forEach { member ->
                            ChipSelector(
                                label    = member,
                                selected = member in vm.pedMembers,
                                onClick  = {
                                    vm.pedMembers = if (member in vm.pedMembers)
                                        vm.pedMembers - member else vm.pedMembers + member
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PRUButton("Proceed", { vm.proceedFromPreExisting() })
            }

            Card(
                Modifier.width(200.dp).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = PruInfoBg)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Why declare pre-existing diseases?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Honest disclosure ensures suitable coverage and avoids issues with future claims.",
                        fontSize = 12.sp, color = PruSubtext)
                }
            }
        }
    }
}
