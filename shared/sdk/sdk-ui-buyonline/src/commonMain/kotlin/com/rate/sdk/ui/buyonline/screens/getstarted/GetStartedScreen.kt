package com.rate.sdk.ui.buyonline.screens.getstarted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.buyonline.screens.components.*
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.kit.i18n.t

@Composable
fun GetStartedScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {})
        MemberSummaryBar(if (vm.kidsCount > 0) "2 Adults | ${vm.kidsCount} Children" else "2 Adults")

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(t("getstarted.title"), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = vm.pincode,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        vm.pincode = it
                        if (it.length == 6) vm.submitPincode()
                    }
                },
                label = { Text(t("getstarted.pincode")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            if (vm.hospitalsNearby > 0) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PruSuccess.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏥", fontSize = 18.sp)
                        Text("${vm.hospitalsNearby} PRU network hospitals found near you",
                            color = PruSuccess, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            PRUButton(t("getstarted.cta"), { vm.proceedFromGetStarted() }, enabled = vm.pincode.length == 6)
        }
    }
}
