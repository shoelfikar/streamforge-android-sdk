package com.streamforge.sdk.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener

class CastManager {

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null
    private var onCastStateChanged: ((Boolean) -> Unit)? = null

    val isCasting: Boolean
        get() = castSession?.isConnected == true

    fun initialize(context: Context) {
        castContext = CastContext.getSharedInstance(context)
        sessionManager = castContext?.sessionManager
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun setOnCastStateChangedListener(listener: (Boolean) -> Unit) {
        onCastStateChanged = listener
    }

    fun startCasting(streamUrl: String, title: String? = null, contentType: String = "application/x-mpegURL") {
        val session = castSession ?: return
        val remoteMediaClient = session.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title ?: "StreamForge Live")
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteMediaClient.load(loadRequest)
    }

    fun stopCasting() {
        castSession?.remoteMediaClient?.stop()
        sessionManager?.endCurrentSession(true)
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        castContext = null
        sessionManager = null
        castSession = null
        onCastStateChanged = null
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            onCastStateChanged?.invoke(true)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            onCastStateChanged?.invoke(true)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
            onCastStateChanged?.invoke(false)
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }
}
