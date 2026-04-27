package com.streamforge.sdk.model

data class TenantConfig(
    val tenantId: String,
    val tenantName: String,
    val tenantSlug: String,
    val status: String,
    val brandColor: String? = null,
    val logoUrl: String? = null,
    val faviconUrl: String? = null,
    val planName: String? = null,
    val planDisplayName: String? = null
) {
    companion object {
        fun fromSdkTenantResponse(response: SdkTenantResponse): TenantConfig {
            val tenant = response.tenant
            return TenantConfig(
                tenantId = tenant.id,
                tenantName = tenant.name,
                tenantSlug = tenant.slug,
                status = tenant.status,
                brandColor = tenant.brandColor,
                logoUrl = tenant.logoUrl,
                faviconUrl = tenant.faviconUrl,
                planName = tenant.plan?.name,
                planDisplayName = tenant.plan?.displayName
            )
        }
    }
}
