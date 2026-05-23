package com.rate.aegis.customer.buyonline.ui.details

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
import com.rate.aegis.customer.buyonline.model.PersonalDetail
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.aegis.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreen(vm: BuyOnlineViewModel) {
    val memberIds = vm.allMembers
    var selectedMemberIndex by remember { mutableStateOf(0) }
    val selectedMemberId = memberIds.getOrElse(selectedMemberIndex) { "Myself" }

    Column(Modifier.fillMaxSize().background(PruBackground)) {
        PRUTopBar(onBack = { vm.navigateBack() }, onSaveExit = {}, progress = 1, currentStep = "Quote")

        ScrollableTabRow(selectedTabIndex = selectedMemberIndex, containerColor = Color.White,
            contentColor = PruRed, edgePadding = 0.dp) {
            memberIds.forEachIndexed { index, member ->
                Tab(selected = selectedMemberIndex == index, onClick = { selectedMemberIndex = index },
                    text = { Text(member, fontSize = 13.sp) })
            }
        }

        val detail = vm.getPersonalDetail(selectedMemberId)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(t("personal.helper"),
                fontSize = 12.sp, color = PruSubtext)

            var titleExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(titleExpanded, { titleExpanded = it }) {
                OutlinedTextField(
                    value = detail.title, onValueChange = {}, readOnly = true,
                    label = { Text("Title") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(titleExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(titleExpanded, { titleExpanded = false }) {
                    listOf("Mr", "Mrs", "Ms", "Dr").forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = {
                            vm.updatePersonalDetail(selectedMemberId, detail.copy(title = t)); titleExpanded = false
                        })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = detail.firstName,
                    onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(firstName = it)) },
                    label = { Text("First name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = detail.lastName,
                    onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(lastName = it)) },
                    label = { Text("Last name") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            OutlinedTextField(value = detail.dob,
                onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(dob = it)) },
                label = { Text("Date of birth (DD/MM/YYYY)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = detail.mobile,
                onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) vm.updatePersonalDetail(selectedMemberId, detail.copy(mobile = it)) },
                label = { Text("Mobile number") }, prefix = { Text("+91 ") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = detail.email,
                onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(email = it)) },
                label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = detail.heightFt,
                    onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(heightFt = it)) },
                    label = { Text("Height (ft)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = detail.heightIn,
                    onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(heightIn = it)) },
                    label = { Text("(in)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = detail.weightKg,
                    onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(weightKg = it)) },
                    label = { Text("Weight (kg)") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            OutlinedTextField(value = detail.panNumber,
                onValueChange = { vm.updatePersonalDetail(selectedMemberId, detail.copy(panNumber = it.uppercase())) },
                label = { Text("PAN number") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            TextButton(onClick = { vm.nomineeAsSelf = !vm.nomineeAsSelf }) {
                Text("+ Add nominee →", color = PruRed)
            }
        }

        Surface(shadowElevation = 8.dp) {
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                if (selectedMemberIndex < memberIds.size - 1) {
                    PRUButton(t("personal.cta.next"), { selectedMemberIndex++ })
                } else {
                    PRUButton(t("personal.cta.proceed"), { vm.proceedFromPersonalDetails() })
                }
            }
        }
    }
}
