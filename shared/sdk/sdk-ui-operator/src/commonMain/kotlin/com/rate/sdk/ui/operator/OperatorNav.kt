package com.rate.sdk.ui.operator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.ui.graphics.vector.ImageVector
import com.rate.sdk.ui.operator.registry.HubGroup

/**
 * One navigable destination in the operator console. Either a fixed operational surface
 * ([OperatorRoute.Surface]), a business HUB card-grid ([OperatorRoute.Hub], keyed by [HubGroup]), or
 * a metadata-driven admin config editor for one entity ([OperatorRoute.Config], keyed by the
 * entity's registry id).
 *
 * Routes are gated by [OperatorRole] so a BUSINESS user sees the operational surfaces but not the
 * destructive config CRUD / ingestion, while ADMIN/OWNER see the hub grid + every editor.
 */
sealed interface OperatorRoute {
    val id: String
    val label: String
    val icon: ImageVector

    enum class Surface(
        override val id: String,
        override val label: String,
        override val icon: ImageVector,
    ) : OperatorRoute {
        HOME("home", "Home", Icons.Filled.Dashboard),
        QUOTES("quotes", "Quote Explorer", Icons.Filled.ReceiptLong),
        CALCULATOR("calculator", "Rate Calculator", Icons.Filled.Calculate),
        SALES_DASHBOARD("sales-dashboard", "Sales Dashboard", Icons.Filled.Insights),
        SALES_PIPELINE("sales-pipeline", "Sales Pipeline", Icons.Filled.ViewKanban),
        GROUP("group", "Group Quoting", Icons.Filled.Groups),
        GROUP_MANUAL("group-manual", "Group Quote (Manual)", Icons.Filled.Calculate),
        ADDON_MATRIX("addon-matrix", "Add-on Matrix", Icons.Filled.GridOn),
        IMPORTS("imports", "Rate Imports", Icons.Filled.UploadFile),
        AUDIT("audit", "Audit Log", Icons.Filled.History),
    }

    /** A hub card-grid for one business [group]; clicking a card drills into a [Config] route. */
    data class Hub(val group: HubGroup) : OperatorRoute {
        override val id: String = "hub:${group.name}"
        override val label: String = group.title
        override val icon: ImageVector = hubIcon(group)
    }

    /**
     * A generated route to the generic config editor for the entity registered under [entityId].
     *
     * @param createOnOpen when true the editor opens straight into the "New" create drawer (the
     *   hub "Quick add" affordance). One-shot — consumed on first composition.
     */
    data class Config(
        val entityId: String,
        override val label: String,
        override val icon: ImageVector,
        val isGroup: Boolean,
        /** Group this config entity belongs to (drives side-nav section + back-to-hub). */
        val group: HubGroup,
        val createOnOpen: Boolean = false,
    ) : OperatorRoute {
        override val id: String = "config:$entityId"
    }

    companion object {
        /** Icon for an entity config route (retail config gets a tune dial, group gets a box). */
        fun configIcon(isGroup: Boolean): ImageVector =
            if (isGroup) Icons.Filled.Inventory2 else Icons.Filled.Tune

        /** Icon for a business hub group. */
        fun hubIcon(group: HubGroup): ImageVector = when (group) {
            HubGroup.CONFIGURATION -> Icons.Filled.AccountTree
            HubGroup.DEMOGRAPHICS -> Icons.Filled.Public
            HubGroup.PARTNERS -> Icons.Filled.Handshake
            HubGroup.UNDERWRITING -> Icons.Filled.MedicalServices
            HubGroup.FINANCIAL -> Icons.Filled.AttachMoney
            HubGroup.ACTUARIAL -> Icons.Filled.Functions
            HubGroup.SALES -> Icons.Filled.Sell
        }

        /** Fallback document icon. */
        val docIcon: ImageVector get() = Icons.Filled.Article
    }
}
