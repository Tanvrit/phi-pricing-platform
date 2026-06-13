package com.rate.sdk.ui.buyonline.screens.addons

import com.rate.core.money.formatRupees

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.buyonline.model.AddOn
import com.rate.sdk.ui.buyonline.model.PlanTier
import com.rate.sdk.ui.buyonline.screens.components.*
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.kit.i18n.t

@Composable
fun AddOnsScreen(vm: BuyOnlineViewModel) {
    Box(Modifier.fillMaxSize().background(PruBackground)) {
        Column(Modifier.fillMaxSize()) {
            PRUTopBar(onBack = { vm.navigateBack() }, progress = 1, currentStep = "Quote")
            MemberSummaryBar(if (vm.kidsCount > 0) "2 Adults | ${vm.kidsCount} Children" else "1 Adult")

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(t("addons.title"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape  = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Recommended bundle for You", fontWeight = FontWeight.SemiBold)
                                Text("+${formatRupees(vm.totalAddOnCost)}/year", fontSize = 13.sp, color = PruRed)
                            }
                            if (vm.selectedTier != PlanTier.PREMIER) {
                                TextButton(onClick = { vm.editingAddOns = !vm.editingAddOns }) {
                                    Text(if (vm.editingAddOns) "Done" else "Edit",
                                        color = PruRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        HorizontalDivider()
                        vm.availableAddOns.forEach { addOn -> AddOnRow(addOn, vm) }
                    }
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total add-on cost", fontSize = 14.sp, color = PruSubtext)
                            Text(formatRupees(vm.totalAddOnCost), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total premium (plan + add-ons)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(formatRupees(vm.totalPremium), fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, color = PruText)
                        }
                    }
                }
            }

            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { vm.showSkipAddOnConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text(t("addons.skip"), color = PruSubtext)
                    }
                    Button(onClick = { vm.proceedFromAddOns() },
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PruRed)) {
                        Text(t("addons.cta"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (vm.showSkipAddOnConfirm) {
        AlertDialog(
            onDismissRequest = { vm.showSkipAddOnConfirm = false },
            title = { Text("Continue without add-ons?") },
            text  = { Text("Add-ons enhance your coverage. Are you sure you want to proceed without them?", fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = { vm.showSkipAddOnConfirm = false; vm.selectedAddOnIds = emptySet(); vm.proceedFromAddOns() },
                    colors = ButtonDefaults.buttonColors(containerColor = PruRed)) { Text("Yes, proceed") }
            },
            dismissButton = { TextButton(onClick = { vm.showSkipAddOnConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddOnRow(addOn: AddOn, vm: BuyOnlineViewModel) {
    val selected = addOn.id in vm.selectedAddOnIds
    val editable = vm.editingAddOns || vm.selectedTier == PlanTier.PREMIER
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(
            checked  = selected,
            onCheckedChange = if (editable) {{ vm.selectedAddOnIds = if (it) vm.selectedAddOnIds + addOn.id else vm.selectedAddOnIds - addOn.id }} else null,
            colors   = CheckboxDefaults.colors(checkedColor = PruRed),
            enabled  = editable
        )
        Column(Modifier.weight(1f)) {
            Text(addOn.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(addOn.description, fontSize = 12.sp, color = PruSubtext)
        }
    }
}
