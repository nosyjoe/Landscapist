/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.landscapist

import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * https://gist.github.com/chrisbanes/9a1d64847278876186be81249d9f9eda
 *
 * Updates:
 * [Drawable Painter] Handle drawables with no intrinsic size #774
 * https://github.com/google/accompanist/pull/774/files
 *
 * A [Painter] which draws an Android [Drawable]. Supports [Animatable] drawables.
 *
 * Taken from https://goo.gle/compose-drawable-painter
 */
internal class DrawablePainter(
  val drawable: Drawable
) : Painter(), RememberObserver {
  private var invalidateTick by mutableStateOf(0)

  private val callback: Drawable.Callback by lazy {
    object : Drawable.Callback {
      override fun invalidateDrawable(d: Drawable) {
        // Update the tick so that we get re-drawn
        invalidateTick++
      }

      override fun scheduleDrawable(d: Drawable, what: Runnable, time: Long) {
        MAIN_HANDLER.postAtTime(what, time)
      }

      override fun unscheduleDrawable(d: Drawable, what: Runnable) {
        MAIN_HANDLER.removeCallbacks(what)
      }
    }
  }

  init {
    if (drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0) {
      // Update the drawable's bounds to match the intrinsic size
      drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    }
  }

  override fun onRemembered() {
    drawable.callback = callback
    drawable.setVisible(true, true)
    if (drawable is Animatable) drawable.start()
  }

  override fun onAbandoned() = onForgotten()

  override fun onForgotten() {
    if (drawable is Animatable) drawable.stop()
    drawable.setVisible(false, false)
    drawable.callback = null
  }

  override fun applyAlpha(alpha: Float): Boolean {
    drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    drawable.colorFilter = colorFilter?.asAndroidColorFilter()
    return true
  }

  override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
    if (Build.VERSION.SDK_INT >= 23) {
      return drawable.setLayoutDirection(
        when (layoutDirection) {
          LayoutDirection.Ltr -> View.LAYOUT_DIRECTION_LTR
          LayoutDirection.Rtl -> View.LAYOUT_DIRECTION_RTL
        }
      )
    }
    return false
  }

  override val intrinsicSize: Size
    get() = when {
      // Only return a finite size if the drawable has an intrinsic size
      drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0 -> {
        Size(
          width = drawable.intrinsicWidth.toFloat(),
          height = drawable.intrinsicHeight.toFloat(),
        )
      }
      else -> Size.Unspecified
    }

  override fun DrawScope.onDraw() {
    drawIntoCanvas { canvas ->
      // Reading this ensures that we invalidate when invalidateDrawable() is called
      invalidateTick

      // Update the Drawable's bounds
      drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())

      canvas.withSave {
        drawable.draw(canvas.nativeCanvas)
      }
    }
  }
}

private val MAIN_HANDLER by lazy(LazyThreadSafetyMode.NONE) {
  Handler(Looper.getMainLooper())
}

/**
 * Remembers [Drawable] wrapped up as a [Painter]. This function attempts to un-wrap the
 * drawable contents and use Compose primitives where possible.
 *
 * If the provided [drawable] is `null`, an empty no-op painter is returned.
 *
 * This function tries to dispatch lifecycle events to [drawable] as much as possible from
 * within Compose.
 */
@Composable
public fun rememberDrawablePainter(drawable: Drawable?): Painter = remember(drawable) {
  when (drawable) {
    null -> EmptyPainter
    is BitmapDrawable -> BitmapPainter(drawable.bitmap.asImageBitmap())
    is ColorDrawable -> ColorPainter(Color(drawable.color))
    // Since the DrawablePainter will be remembered and it implements RememberObserver, it
    // will receive the necessary events
    else -> DrawablePainter(drawable.mutate())
  }
}

internal object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() {}
}
