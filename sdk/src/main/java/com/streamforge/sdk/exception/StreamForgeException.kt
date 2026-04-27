package com.streamforge.sdk.exception

/** Base exception for all StreamForge SDK errors. */
open class StreamForgeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Thrown when the API key is invalid or expired (HTTP 401/403). */
class SFAuthException(
    message: String = "Authentication failed. Check your API key.",
    val statusCode: Int = 401,
    cause: Throwable? = null
) : StreamForgeException(message, cause)

/** Thrown when a network error occurs (no connection, timeout, etc.). */
class SFNetworkException(
    message: String = "Network error. Check your internet connection.",
    cause: Throwable? = null
) : StreamForgeException(message, cause)

/** Thrown when the requested stream ID does not exist (HTTP 404). */
class SFStreamNotFoundException(
    val streamIdentifier: String,
    message: String = "Stream not found: $streamIdentifier",
    cause: Throwable? = null
) : StreamForgeException(message, cause)

/** Thrown when SDK methods are called before [com.streamforge.sdk.StreamForge.init]. */
class SFNotInitializedException(
    message: String = "StreamForge SDK is not initialized. Call StreamForge.init() first."
) : StreamForgeException(message)

/** Thrown for general API errors (non-401/403/404). */
class SFApiException(
    message: String,
    val statusCode: Int,
    val errorBody: String? = null,
    cause: Throwable? = null
) : StreamForgeException(message, cause)

/** Thrown when the API key lacks a required permission scope. */
class SFInsufficientScopeException(
    val requiredScope: String,
    message: String = "API key does not have required scope: $requiredScope"
) : StreamForgeException(message)
