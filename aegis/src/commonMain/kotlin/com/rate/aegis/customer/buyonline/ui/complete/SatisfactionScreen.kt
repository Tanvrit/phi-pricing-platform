package com.rate.aegis.customer.buyonline.ui.complete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.rate.aegis.customer.buyonline.ui.components.*
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun SatisfactionScreen(vm: BuyOnlineViewModel) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        PRUTopBar()

        Column(Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.weight(1f))
            Text("😊", fontSize = 56.sp)
            Text("How satisfied are you with this journey?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Your feedback helps us improve the experience for everyone.",
                fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(5) { index ->
                    val starIndex = index + 1
                    Text(if (starIndex <= vm.satisfactionRating) "⭐" else "☆",
                        fontSize = 36.sp,
                        modifier = Modifier.clickable { vm.satisfactionRating = starIndex })
                }
            }

            if (vm.satisfactionRating > 0) {
                val labels = listOf("", "Poor", "Fair", "Good", "Very Good", "Excellent")
                Text(labels[vm.satisfactionRating], fontWeight = FontWeight.SemiBold,
                    color = PruRed, fontSize = 16.sp)
            }

            Spacer(Modifier.weight(1f))
            PRUButton("Submit", onClick = {}, enabled = vm.satisfactionRating > 0)
            TextButton(onClick = {}) { Text("Skip", color = PruSubtext) }
        }
    }
}
