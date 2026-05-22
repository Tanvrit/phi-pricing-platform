package com.rate.buyonline.ui.otp

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
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun OtpScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() })

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Box(
                Modifier.size(80.dp).background(PruRed.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🔐", fontSize = 36.sp) }

            Text("Verifying that it's you!", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Text(
                "You will receive an OTP on your mobile number +91 XXXXXX${vm.mobile.takeLast(4)}",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center
            )

            // 4-digit OTP boxes
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    val value = vm.otpDigits.getOrElse(index) { "" }
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.length <= 1 && it.all(Char::isDigit)) vm.updateOtpDigit(index, it) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = PruText),
                        decorationBox = { inner ->
                            Box(
                                Modifier.size(56.dp)
                                    .background(if (value.isNotEmpty()) PruRed.copy(0.05f) else Color.White, RoundedCornerShape(8.dp))
                                    .border(2.dp, if (value.isNotEmpty()) PruRed else Color(0xFFBDBDBD), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { inner() }
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.otpTimerFormatted, color = PruRed, fontWeight = FontWeight.SemiBold)
                Text("|", color = PruSubtext)
                TextButton(onClick = { vm.startOtpTimer() }, enabled = vm.otpTimer == 0) {
                    Text("Resend OTP", color = if (vm.otpTimer == 0) PruRed else PruSubtext)
                }
            }

            // Surface OTP verification errors. Pre-Foundation-Pack the `otpError` state
            // existed in the VM but was never rendered, so users got no feedback on a wrong
            // code. Now displayed in a red banner.
            vm.otpError?.let { msg ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(PruRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, PruRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(msg, color = PruRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.weight(1f))

            PRUButton("Verify", { vm.verifyOtp() }, enabled = vm.otpFilled && !vm.loading)

            // Replaced "99% CLAIM APPROVAL" unsubstantiated claim with IRDAI-compliant
            // trust strip. Claim ratio is only displayed once a real, citable number is
            // wired in via IrdaiRegistrationInfo.claimSettlementRatio.
            IrdaiTrustStrip(modifier = Modifier.fillMaxWidth())
        }
    }
}
