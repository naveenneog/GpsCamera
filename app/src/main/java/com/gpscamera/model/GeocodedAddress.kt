package com.gpscamera.model

data class GeocodedAddress(
    val fullAddress: String,
    val locality: String?,
    val adminArea: String?,
    val countryName: String?,
    val countryCode: String?,
    val postalCode: String?
)
