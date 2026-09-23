package de.workflow42.meinenoten.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.ui.components.VersionFooter
import de.workflow42.meinenoten.ui.theme.MeineNotenTheme
import kotlin.math.max

/**
 * Launch screen, continuing seamlessly from the system splash screen.
 *
 * The system splash (which an app cannot switch off on Android 12+) only allows a flat
 * background, so it shows white with the note on a blue circle. This screen starts from
 * exactly that picture: the circle's hard edge then softens into a radial gradient that
 * runs out to white at the screen edges, while name and version fade in.
 *
 * The gradient is centred on the note, so it reaches white at the nearer edges first; the
 * remaining space, above and below, simply stays white.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val blue = colorResource(R.color.ic_launcher_background)
    val ink = Color.Black

    // 0 = identical to the system splash, 1 = full gradient with text.
    // The preview skips the animation, otherwise it would only show the start picture.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val circleRadius = IconCircleDiameter.toPx() / 2f
                // White is reached at the nearer pair of screen edges.
                val fullRadius = max(circleRadius + 1f, size.minDimension / 2f)
                val p = progress.value
                // Solid blue shrinks from the circle to the centre while the soft edge
                // grows from the circle outline to the screen edge.
                val solidRadius = circleRadius * (1f - p)
                val outerRadius = circleRadius + (fullRadius - circleRadius) * p
                val solidStop = (solidRadius / outerRadius).coerceIn(0f, 0.999f)

                drawRect(Color.White)
                drawRect(
                    brush = Brush.radialGradient(
                        0f to blue,
                        solidStop to blue,
                        1f to Color.White,
                        center = center,
                        radius = outerRadius,
                    ),
                )
            },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(SystemSplashIconSize)
                .align(Alignment.Center),
        )

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 200.dp)
                .graphicsLayer { alpha = progress.value },
        )

        VersionFooter(
            color = ink.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = progress.value },
        )
    }
}

/**
 * Size at which Android 12+ draws a splash icon that has an icon background colour, and
 * the diameter of the visible circle inside it.
 */
private val SystemSplashIconSize = 240.dp
private val IconCircleDiameter = 160.dp

@Preview(showBackground = true)
@Composable
private fun LaunchScreenPreview() {
    MeineNotenTheme {
        LaunchScreen()
    }
}
