package de.workflow42.meinenoten.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.workflow42.meinenoten.ui.theme.MeineNotenTheme

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainAppPreview() {
    MeineNotenTheme {
        MainApp()
    }
}
