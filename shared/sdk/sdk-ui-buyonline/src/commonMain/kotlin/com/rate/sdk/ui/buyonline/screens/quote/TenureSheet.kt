package com.rate.sdk.ui.buyonline.screens.quote

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
import com.rate.sdk.ui.buyonline.screens.components.*
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun TenureSheet(vm: BuyOnlineViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Text("PRUHealth / ${vm.selectedTier.displayName}", fontSize = 12.sp, color = PruSubtext)
        InfoBanner("Why consider higher tenure?",
            "A higher tenure means longer peace of mind, stable coverage and fewer renewal hassles.")
        Text("Select tenure", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        val popular = 3
        vm.tenureOptions.forEach { tenure ->
            val selected = tenure == vm.selectedTenure
            val disc     = vm.tenureDiscounts[tenure] ?: 0.0
            Row(
                Modifier.fillMaxWidth().clickable { vm.selectedTenure = tenure }
                    .background(if (selected) PruRed.copy(0.05f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { vm.selectedTenure = tenure },
                    colors = RadioButtonDefaults.colors(selectedColor = PruRed))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$tenure year${if (tenure > 1) "s" else ""}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (tenure == popular) PopularBadge()
                    }
                    if (disc > 0) Text("Get ${(disc * 100).toInt()}% discount on premium", fontSize = 12.sp, color = PruSuccess)
                }
            }
            if (tenure != vm.tenureOptions.last()) HorizontalDivider()
        }

        Spacer(Modifier.height(8.dp))
        PRUButton("Done", onDone)
    }
}
