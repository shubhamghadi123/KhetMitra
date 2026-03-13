package com.example.khetmitra

import kotlinx.serialization.SerialName
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
    val polygon_id: String? = null
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
data class StateRow(
    val id: Int,
    @SerialName("state_id") val stateId: Int,
    @SerialName("state_name") val stateName: String,
    val status: Int
)

@Serializable
data class DistrictRow(
    val id: Int,
    @SerialName("district_id") val districtId: Int,
    @SerialName("district_name") val districtName: String,
    @SerialName("state_id") val stateId: Int,
    val status: Int
)

@Serializable
data class MarketRow(
    val id: Int,
    @SerialName("market_id") val marketId: Int,
    @SerialName("market_name") val marketName: String,
    @SerialName("state_id") val stateId: Int,
    @SerialName("district_id") val districtId: Int,
    val status: Int
)

@Serializable
data class CropRow(
    val id: Int,
    @SerialName("crop_id") val cropId: Int,
    @SerialName("crop_name") val cropName: String,
    @SerialName("crop_group_id") val cropGroupId: Int,
    val status: Int
)

@Serializable
data class CropPriceRow(
    val id: Int,
    @SerialName("crop_id") val cropId: Int,
    @SerialName("crop_name") val cropName: String,
    @SerialName("crop_group_id") val cropGroupId: Int,
    @SerialName("crop_group_name") val cropGroupName: String,
    @SerialName("state_id") val stateId: Int,
    @SerialName("state_name") val stateName: String,
    @SerialName("district_id") val districtId: Int,
    @SerialName("district_name") val districtName: String,
    @SerialName("market_id") val marketId: Int,
    @SerialName("market_name") val marketName: String,
    @SerialName("min_price") val minPrice: Float,
    @SerialName("max_price") val maxPrice: Float,
    @SerialName("modal_price") val modalPrice: Float,
    @SerialName("price_unit") val priceUnit: String,
    @SerialName("price_date") val priceDate: String  // "YYYY-MM-DD"
)

@Serializable
data class ChatHistoryEntry(
    val user_id: String,
    val message: String,
    val is_user: Boolean,
    val image_url: String? = null,
    val created_at: String? = null
)