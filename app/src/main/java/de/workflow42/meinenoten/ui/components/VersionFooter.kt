package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import de.workflow42.meinenoten.BuildConfig
import de.workflow42.meinenoten.R

/**
 * Version and author credit.
 *
 * Shared by the settings screen and the launch screen so both show exactly the same,
 * deliberately quiet line – and so bug reports can name the exact build.
 *
 * [color] defaults to the theme's outline colour; the launch screen passes its own because
 * it sits on the brand colour instead of the app background.
 */
@Composable
fun VersionFooter(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                R.string.settings_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        Text(
            text = stringResource(R.string.settings_by),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
