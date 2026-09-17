package it.vincenzo.visitaclienti

import android.content.Context
import android.view.View

/**
 * Compatibility helper: accepts either an Activity/Context or a View as the
 * first argument, so Toast calls remain safe inside Kotlin apply/listener scopes.
 */
object Toast {
    const val LENGTH_SHORT: Int = android.widget.Toast.LENGTH_SHORT
    const val LENGTH_LONG: Int = android.widget.Toast.LENGTH_LONG

    fun makeText(source: Any, text: CharSequence, duration: Int): android.widget.Toast {
        val context: Context = when (source) {
            is Context -> source
            is View -> source.context
            else -> throw IllegalArgumentException("Toast source must be a Context or View")
        }
        return android.widget.Toast.makeText(context, text, duration)
    }

    fun makeText(source: Any, textResId: Int, duration: Int): android.widget.Toast {
        val context: Context = when (source) {
            is Context -> source
            is View -> source.context
            else -> throw IllegalArgumentException("Toast source must be a Context or View")
        }
        return android.widget.Toast.makeText(context, textResId, duration)
    }
}
