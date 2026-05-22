package com.rate.aegis.customer.buyonline.ui.quote

import com.rate.domain.money.formatRupees

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.model.PlanTier
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(vm: BuyOnlineViewModel) {
    Box(Modifier.fillMaxSize().background(PruBackground)) {
        Column(Modifier.fillMaxSize()) {
            PRUTopBar(onBack = { vm.navigateBack() }, progress = 1, currentStep = "Quote")
            MemberSummaryBar(if (vm.kidsCount > 0) "1 Adult | ${vm.kidsCount} Children" else "1 Adult")

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan tier tabs
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlanTier.values().forEach { tier ->
                        val selected = tier == vm.selectedTier
                        Box(
                            Modifier.weight(1f)
                                .background(if (selected) PruRed else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { vm.onTierChanged(tier) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tier.displayName,
                                color = if (selected) Color.White else PruSubtext,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp)
                        }
                    }
                }

                // Sum insured selector
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().clickable { vm.showSISheet = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sum insured", fontSize = 12.sp, color = PruSubtext)
                            Text(vm.formatSI(vm.selectedSumInsured), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("▾", color = PruRed, fontSize = 18.sp)
                    }
                }

                // Tenure selector
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().clickable { vm.showTenureSheet = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tenure", fontSize = 12.sp, color = PruSubtext)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${vm.selectedTenure} year${if (vm.selectedTenure > 1) "s" else ""}",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                val disc = vm.tenureDiscounts[vm.selectedTenure] ?: 0.0
                                if (disc > 0) {
                                    Box(Modifier.background(PruSuccess.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("${(disc * 100).toInt()}% off", fontSize = 11.sp,
                                            color = PruSuccess, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        Text("▾", color = PruRed, fontSize = 18.sp)
                    }
                }

                // Plan detail card — uses the real engine's totals (with GST).
                // Falls back to 0 while the first calculation is in-flight.
                val annualPreTax = vm.totalAnnualPreTax
                val gst          = vm.gstAmount
                val annualTotal  = vm.totalAnnualWithGst
                val monthly      = annualTotal / 12.0
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, PruRed),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("PRUHealth", fontSize = 11.sp, color = PruSubtext)
                                Text(vm.selectedTier.displayName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            }
                            Box(Modifier.background(PruRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatRupees(monthly), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                            Text("/month", fontSize = 13.sp, color = PruSubtext, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        // Full GST-inclusive premium ledger — replaces the "+ 0% GST" placeholder.
                        Text("${formatRupees(annualPreTax)} base + ${formatRupees(gst)} GST (18%) = ${formatRupees(annualTotal)}/year",
                            fontSize = 13.sp, color = PruSubtext)
                        if (vm.premiumLoading) {
                            Text("Calculating…", fontSize = 11.sp, color = PruSubtext)
                        }
                        vm.premiumError?.let { err ->
                            Text(err, fontSize = 12.sp, color = PruRed)
                        }
                        HorizontalDivider()
                        listOf("100% restoration from 2nd claim", "Loyalty addition benefit", "Customisable plan").forEach {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓", color = PruSuccess, fontWeight = FontWeight.Bold)
                                Text(it, fontSize = 13.sp)
                            }
                        }
                        Text("Learn More", color = PruRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            StickyPriceBar(annualPremium = vm.totalAnnualWithGst, onProceed = { vm.proceedFromQuote() })
        }

        if (vm.showSISheet) {
            ModalBottomSheet(onDismissRequest = { vm.showSISheet = false }) {
                SumInsuredSheet(vm) { vm.showSISheet = false }
            }
        }
        if (vm.showTenureSheet) {
            ModalBottomSheet(onDismissRequest = { vm.showTenureSheet = false }) {
                TenureSheet(vm) { vm.showTenureSheet = false }
            }
        }
    }
}
