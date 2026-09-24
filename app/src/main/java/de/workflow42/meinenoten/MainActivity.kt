package de.workflow42.meinenoten

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.data.SettingsRepository
import de.workflow42.meinenoten.data.ThemeMode
import de.workflow42.meinenoten.ui.MainApp
import de.workflow42.meinenoten.ui.screens.LaunchScreen
import de.workflow42.meinenoten.ui.theme.MeineNotenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Written from composition, read by the splash screen's keep-on-screen condition on the
     * main thread. Until the stored settings are known the app cannot tell light from dark,
     * so the system splash stays up instead of flashing the wrong theme for a frame.
     */
    @Volatile
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate so the splash theme is swapped out correctly.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !settingsLoaded }
        // Removed at once instead of the default fade: the Compose launch screen underneath
        // shows the note at exactly the same size and place, so the swap is invisible and
        // the user sees one screen whose text simply fades in.
        splashScreen.setOnExitAnimationListener { it.remove() }

        // Initial call with the system default; re-applied below once the chosen theme is known.
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(this)
        // Person id for note authorship; a no-op on every start after the first.
        lifecycleScope.launch { settingsRepository.ensureUserId() }

        setContent {
            // Null until DataStore has answered – deliberately not AppSettings(), which would
            // render one frame with the default theme before the stored one arrives.
            val loadedSettings by settingsRepository.settings.collectAsState(initial = null)
            val settings = loadedSettings ?: AppSettings()
            val scope = rememberCoroutineScope()

            LaunchedEffect(loadedSettings != null) {
                if (loadedSettings != null) settingsLoaded = true
            }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // System bar icons follow the app's theme, not the device's. Without this, "Light"
            // on a phone set to dark mode leaves light icons on a light navigation bar.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LightNavScrim,
                        darkScrim = DarkNavScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }

            // Only on a real cold start: rotating the device or returning from the file
            // picker recreates the activity, and replaying the launch screen then would be
            // an annoyance rather than a greeting.
            var showLaunchScreen by rememberSaveable {
                mutableStateOf(savedInstanceState == null)
            }
            LaunchedEffect(loadedSettings != null) {
                if (loadedSettings != null && showLaunchScreen) {
                    delay(LaunchScreenMillis)
                    showLaunchScreen = false
                }
            }

            MeineNotenTheme(darkTheme = darkTheme) {
                Box {
                    MainApp(
                        settings = settings,
                        onSettingsChange = { new ->
                            scope.launch { settingsRepository.update { new } }
                        },
                    )

                    AnimatedVisibility(
                        visible = showLaunchScreen,
                        exit = fadeOut(animationSpec = tween(durationMillis = 400)),
                    ) {
                        // A tap skips the wait, and while visible it keeps touches from
                        // reaching the list underneath.
                        LaunchScreen(
                            userName = settings.displayName,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { showLaunchScreen = false }
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** Long enough to read the version, short enough not to be in the way. */
        const val LaunchScreenMillis = 1200L

        // Same scrims enableEdgeToEdge uses by default for three-button navigation.
        val LightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
