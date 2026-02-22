package com.rate.buyonline.ui.kyc

import androidx.compose.foundation.BorderStroke
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
import com.rate.buyonline.model.KycMethod
import com.rate.buyonline.ui.components.*
import com.rate.buyonline.ui.theme.*
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun KycDetailsScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 3, currentStep = "KYC")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(when (vm.selectedKycMethod) {
                KycMethod.CKYC   -> "C-KYC details"
                KycMethod.EKYC   -> "E-KYC details"
                KycMethod.MANUAL -> "Manual KYC details"
            }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            when (vm.selectedKycMethod) {
                KycMethod.CKYC   -> CkycForm(vm)
                KycMethod.EKYC   -> EkycForm(vm)
                KycMethod.MANUAL -> ManualKycForm()
            }
        }

        Surface(shadowElevation = 8.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                PRUButton("Proceed", { vm.proceedFromKycDetails() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CkycForm(vm: BuyOnlineViewModel) {
    var subTab by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = subTab, containerColor = Color.White, contentColor = PruRed) {
            listOf("PAN card", "Aadhaar card", "Manual").forEachIndexed { i, t ->
                Tab(selected = subTab == i, onClick = { subTab = i }) {
                    Text(t, Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(value = vm.kycPanNumber, onValueChange = { vm.kycPanNumber = it.uppercase() },
            label = { Text("PAN number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = vm.kycDob, onValueChange = { vm.kycDob = it },
            label = { Text("Date of birth (DD/MM/YYYY)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EkycForm(vm: BuyOnlineViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = vm.kycAadhaar,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) vm.kycAadhaar = it },
            label = { Text("Aadhaar number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = vm.kycDob, onValueChange = { vm.kycDob = it },
            label = { Text("Date of birth (DD/MM/YYYY)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("OR", color = PruSubtext, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(48.dp),
            border = BorderStroke(1.5.dp, PruRed)) {
            Text("📱 Fetch my Aadhaar from DigiLocker", color = PruRed)
        }
    }
}

@Composable
private fun ManualKycForm() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DocumentUploadSection("Identity Proof")
        DocumentUploadSection("Address Proof")
    }
}

@Composable
private fun DocumentUploadSection(title: String) {
    var docType by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = docType, onValueChange = { docType = it },
            label = { Text("Select document type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFBDBDBD)), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⬆", fontSize = 28.sp, color = PruSubtext)
                Text("Drop file here or click to browse", fontSize = 13.sp, color = PruSubtext)
                Text("Supports PDF, JPG & PNG — max 10MB", fontSize = 11.sp, color = Color(0xFFBDBDBD))
            }
        }
    }
}
