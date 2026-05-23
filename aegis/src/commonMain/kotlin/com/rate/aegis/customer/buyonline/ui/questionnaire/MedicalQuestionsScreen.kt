package com.rate.aegis.customer.buyonline.ui.questionnaire

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
import com.rate.aegis.customer.buyonline.model.MEDICAL_QUESTIONS
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.aegis.i18n.t

@Composable
fun MedicalQuestionsScreen(vm: BuyOnlineViewModel) {
    val members = vm.allMembers

    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 4, currentStep = "Health")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(t("medical.title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(t("medical.subtitle"),
                fontSize = 13.sp, color = PruSubtext)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Question", Modifier.weight(4f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        members.forEach { m ->
                            Text(m.take(6), Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp, color = PruSubtext)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    MEDICAL_QUESTIONS.forEach { question ->
                        val answeredMembers = vm.medicalAnswers[question.id] ?: emptySet()
                        val anyYes = answeredMembers.isNotEmpty()

                        Column {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Text(question.text, Modifier.weight(4f), fontSize = 12.sp)
                                members.forEach { member ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Checkbox(
                                            checked = member in answeredMembers,
                                            onCheckedChange = { vm.toggleMedicalMember(question.id, member, it) },
                                            colors = CheckboxDefaults.colors(checkedColor = PruRed),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            if (anyYes && question.requiresDetail) {
                                Card(
                                    Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = vm.medicalDetails[question.id] ?: "",
                                            onValueChange = { vm.updateMedicalDetail(question.id, it) },
                                            label = { Text(if (question.id == "hosp") "Reason / Diagnosis" else "Please describe") },
                                            modifier = Modifier.fillMaxWidth(), maxLines = 3
                                        )
                                        if (question.id == "hosp") {
                                            Card(
                                                Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = BorderStroke(1.dp, Color(0xFFBDBDBD))
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(16.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text("⬆", fontSize = 24.sp, color = PruSubtext)
                                                    Text("Drop discharge summary or click to browse",
                                                        fontSize = 12.sp, color = PruSubtext)
                                                    Text("Supports PDF, JPG & PNG — max 10MB",
                                                        fontSize = 11.sp, color = Color(0xFFBDBDBD))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                        }
                    }
                }
            }
        }

        Surface(shadowElevation = 4.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                PRUButton(t("medical.cta"), { vm.proceedFromMedical() })
            }
        }
    }
}
