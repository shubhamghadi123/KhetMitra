package com.example.khetmitra

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AgroMonitoringApi {

    @POST("polygons")
    suspend fun createPolygon(
        @Query("appid") apiKey: String,
        @Body request: PolygonRequest
    ): Response<PolygonResponse>

    @GET("soil")
    suspend fun getSoilData(
        @Query("polyid") polyId: String,
        @Query("appid") apiKey: String
    ): Response<SoilDataResponse>

    @GET("image/search")
    suspend fun getSatelliteImages(
        @Query("polyid") polyId: String,
        @Query("start") start: Long,
        @Query("end") end: Long,
        @Query("appid") apiKey: String
    ): Response<List<SatelliteImageResponse>>
}