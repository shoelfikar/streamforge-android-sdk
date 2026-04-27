package com.streamforge.sdk.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from GET /sdk/tenant
 */
@JsonClass(generateAdapter = true)
data class SdkTenantResponse(
    @Json(name = "tenant") val tenant: SdkTenantInfo
)

@JsonClass(generateAdapter = true)
data class SdkTenantInfo(
    @Json(name = "id") val id: String,
    @Json(name = "slug") val slug: String,
    @Json(name = "name") val name: String,
    @Json(name = "brand_color") val brandColor: String? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "favicon_url") val faviconUrl: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "plan") val plan: SdkTenantPlan? = null
)

@JsonClass(generateAdapter = true)
data class SdkTenantPlan(
    @Json(name = "name") val name: String,
    @Json(name = "display_name") val displayName: String
)
