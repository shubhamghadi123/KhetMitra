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
    val id: String? = null,
    val farmer_id: String,
    val name: String? = null,
    val land_size: String,
    val soil_type: String,
    val coordinates: String,
    val crop: String? = "Not Selected",
    val poly_id: String? = null
)

@Serializable
data class FetchedFarm(
    val coordinates: String,
    val land_size: String,
    val soil_type: String
)

@Serializable
data class FieldMonitoring(
    val user_id: String,
    val polygon_id: String? = null,
    val field_name: String,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val weather_condition: String? = null,
    val soil_moisture: Double? = null,
    val soil_temperature: Double? = null,
    val ndvi_score: Double? = null
)

@Serializable
data class ChatHistoryEntry(
    val user_id: String,
    val message: String,
    val is_user: Boolean,
    val image_url: String? = null,
    val created_at: String? = null
)