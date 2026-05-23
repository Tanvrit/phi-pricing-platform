package com.rate.aegis.customer.buyonline.ui.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.aegis.i18n.t

@Composable
fun BankDetailsScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, progress = 3, currentStep = "KYC")

        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t("bank.title"), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(t("bank.subtitle"),
                fontSize = 13.sp, color = PruSubtext)

            OutlinedTextField(value = vm.bankDetails.accountNumber,
                onValueChange = { vm.bankDetails = vm.bankDetails.copy(accountNumber = it) },
                label = { Text("Account number") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = vm.bankDetails.bankName,
                onValueChange = { vm.bankDetails = vm.bankDetails.copy(bankName = it) },
                label = { Text("Bank name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = vm.bankDetails.ifscCode,
                onValueChange = { vm.bankDetails = vm.bankDetails.copy(ifscCode = it.uppercase()) },
                label = { Text("IFSC code") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.weight(1f))
        }

        Surface(shadowElevation = 8.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                PRUButton(t("bank.cta"), { vm.submitBankDetails() },
                    enabled = vm.bankDetails.accountNumber.isNotEmpty() &&
                              vm.bankDetails.bankName.isNotEmpty() &&
                              vm.bankDetails.ifscCode.length >= 11 && !vm.loading)
            }
        }
    }
}
