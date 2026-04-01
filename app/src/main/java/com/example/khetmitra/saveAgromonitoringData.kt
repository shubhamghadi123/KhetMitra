package com.example.khetmitra

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun saveAgromonitoringData(
    fieldName: String,
    polyId: String? = null,
    temp: Double? = null,
    hum: Double? = null,
    condition: String? = null,
    soilMoist: Double? = null,
    soilTemp: Double? = null,
    ndviScore: Double? = null
) {
    withContext(Dispatchers.IO) {
        try {
            val user = SupabaseManager.client.auth.currentUserOrNull()
            if (user != null && polyId != null) {

                val existingRows = SupabaseManager.client.postgrest["field_monitoring"]
                    .select {
                        filter {
                            eq("polygon_id", polyId)
                            eq("user_id", user.id)
                        }
                    }.decodeList<FieldMonitoring>()

                if (existingRows.isNotEmpty()) {
                    SupabaseManager.client.postgrest["field_monitoring"].update(
                        {
                            if (temp != null) set("temperature", temp)
                            if (hum != null) set("humidity", hum)
                            if (condition != null) set("weather_condition", condition)
                            if (soilMoist != null) set("soil_moisture", soilMoist)
                            if (soilTemp != null) set("soil_temperature", soilTemp)
                            if (ndviScore != null) set("ndvi_score", ndviScore)

                            set("field_name", fieldName)
                        }
                    ) {
                        filter {
                            eq("polygon_id", polyId)
                            eq("user_id", user.id)
                        }
                    }
                } else {
                    val monitoringData = FieldMonitoring(
                        user_id = user.id,
                        polygon_id = polyId,
                        field_name = fieldName,
                        temperature = temp,
                        humidity = hum,
                        weather_condition = condition,
                        soil_moisture = soilMoist,
                        soil_temperature = soilTemp,
                        ndvi_score = ndviScore
                    )
                    SupabaseManager.client.postgrest["field_monitoring"].insert(monitoringData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}