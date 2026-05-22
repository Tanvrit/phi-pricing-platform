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

private val CHRONIC_CONDITIONS = listOf(
    "Cancer", "Diabetes", "Heart Disease", "Hypertension", "Stroke",
    "Asthma", "COPD", "Kidney Failure", "Liver Disease", "Thyroid Disorder",
    "Epilepsy", "Arthritis", "Depression"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CriticalIllnessScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() })
        MemberSummaryBar(if (vm.kidsCount > 0) "2 Adults | ${vm.kidsCount} Children" else "2 Adults")

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Have any member(s) been treated for critical conditions?",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                TextButton(onClick = { vm.showCriticalInfoModal = true }) {
                    Text("View 13 covered conditions →", color = PruRed, fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(true to "Yes", false to "No").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RadioButton(
                                selected = vm.hasCriticalIllness == value,
                                onClick  = { vm.hasCriticalIllness = value },
                                colors   = RadioButtonDefaults.colors(selectedColor = PruRed)
                            )
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }

                if (vm.hasCriticalIllness) {
                    Text("Please select applicable members:", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.extendedMembers.forEach { member ->
                            ChipSelector(
                                label    = member,
                                selected = member in vm.criticalIllnessMembers,
                                onClick  = {
                                    vm.criticalIllnessMembers =
                                        if (member in vm.criticalIllnessMembers)
                                            vm.criticalIllnessMembers - member
                                        else vm.criticalIllnessMembers + member
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PRUButton("Proceed", { vm.proceedFromCriticalIllness() })
            }

            Card(Modifier.width(200.dp).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = PruInfoBg)) {
                Column(Modifier.padding(12.dp)) {
                    // Copy-paste bug pre-Foundation-Pack: this card lived in the Critical Illness
                    // screen but used "Pre-existing diseases" copy. Fixed to match the screen.
                    Text("Why declare critical illnesses?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Honest disclosure lets us price your policy accurately and ensures " +
                            "your claim isn't rejected later for non-disclosure.",
                        fontSize = 12.sp, color = PruSubtext)
                }
            }
        }
    }

    if (vm.showCriticalInfoModal) {
        AlertDialog(
            onDismissRequest = { vm.showCriticalInfoModal = false },
            title = { Text("What qualifies as critical illness?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val half = CHRONIC_CONDITIONS.size / 2
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CHRONIC_CONDITIONS.take(half).forEach { Text("• $it", fontSize = 13.sp) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CHRONIC_CONDITIONS.drop(half).forEach { Text("• $it", fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Note: If you have an unlisted condition requiring ongoing treatment, select 'Yes'.",
                        fontSize = 11.sp, color = PruSubtext)
                }
            },
            confirmButton = { TextButton(onClick = { vm.showCriticalInfoModal = false }) { Text("Close", color = PruRed) } }
        )
    }
}
