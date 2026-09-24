package de.workflow42.meinenoten.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.ui.util.PossessiveRule
import de.workflow42.meinenoten.ui.util.personalTitle

/**
 * "‹Name›s Noten" for [userName], or the plain app name when none is set.
 *
 * Resolved from resources so the grammar follows the UI language: German "Hans’ Noten",
 * English "Hans's Sheets".
 */
@Composable
fun appTitle(userName: String): String = personalTitle(
    name = userName,
    rule = PossessiveRule.fromTag(stringResource(R.string.possessive_rule)),
    template = stringResource(R.string.app_title_personal),
    fallback = stringResource(R.string.app_name),
)
