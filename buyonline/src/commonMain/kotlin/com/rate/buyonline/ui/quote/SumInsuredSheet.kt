package com.rate.buyonline.ui.quote

import com.rate.domain.money.formatRupees

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun SumInsuredSheet(vm: BuyOnlineViewModel, onDone: () -> Unit) {
    val siLabels = mapOf(1_000_000L to "₹10 lakh", 2_500_000L to "₹25 lakh",
        5_000_000L to "₹50 lakh", 10_000_000L to "₹1 crore")
    val popular = 5_000_000L

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Text("PRUHealth / ${vm.selectedTier.displayName}", fontSize = 12.sp, color = PruSubtext)
        InfoBanner("Why buy high sum insured?",
            "A slightly higher premium means better financial security through enhanced benefits and wider coverage.")
        Text("Select sum insured", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        vm.sumInsuredOptions.forEach { si ->
            val selected = si == vm.selectedSumInsured
            // Only the currently-selected SI has an engine-priced premium readily available.
            // Other options show "Tap to view"; switching SI triggers a fresh engine call.
            val annual   = if (selected) vm.totalAnnualWithGst else 0.0
            val monthly  = annual / 12
            Row(
                Modifier.fillMaxWidth().clickable { vm.selectedSumInsured = si }
                    .background(if (selected) PruRed.copy(0.05f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { vm.selectedSumInsured = si },
                    colors = RadioButtonDefaults.colors(selectedColor = PruRed))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(siLabels[si] ?: "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (si == popular) PopularBadge()
                    }
                    if (selected) {
                        Text(
                            if (annual > 0) "${formatRupees(annual)} per year | Monthly ${formatRupees(monthly)}"
                            else "Calculating…",
                            fontSize = 12.sp, color = PruSubtext
                        )
                    } else {
                        Text("Tap to view premium", fontSize = 12.sp, color = PruSubtext)
                    }
                }
            }
            if (si != vm.sumInsuredOptions.last()) HorizontalDivider()
        }

        Spacer(Modifier.height(8.dp))
        PRUButton("Done", onDone)
    }
}
