package com.rate.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.desktop.navigation.Screen
import com.rate.desktop.ui.theme.AppColorScheme

// ── Top navigation bar ────────────────────────────────────────────────────

@Composable
fun AppNavBar(
    current:    Screen,
    onNavigate: (Screen) -> Unit
) {
    val screens = listOf(Screen.Calculator, Screen.Configurator, Screen.Import)
    NavigationBar(containerColor = AppColorScheme.primary) {
        screens.forEach { screen ->
            NavigationBarItem(
                selected = current == screen,
                onClick  = { onNavigate(screen) },
                label    = { Text(screen.label, color = Color.White) },
                icon     = {}
            )
        }
    }
}

// ── Section card (always visible) ─────────────────────────────────────────

@Composable
fun SectionCard(
    title:    String,
    modifier: Modifier = Modifier,
    content:  @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Collapsible section card ───────────────────────────────────────────────

@Composable
fun CollapsibleSectionCard(
    title:          String,
    badge:          String?  = null,
    startExpanded:  Boolean  = false,
    modifier:       Modifier = Modifier,
    content:        @Composable ColumnScope.() -> Unit
) {
    var expanded by remember(title) { mutableStateOf(startExpanded) }

    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            badge,
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint               = MaterialTheme.colorScheme.outline
                )
            }
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    content()
                }
            }
        }
    }
}

// ── Labelled dropdown ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabelledDropdown(
    label:    String,
    options:  List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    display:  (T) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = modifier
    ) {
        OutlinedTextField(
            value         = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(display(opt)) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

// ── Number field ──────────────────────────────────────────────────────────

@Composable
fun NumberField(
    label:    String,
    value:    String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { if (it.all { c -> c.isDigit() }) onChange(it) },
        label         = { Text(label) },
        singleLine    = true,
        modifier      = modifier.fillMaxWidth()
    )
}

// ── Premium display row ───────────────────────────────────────────────────

@Composable
fun PremiumRow(label: String, amount: Double, color: Color = Color.Unspecified) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "₹ %,.0f".format(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = color
        )
    }
}

// ── Info chip ─────────────────────────────────────────────────────────────

@Composable
fun InfoChip(
    label:    String,
    color:    Color   = Color(0xFFE3F2FD),
    textColor: Color  = Color(0xFF1565C0)
) {
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

// ── Loading overlay ───────────────────────────────────────────────────────

@Composable
fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ── Error banner ──────────────────────────────────────────────────────────

@Composable
fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color    = MaterialTheme.colorScheme.error,
            fontSize = 13.sp
        )
    }
}

// ── Warning banner ────────────────────────────────────────────────────────

@Composable
fun WarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color    = Color(0xFF5D4037),
            fontSize = 13.sp
        )
    }
}
