package com.example.khetmitra

import kotlinx.serialization.Serializable

@Serializable
data class FarmerProfile(
    val id: String,
    val first_name: String,
    val last_name: String,
    val phone_number: String,
    val email: String,
    val gov_farmer_id: String? = null,
    val date_of_birth: String,
    val state_location: String,
    val annual_income_range: String,
    val district: String,

    val land_size: String? = null,
    val soil_type: String? = null,
    val crops: String? = null
)

@Serializable
data class FarmEntry(
    val farmer_id: String,
    val land_size: String,
    val soil_type: String,
    val coordinates: String
)

@Serializable
data class FetchedFarm(
    val coordinates: String,
    val land_size: String,
    val soil_type: String
)
