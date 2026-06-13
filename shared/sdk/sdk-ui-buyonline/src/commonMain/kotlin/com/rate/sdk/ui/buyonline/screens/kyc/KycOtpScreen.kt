package com.rate.sdk.ui.buyonline.screens.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.buyonline.screens.components.*
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.kit.i18n.t

@Composable
fun KycOtpScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, progress = 3, currentStep = "KYC")

        Column(Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(32.dp))
            Text(t("kyc.otp.title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Please enter the OTP sent to your registered mobile number XXXXXX${vm.kycAadhaar.takeLast(4)}",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            // 6-digit OTP
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val value = vm.kycOtpDigits.getOrElse(index) { "" }
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.length <= 1 && it.all(Char::isDigit)) vm.updateKycOtpDigit(index, it) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = PruText),
                        decorationBox = { inner ->
                            Box(
                                Modifier.size(48.dp)
                                    .background(if (value.isNotEmpty()) PruRed.copy(0.05f) else Color.White, RoundedCornerShape(8.dp))
                                    .border(2.dp, if (value.isNotEmpty()) PruRed else Color(0xFFBDBDBD), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { inner() }
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.kycOtpTimerFormatted, color = PruRed, fontWeight = FontWeight.SemiBold)
                Text("|", color = PruSubtext)
                TextButton(onClick = { vm.startKycOtpTimer() }, enabled = vm.kycOtpTimer == 0) {
                    Text(t("kyc.otp.resend"), color = if (vm.kycOtpTimer == 0) PruRed else PruSubtext)
                }
            }

            Spacer(Modifier.weight(1f))
            PRUButton(t("kyc.otp.cta"), { vm.verifyKycOtp() }, enabled = vm.kycOtpFilled && !vm.loading)
        }
    }
}
