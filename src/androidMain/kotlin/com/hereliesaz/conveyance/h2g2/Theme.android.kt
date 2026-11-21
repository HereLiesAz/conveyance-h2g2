package com.hereliesaz.conveyance.h2g2

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
internal actual fun H2g2PlatformEffects(ground: Ground) {
    val animatorScale = rememberSystemAnimatorScale()
    val view = LocalView.current
    SideEffect {
        H2g2Motion.updateDurationScale(animatorScale)
        if (!view.isInEditMode) view.context.findActivity()?.window?.applyH2g2Ground(ground)
    }
}

/**
 * Paints this window with [ground] before (or independently of) Compose: the window background, so
 * nothing flashes a different colour behind the first frame, a keyboard resize or an overscroll;
 * and light/dark status- and navigation-bar icons chosen for the ground's own text colour (dark
 * icons on a light ground), not for the system theme.
 *
 * [H2g2Theme] calls it on every composition. An Activity should also call it from `onCreate`
 * before `setContent` (after `enableEdgeToEdge()`), with the ground its [GroundState] already
 * holds, so a launch on a dark ground does not start with a light window.
 */
fun Window.applyH2g2Ground(ground: Ground) {
    setBackgroundDrawable(ColorDrawable(ground.page.toArgb()))
    val lightGround = ground.onPage == H2g2.ink
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.isAppearanceLightStatusBars = lightGround
    controller.isAppearanceLightNavigationBars = lightGround
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * The system "Animator duration scale" (Developer options; "Remove animations" sets it to 0),
 * kept live: a settings observer updates it while the screen is up, so toggling the preference
 * takes effect without relaunching.
 */
@Composable
private fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(readAnimatorScale(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}
