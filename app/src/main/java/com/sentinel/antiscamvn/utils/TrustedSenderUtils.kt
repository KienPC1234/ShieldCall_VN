package com.sentinel.antiscamvn.utils

object TrustedSenderUtils {

    // List of trusted display names or addresses (case-insensitive for safety)
    private val TRUSTED_SENDERS = setOf(
        "VIETTEL",
        "VINAPHONE",
        "MOBIFONE",
        "VIETNAMOBILE",
        "GMOBILE",
        "ITEL",
        "WINTEL",
        "REDDI",
        "LOCAL",
        "191", // Viettel Promo
        "199", // Viettel Cust Service
        "18008098", // Viettel
        "900", // Mobifone
        "9090", // Mobifone
        "18001090", // Mobifone
        "9191", // Vinaphone
        "18001091", // Vinaphone
        "CSKH", // Generic Customer Service (often legitimate carriers)
        "BO Y TE", // Ministry of Health (Vietnam)
        "VNEID" // National ID App
    )

    fun isTrusted(address: String?, displayAddress: String?): Boolean {
        if (address == null && displayAddress == null) return false

        val cleanAddress = address?.uppercase()?.trim() ?: ""
        val cleanDisplay = displayAddress?.uppercase()?.trim() ?: ""

        // Check if either the raw address or display name matches our trusted list
        return TRUSTED_SENDERS.contains(cleanAddress) || TRUSTED_SENDERS.contains(cleanDisplay)
    }
}
