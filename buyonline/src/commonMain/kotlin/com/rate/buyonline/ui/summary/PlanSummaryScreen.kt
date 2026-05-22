package com.rate.buyonline.ui.summary

import com.rate.domain.money.formatRupees

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
import com.rate.buyonline.navigation.BuyOnlineScreen
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun PlanSummaryScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, progress = 1, currentStep = "Quote")
        MemberSummaryBar("Immediate family")

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Here is your plan summary", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Plan details", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { vm.navigate(BuyOnlineScreen.Quote) }) {
                            Text("Edit", color = PruRed)
                        }
                    }
                    HorizontalDivider()
                    SummaryRow("Plan",           "PRUHealth ${vm.selectedTier.displayName}")
                    SummaryRow("Sum Insured",    vm.formatSI(vm.selectedSumInsured))
                    SummaryRow("Policy Duration","${vm.selectedTenure} year${if (vm.selectedTenure > 1) "s" else ""}")
                    SummaryRow("Members Covered",(vm.coveredMembers.ifEmpty { vm.allMembers }).joinToString(", "))
                    val addOnNames = vm.availableAddOns.filter { it.id in vm.selectedAddOnIds }.map { it.name }
                    if (addOnNames.isNotEmpty()) SummaryRow("Add-ons", addOnNames.joinToString(", "))
                    HorizontalDivider()
                    // Full premium ledger — replaces the prior "+ GST" placeholder line.
                    // Every row breaks the headline figure into a defensible component so
                    // customer + auditor can reconcile the total.
                    val basePremium  = vm.lastPremium?.basePremium ?: 0.0
                    val addons       = vm.lastPremium?.totalAddons ?: 0.0
                    val discount     = vm.lastPremium?.totalDiscountAmount ?: 0.0
                    val preTax       = vm.totalAnnualPreTax
                    val gst          = vm.gstAmount
                    val grandTotal   = vm.totalAnnualWithGst

                    SummaryRow("Base premium",   formatRupees(basePremium, 2))
                    if (addons != 0.0)   SummaryRow("Add-ons",        formatRupees(addons, 2))
                    if (discount != 0.0) SummaryRow("Discount",       "-${formatRupees(-discount, 2)}")
                    SummaryRow("Sub-total",      formatRupees(preTax, 2))
                    SummaryRow("GST (18%)",      formatRupees(gst, 2))
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Premium per month", fontWeight = FontWeight.Medium)
                        Text(formatRupees(grandTotal / 12), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total per year (incl. GST)", fontWeight = FontWeight.SemiBold)
                        Text(formatRupees(grandTotal), fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp, color = PruRed)
                    }
                    if (vm.premiumLoading) {
                        Text("Calculating latest premium…", fontSize = 11.sp, color = PruSubtext)
                    }
                    vm.premiumError?.let { Text(it, fontSize = 12.sp, color = PruRed) }
                }
            }

            Text("Premiums are indicative. Final premiums may vary based on underwriting.",
                fontSize = 11.sp, color = PruSubtext)
            Text(
                "Health insurance attracts 18% GST under HSN 9971. " +
                "Section 80D may make a portion of the premium tax-deductible — consult your tax advisor.",
                fontSize = 11.sp, color = PruSubtext
            )
            Spacer(Modifier.height(4.dp))
            // IRDAI-mandated regulatory footer: registration number, free-look period,
            // grievance redressal, ombudsman, UIN, GST disclosure.
            IrdaiComplianceFooter()
        }

        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${formatRupees(vm.totalAnnualWithGst)} /yr", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("incl. 18% GST", fontSize = 11.sp, color = PruSubtext)
                    }
                    Text("Price details ↑", color = PruRed, fontSize = 13.sp)
                }
                PRUButton("Proceed with payment", { vm.proceedFromSummary() })
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = PruSubtext)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PruText)
    }
}
