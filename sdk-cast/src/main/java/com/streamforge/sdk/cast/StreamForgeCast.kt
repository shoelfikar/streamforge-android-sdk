package com.streamforge.sdk.cast

import android.content.Context
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

object StreamForgeCast {

    internal var receiverAppId: String? = null

    private var castManager: CastManager? = null

    val isCasting: Boolean
        get() = castManager?.isCasting == true

    /**
     * Initialize Cast support. Call this in Application.onCreate() or Activity.onCreate().
     *
     * @param context Application context
     * @param receiverAppId Optional custom Cast receiver app ID.
     *   If null, uses the default media receiver.
     */
    fun initialize(context: Context, receiverAppId: String? = null) {
        this.receiverAppId = receiverAppId
        val manager = CastManager()
        manager.initialize(context)
        castManager = manager
    }

    /**
     * Setup a MediaRouteButton for Cast device discovery.
     * Add this button to your toolbar or layout.
     */
    fun setupCastButton(button: MediaRouteButton) {
        CastButtonFactory.setUpMediaRouteButton(button.context, button)
    }

    /**
     * Create a new MediaRouteButton ready for Cast.
     */
    fun createCastButton(context: Context): MediaRouteButton {
        return MediaRouteButton(context).also {
            CastButtonFactory.setUpMediaRouteButton(context, it)
        }
    }

    /**
     * Start casting a stream URL to the connected Cast device.
     */
    fun startCasting(streamUrl: String, title: String? = null) {
        castManager?.startCasting(streamUrl, title)
    }

    /**
     * Stop casting and disconnect from the Cast device.
     */
    fun stopCasting() {
        castManager?.stopCasting()
    }

    /**
     * Set a listener for Cast connection state changes.
     */
    fun setOnCastStateChangedListener(listener: (Boolean) -> Unit) {
        castManager?.setOnCastStateChangedListener(listener)
    }

    /**
     * Release Cast resources. Call when the app is being destroyed.
     */
    fun release() {
        castManager?.release()
        castManager = null
        receiverAppId = null
    }
}
