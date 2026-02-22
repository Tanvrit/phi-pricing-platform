package com.rate.buyonline.ui.questionnaire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun LifestyleQuestionsScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 4, currentStep = "Health")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Lifestyle questions", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

            val members = vm.allMembers

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Question", Modifier.weight(3f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        members.forEach { m ->
                            Text(m.take(7), Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp, color = PruSubtext)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    LifestyleRow("tobacco products", members, vm.lifestyleAnswers.tobaccoMembers) { mem, checked ->
                        vm.lifestyleAnswers = vm.lifestyleAnswers.copy(
                            tobaccoMembers = if (checked) vm.lifestyleAnswers.tobaccoMembers + mem
                            else vm.lifestyleAnswers.tobaccoMembers - mem)
                    }
                    LifestyleRow("alcohol", members, vm.lifestyleAnswers.alcoholMembers) { mem, checked ->
                        vm.lifestyleAnswers = vm.lifestyleAnswers.copy(
                            alcoholMembers = if (checked) vm.lifestyleAnswers.alcoholMembers + mem
                            else vm.lifestyleAnswers.alcoholMembers - mem)
                    }
                    LifestyleRow("tobacco/smoke", members, vm.lifestyleAnswers.smokesMembers) { mem, checked ->
                        vm.lifestyleAnswers = vm.lifestyleAnswers.copy(
                            smokesMembers = if (checked) vm.lifestyleAnswers.smokesMembers + mem
                            else vm.lifestyleAnswers.smokesMembers - mem)
                    }
                }
            }
        }

        Surface(shadowElevation = 4.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                PRUButton("Proceed", { vm.proceedFromLifestyle() })
            }
        }
    }
}

@Composable
private fun LifestyleRow(
    question: String, members: List<String>,
    selectedMembers: Set<String>, onToggle: (String, Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Do you consume $question?", Modifier.weight(3f), fontSize = 13.sp)
        members.forEach { member ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Checkbox(checked = member in selectedMembers, onCheckedChange = { onToggle(member, it) },
                    colors = CheckboxDefaults.colors(checkedColor = PruRed), modifier = Modifier.size(24.dp))
            }
        }
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}
