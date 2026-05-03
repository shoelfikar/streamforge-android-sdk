package com.streamforge.sdk.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.streamforge.sdk.R
import com.streamforge.sdk.exception.*

/**
 * A full-screen error view shown when player setup fails.
 *
 * Displays a user-friendly error message based on the exception type,
 * with an optional retry button.
 */
class StreamForgeErrorView(
    context: Context,
    private val error: StreamForgeException
) : FrameLayout(context) {

    private var onRetryClickListener: (() -> Unit)? = null
    private var onBackClickListener: (() -> Unit)? = null

    init {
        setBackgroundColor(0xFF111111.toInt())
        buildLayout()
    }

    fun setOnRetryClickListener(listener: () -> Unit) {
        onRetryClickListener = listener
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        onBackClickListener = listener
    }

    private fun buildLayout() {
        val errorInfo = getErrorInfo(error)

        // Center container
        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER }
        }

        // Error icon
        val iconSize = dp(48)
        val iconView = ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, errorInfo.icon))
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        }

        // Error title
        val titleView = TextView(context).apply {
            text = errorInfo.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
        }

        // Error message
        val messageView = TextView(context).apply {
            text = errorInfo.message
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
        }

        centerContainer.addView(iconView)
        centerContainer.addView(titleView)
        centerContainer.addView(messageView)

        // Buttons row
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        // Back button
        val backButton = createButton("Back", 0xFF333333.toInt())
        backButton.setOnClickListener { onBackClickListener?.invoke() }
        buttonsRow.addView(backButton)

        // Retry button (only for retryable errors)
        if (errorInfo.retryable) {
            val retryButton = createButton("Retry", 0xFFE53935.toInt())
            retryButton.setOnClickListener { onRetryClickListener?.invoke() }
            buttonsRow.addView(retryButton)
        }

        centerContainer.addView(buttonsRow)

        // Back arrow (top-left)
        val arrowSize = dp(24)
        val backArrow = FrameLayout(context).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.START or Gravity.TOP }
            setOnClickListener { onBackClickListener?.invoke() }
            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.sf_ic_arrow_back))
                setColorFilter(Color.WHITE)
                layoutParams = LayoutParams(arrowSize, arrowSize)
            })
        }

        addView(centerContainer)
        addView(backArrow)
    }

    private fun createButton(text: String, bgColor: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8); marginEnd = dp(8) }
        }
    }

    private fun getErrorInfo(error: StreamForgeException): ErrorInfo {
        return when (error) {
            is SFAuthException -> ErrorInfo(
                icon = R.drawable.sf_ic_lock,
                title = "Authentication Failed",
                message = when {
                    error.message?.contains("revoked", ignoreCase = true) == true ->
                        "Your API key has been revoked. Please contact the administrator to get a new key."
                    error.message?.contains("expired", ignoreCase = true) == true ->
                        "Your API key has expired. Please renew your API key."
                    error.statusCode == 403 ->
                        "Access denied. Your API key does not have permission to access this stream."
                    else ->
                        "Invalid API key. Please check your API key and try again."
                },
                retryable = false
            )
            is SFNetworkException -> ErrorInfo(
                icon = R.drawable.sf_ic_signal_cellular,
                title = "Connection Error",
                message = when {
                    error.message?.contains("timeout", ignoreCase = true) == true ->
                        "The connection timed out. Please check your internet connection and try again."
                    error.message?.contains("resolve", ignoreCase = true) == true ->
                        "Unable to connect to the server. Please check your internet connection."
                    else ->
                        "A network error occurred. Please check your connection and try again."
                },
                retryable = true
            )
            is SFStreamNotFoundException -> ErrorInfo(
                icon = R.drawable.sf_ic_tv,
                title = "Stream Not Found",
                message = "The requested stream could not be found. It may have ended or the ID is incorrect.",
                retryable = false
            )
            is SFInsufficientScopeException -> ErrorInfo(
                icon = R.drawable.sf_ic_block,
                title = "Permission Denied",
                message = "Your API key does not have the required permissions to play this stream.",
                retryable = false
            )
            else -> ErrorInfo(
                icon = R.drawable.sf_ic_warning,
                title = "Playback Error",
                message = error.message ?: "An unexpected error occurred. Please try again.",
                retryable = true
            )
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private data class ErrorInfo(
        val icon: Int,
        val title: String,
        val message: String,
        val retryable: Boolean
    )
}
