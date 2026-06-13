package com.rate.sdk.ui.buyonline.screens.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.rate.sdk.ui.buyonline.model.KycMethod
import com.rate.sdk.ui.buyonline.screens.components.*
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.kit.i18n.t

@Composable
fun KycMethodScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 3, currentStep = "KYC")

        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t("kyc.method.title"),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            KycMethod.values().forEach { method ->
                val selected = method == vm.selectedKycMethod
                Card(
                    Modifier.fillMaxWidth().clickable { vm.selectedKycMethod = method }
                        .then(if (selected) Modifier.border(2.dp, PruRed, RoundedCornerShape(12.dp)) else Modifier),
                    colors = CardDefaults.cardColors(containerColor = if (selected) PruRed.copy(0.05f) else Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected, onClick = { vm.selectedKycMethod = method },
                            colors = RadioButtonDefaults.colors(selectedColor = PruRed))
                        Column(Modifier.weight(1f)) {
                            Text(method.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(method.description, fontSize = 12.sp, color = PruSubtext)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            PRUButton(t("kyc.method.cta"), { vm.proceedFromKycMethod() })
        }
    }
}
