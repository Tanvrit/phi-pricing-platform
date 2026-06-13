package com.rate.sdk.ui.buyonline.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.buyonline.navigation.BuyOnlineScreen
import com.rate.sdk.ui.kit.brand.PruBackground
import com.rate.sdk.ui.kit.brand.PruRed
import com.rate.sdk.ui.kit.brand.PruSubtext
import com.rate.sdk.ui.kit.brand.PruText

/**
 * Five-stage progress indicator for the buyonline journey. Maps the 22 screens
 * into 5 user-facing phases (Profile → Plan → Health → Payment → KYC) so the
 * customer always sees where they are in the flow at a glance.
 *
 * Past stages render in a pale red wash (signals "completed"), the active
 * stage in full PruRed, future stages in neutral gray. A 2dp connector line
 * between chips picks up the same past/future palette to imply progression.
 *
 * Intentionally non-interactive: tapping a past stage would require unwinding
 * VM state safely, which is a much larger change than a visual indicator.
 */
@Composable
fun StepIndicator(
    currentScreen: BuyOnlineScreen,
    modifier: Modifier = Modifier
) {
    val current = stageFor(currentScreen)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (stage in 1..STAGE_LABELS.size) {
                StageChip(
                    index = stage,
                    label = STAGE_LABELS[stage - 1],
                    state = stateFor(stage, current)
                )
                if (stage < STAGE_LABELS.size) {
                    // Connector line — colored by whether the *next* boundary
                    // has been crossed. Past boundaries get the pale red wash
                    // so the eye reads a single continuous "filled" track up
                    // to the active chip; future boundaries stay neutral.
                    val crossed = stage < current
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (crossed) PALE_RED else CONNECTOR_GRAY
                            )
                    )
                }
            }
        }
    }
}

private enum class ChipState { Past, Active, Future }

@Composable
private fun StageChip(index: Int, label: String, state: ChipState) {
    val bg = when (state) {
        ChipState.Active -> PruRed
        ChipState.Past   -> PALE_RED
        ChipState.Future -> PruBackground
    }
    val fg = when (state) {
        ChipState.Active -> Color.White
        ChipState.Past   -> PruRed
        ChipState.Future -> PruSubtext
    }
    val weight = when (state) {
        ChipState.Active -> FontWeight.Bold
        ChipState.Past   -> FontWeight.SemiBold
        ChipState.Future -> FontWeight.Normal
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Numbered circle — gives the chip a tactile "step N of 5" feel and
        // keeps the row legible even on the narrowest mobile widths where the
        // label might be clipped.
        Box(
            Modifier
                .size(20.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .background(
                    color = if (state == ChipState.Future) Color.Transparent else bg,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                color = if (state == ChipState.Future) PruSubtext
                        else if (state == ChipState.Active) Color.White
                        else PruText,
                fontSize = 11.sp,
                fontWeight = weight
            )
        }
    }
}

private val STAGE_LABELS = listOf("Profile", "Plan", "Health", "Payment", "KYC")
private val PALE_RED      = Color(0xFFFFD6DC)   // tinted PruRed for past/connector
private val CONNECTOR_GRAY = Color(0xFFE0E0E0)

private fun stateFor(stage: Int, current: Int): ChipState = when {
    stage  < current -> ChipState.Past
    stage == current -> ChipState.Active
    else             -> ChipState.Future
}

/**
 * Maps each of the 22 [BuyOnlineScreen] subtypes to one of 5 phases.
 * Using the exhaustive `when` on the sealed class so the compiler will flag
 * any future screens that get added without an explicit mapping.
 */
private fun stageFor(screen: BuyOnlineScreen): Int = when (screen) {
    BuyOnlineScreen.Landing,
    BuyOnlineScreen.Otp,
    BuyOnlineScreen.GetStarted            -> 1
    BuyOnlineScreen.PreExistingDisease,
    BuyOnlineScreen.CriticalIllness,
    BuyOnlineScreen.PlanLoading,
    BuyOnlineScreen.Eligibility,
    BuyOnlineScreen.Quote,
    BuyOnlineScreen.AddOns,
    BuyOnlineScreen.PlanSummary           -> 2
    BuyOnlineScreen.PersonalDetails,
    BuyOnlineScreen.LifestyleQuestions,
    BuyOnlineScreen.MedicalQuestions      -> 3
    BuyOnlineScreen.Payment,
    BuyOnlineScreen.PaymentSuccess        -> 4
    BuyOnlineScreen.KycMethod,
    BuyOnlineScreen.KycDetails,
    BuyOnlineScreen.KycOtp,
    BuyOnlineScreen.BankDetails,
    BuyOnlineScreen.KycSubmitted,
    BuyOnlineScreen.ApplicationComplete,
    BuyOnlineScreen.Satisfaction          -> 5
}
