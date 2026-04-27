package com.streamforge.sdk.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.streamforge.sdk.exception.*
import com.streamforge.sdk.model.ErrorResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal class ApiClient(
    baseUrl: String,
    apiKey: String,
    enableLogging: Boolean = false,
    trustAllCertificates: Boolean = false
) {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ApiKeyInterceptor(apiKey))
        .apply {
            if (enableLogging) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            if (trustAllCertificates) {
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                sslSocketFactory(sslContext.socketFactory, trustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: StreamForgeApi = retrofit.create(StreamForgeApi::class.java)

    suspend fun <T> execute(call: suspend StreamForgeApi.() -> Response<T>): T {
        val response: Response<T>
        try {
            response = api.call()
        } catch (e: UnknownHostException) {
            throw SFNetworkException(
                "Unable to resolve host. Check your internet connection.", e
            )
        } catch (e: SocketTimeoutException) {
            throw SFNetworkException("Request timed out. Please try again.", e)
        } catch (e: IOException) {
            throw SFNetworkException("Network error: ${e.message}", e)
        }

        if (response.isSuccessful) {
            return response.body() ?: throw SFApiException(
                message = "Empty response body",
                statusCode = response.code()
            )
        }

        val errorBody = response.errorBody()?.string()
        val errorResponse = try {
            errorBody?.let {
                moshi.adapter(ErrorResponse::class.java).fromJson(it)
            }
        } catch (_: Exception) { null }

        val message = errorResponse?.message ?: errorBody ?: "Unknown error"

        throw when (response.code()) {
            401 -> SFAuthException(message = message, statusCode = 401)
            403 -> SFAuthException(
                message = "Access denied: $message", statusCode = 403
            )
            404 -> SFApiException(
                message = "Not found: $message", statusCode = 404
            )
            else -> SFApiException(
                message = message,
                statusCode = response.code(),
                errorBody = errorBody
            )
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trimEnd('/')
        if (!normalized.endsWith("/api/v1")) {
            normalized = "$normalized/api/v1"
        }
        return "$normalized/"
    }
}
