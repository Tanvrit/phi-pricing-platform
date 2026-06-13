package com.rate.sdk.ui.buyonline.screens.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.kit.brand.*
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.kit.i18n.t

@Composable
fun PlanLoadingScreen(vm: BuyOnlineViewModel) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {

            Box(
                Modifier.size(120.dp).scale(scale).background(PruRed.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(90.dp).background(PruRed.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🛡️", fontSize = 40.sp) }
            }

            Text(t("loading.title"), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Text(t("loading.subtitle"), fontSize = 14.sp, color = PruSubtext, textAlign = TextAlign.Center)

            CircularProgressIndicator(color = PruRed, strokeWidth = 3.dp)
        }
    }
}
