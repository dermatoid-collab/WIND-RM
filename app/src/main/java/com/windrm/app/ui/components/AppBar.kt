package com.windrm.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windrm.app.R

/** Height of the bar below the status bar: shorter than Material's 64 dp, as short as a touch target allows. */
val AppBarHeight = 44.dp

/**
 * The colored bar at the top of every screen: optional back arrow, title, the screen's own [actions] and,
 * on every screen but Settings itself, the Settings gear ([onOpenSettings] null = no gear).
 */
@Composable
fun AppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(AppBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(AppBarHeight)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            if (onOpenSettings != null) SettingsGear(onOpenSettings)
        }
    }
}

/** The Settings icon button, sized for [AppBar] and for the bars that draw their own row. */
@Composable
fun SettingsGear(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(AppBarHeight)) {
        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.menu_settings), tint = Color.White)
    }
}
