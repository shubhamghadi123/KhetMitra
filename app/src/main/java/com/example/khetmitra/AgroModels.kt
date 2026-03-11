package com.example.khetmitra

data class PolygonRequest(
    val name: String,
    val geo_json: GeoJson
)

data class GeoJson(
    val type: String = "Feature",
    val properties: Properties = Properties(),
    val geometry: Geometry
)

class Properties

data class Geometry(
    val type: String = "Polygon",
    val coordinates: List<List<List<Double>>>
)

data class PolygonResponse(
    val id: String,
    val name: String
)

data class SoilDataResponse(
    val dt: Long,
    val t10: Double,
    val moisture: Double,
    val t0: Double
)

data class SatelliteImageResponse(
    val dt: Long,
    val type: String,
    val image: ImageUrls,
    val stats: StatUrls? = null
)

data class ImageUrls(
    val truecolor: String,
    val ndvi: String
)

data class SavedCoordinate(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

data class AgroWeatherResponse(
    val weather: List<WeatherItem>,
    val main: MainWeather
)

data class WeatherItem(
    val main: String,
    val description: String
)

data class MainWeather(
    val temp: Double,
    val humidity: Double
)

data class StatUrls(
    val ndvi: String?
)

data class NdviStatResponse(
    val mean: Double,
    val max: Double,
    val min: Double
)