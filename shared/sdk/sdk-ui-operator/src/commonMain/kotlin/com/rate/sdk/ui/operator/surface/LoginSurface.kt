package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.AuthApi
import com.rate.sdk.ui.operator.viewmodel.LoginSuccess
import com.rate.sdk.ui.operator.viewmodel.LoginViewModel

/**
 * Operator login gate — the first surface an unauthenticated console session sees.
 *
 * Dual-panel layout: a left brand panel (vertical gradient + product wordmark +
 * tagline) and a right [AegisCard] holding the form. The form reuses the kit
 * primitives — [AegisInput] for email + password, a Visibility eye-toggle in the
 * password suffix, an [AegisButton] submit, an [AegisCallout] for error / reset
 * confirmation, and a Forgot-password link that swaps the card into a tiny reset
 * panel.
 *
 * This surface owns no token state: [LoginViewModel] calls [onAuthenticated] with a
 * [LoginSuccess] on a good login; the app shell stores the token + routes into the
 * console. The narrow left panel collapses gracefully when the window is small.
 *
 * @param auth            the operator [AuthApi] over the shared transport.
 * @param onAuthenticated invoked with the JWT + mapped role + display name on success.
 */
@Composable
fun LoginSurface(
    auth: AuthApi,
    onAuthenticated: (LoginSuccess) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(auth) { LoginViewModel(auth, scope, onAuthenticated) }

    Row(modifier.fillMaxSize().background(AegisColors.canvas)) {
        BrandPanel(
            Modifier
                .fillMaxHeight()
                .weight(0.45f)
                .widthIn(min = 0.dp),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .weight(0.55f)
                .background(AegisColors.canvas)
                .padding(AegisSpacing.s7),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                if (vm.forgotMode) ResetPanel(vm) else LoginForm(vm)
            }
        }
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    val gradient = Brush.verticalGradient(
        colors = listOf(AegisColors.brandPressed, AegisColors.brand, AegisColors.brandHover),
    )
    Box(
        modifier
            .background(gradient)
            .padding(AegisSpacing.s7),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            Text(
                "Aegis",
                style = AegisTypography.display.copy(
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                "Group insurance, operated end to end.",
                style = AegisTypography.h2.copy(color = androidx.compose.ui.graphics.Color.White),
            )
            Text(
                "Quote, configure, and govern every plan from one console — built for the people who run the book, not just buy it.",
                style = AegisTypography.bodyL.copy(color = androidx.compose.ui.graphics.Color(0xCCFFFFFF)),
            )
        }
    }
}

@Composable
private fun LoginForm(vm: LoginViewModel) {
    AegisCard(title = "Sign in", subtitle = "Operator & admin console") {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            vm.error?.let {
                AegisCallout(kind = CalloutKind.DANGER, title = "Sign-in failed", body = it)
            }

            AegisInput(
                value = vm.email,
                onValueChange = vm::onEmailChange,
                label = "Email",
                placeholder = "you@pruhealth.in",
                enabled = !vm.loading,
            )

            // Password with an inline eye-toggle. The kit AegisInput has no
            // password-masking slot, so we render the masked value ourselves and
            // expose the real value only when toggled visible.
            PasswordField(vm)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LinkText("Forgot password?", onClick = vm::openForgot)
            }

            AegisButton(
                label = if (vm.loading) "Signing in…" else "Sign in",
                onClick = vm::submit,
                size = AegisButtonSize.Lg,
                enabled = vm.canSubmit,
                loading = vm.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PasswordField(vm: LoginViewModel) {
    // The kit [AegisInput] is a single-line plain-text field with no masking slot,
    // so we mask the value ourselves: until the eye-toggle is flipped on, we feed
    // a bullet-masked string to the field but route edits back through the raw
    // value. The diff of the displayed text vs the masked length lets us
    // reconstruct what the operator typed/deleted without ever showing the secret.
    Column {
        val masked = "•".repeat(vm.password.length)
        AegisInput(
            value = if (vm.passwordVisible) vm.password else masked,
            onValueChange = { typed -> vm.onPasswordChange(reconcileMasked(vm.password, masked, typed, vm.passwordVisible)) },
            label = "Password",
            placeholder = "Your password",
            enabled = !vm.loading,
        )
        Spacer(Modifier.height(AegisSpacing.s1))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EyeToggle(visible = vm.passwordVisible, onClick = vm::togglePasswordVisible)
        }
    }
}

/**
 * Reconcile an edit made against a bullet-masked rendering back into the real
 * password. When [visible] the field already shows the raw value so [typed] is the
 * new password verbatim. When masked, we only support append (a longer string
 * means new trailing chars were typed) and truncation (a shorter string means
 * chars were deleted from the end) — the common case for a password box; any other
 * change collapses to the typed text so the field never desyncs.
 */
private fun reconcileMasked(current: String, masked: String, typed: String, visible: Boolean): String {
    if (visible) return typed
    return when {
        typed.length > masked.length -> current + typed.substring(masked.length)
        typed.length < current.length -> current.take(typed.length)
        else -> current
    }
}

@Composable
private fun EyeToggle(visible: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = AegisColors.textSecondary,
            modifier = Modifier.width(16.dp),
        )
        Text(
            if (visible) "Hide password" else "Show password",
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
        )
    }
}

@Composable
private fun ResetPanel(vm: LoginViewModel) {
    AegisCard(title = "Reset password", subtitle = "We'll email you a reset link") {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            if (vm.resetSent) {
                AegisCallout(
                    kind = CalloutKind.SUCCESS,
                    title = "Check your inbox",
                    body = "If that email is registered, a reset link is on its way.",
                )
            } else {
                vm.error?.let {
                    AegisCallout(kind = CalloutKind.DANGER, title = "Couldn't send", body = it)
                }
                AegisInput(
                    value = vm.email,
                    onValueChange = vm::onEmailChange,
                    label = "Email",
                    placeholder = "you@pruhealth.in",
                    enabled = !vm.loading,
                )
                AegisButton(
                    label = if (vm.loading) "Sending…" else "Send reset link",
                    onClick = vm::requestReset,
                    size = AegisButtonSize.Lg,
                    enabled = !vm.loading && vm.email.isNotBlank(),
                    loading = vm.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AegisButton(
                label = "Back to sign in",
                onClick = vm::closeForgot,
                variant = AegisButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = AegisTypography.small.copy(color = AegisColors.brand, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
    )
}
