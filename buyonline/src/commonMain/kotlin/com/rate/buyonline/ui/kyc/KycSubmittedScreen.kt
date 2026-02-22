package com.rate.buyonline.ui.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun KycSubmittedScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        PRUTopBar(onSaveExit = {}, progress = 3, currentStep = "KYC")

        Column(Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.weight(1f))
            Text("🎉", fontSize = 64.sp)
            Text("KYC Submitted", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("To issue your policy, we need your bank details and insured member information. We will be in touch shortly.",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            PRUButton("Proceed", { vm.proceedFromKycSubmitted() })
        }
    }
}
