package com.streamforge.sdk.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StreamsResponse(
    @Json(name = "streams") val streams: List<Stream>
)

@JsonClass(generateAdapter = true)
data class StreamResponse(
    @Json(name = "stream") val stream: Stream
)

@JsonClass(generateAdapter = true)
data class StreamUrlResponse(
    @Json(name = "url") val url: String,
    @Json(name = "title") val title: String,
    @Json(name = "is_live") val isLive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Stream(
    @Json(name = "id") val id: String,
    @Json(name = "tenant_id") val tenantId: String,
    @Json(name = "name") val name: String,
    @Json(name = "stream_key") val streamKey: String,
    @Json(name = "flussonic_stream_name") val flussonicStreamName: String,
    @Json(name = "ingest_protocol") val ingestProtocol: String,
    @Json(name = "srt_port") val srtPort: Int? = null,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "ingest_url") val ingestUrl: String? = null,
    @Json(name = "hls_url") val hlsUrl: String? = null,
    @Json(name = "dash_url") val dashUrl: String? = null,
    @Json(name = "rtmp_url") val rtmpUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "is_static") val isStatic: Boolean? = null,
    @Json(name = "transcoder") val transcoder: StreamTranscoderInfo? = null,
    @Json(name = "thumbnails") val thumbnails: StreamThumbnailsInfo? = null,
    @Json(name = "live_status") val liveStatus: StreamLiveStatus? = null
) {
    val isLive: Boolean
        get() = status == "live" || liveStatus?.alive == true
}

@JsonClass(generateAdapter = true)
data class StreamTranscoderInfo(
    @Json(name = "audio") val audio: AudioTranscoder? = null,
    @Json(name = "video") val video: List<VideoTranscoder> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AudioTranscoder(
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null
)

@JsonClass(generateAdapter = true)
data class VideoTranscoder(
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null,
    @Json(name = "fps") val fps: Int? = null,
    @Json(name = "preset") val preset: String? = null,
    @Json(name = "profile") val profile: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class StreamThumbnailsInfo(
    @Json(name = "enabled") val enabled: Boolean,
    @Json(name = "sizes") val sizes: List<ThumbnailSize> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ThumbnailSize(
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int
)

@JsonClass(generateAdapter = true)
data class StreamLiveStatus(
    @Json(name = "alive") val alive: Boolean,
    @Json(name = "status") val status: String? = null,
    @Json(name = "lifetime") val lifetime: Long = 0
)

object StreamStatus {
    const val CREATED = "created"
    const val LIVE = "live"
    const val OFFLINE = "offline"
    const val ERROR = "error"
}
