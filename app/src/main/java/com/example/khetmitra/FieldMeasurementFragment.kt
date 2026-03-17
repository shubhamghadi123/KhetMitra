package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.maps.android.SphericalUtil
import com.google.mlkit.nl.translate.TranslateLanguage
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnCircleAnnotationDragListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class FieldMeasurementFragment : Fragment(R.layout.fragment_field_measurement) {

    private lateinit var mapView: MapView
    private lateinit var polygonAnnotationManager: PolygonAnnotationManager
    private lateinit var circleAnnotationManager: CircleAnnotationManager
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private val savedFarmsDataMap = mutableMapOf<String, FetchedFarm>()
    private var activePolygonAnnotation: com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation? = null
    private val boundaryPoints = mutableListOf<LatLng>()
    private val circleIdToIndex = mutableMapOf<String, Int>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false
    private var lastCalculatedAreaAcres: Double = 0.0
    private lateinit var tvCalculatedArea: TextView
    private lateinit var cardWalkBoundary: MaterialCardView
    private lateinit var cardNextStep: MaterialCardView
    private lateinit var cardClearMap: MaterialCardView
    private lateinit var cardUndo: MaterialCardView
    private lateinit var tvWalkLabel: TextView
    private lateinit var ivWalkIcon: ImageView
    private var langCode: String = TranslateLanguage.ENGLISH

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    private var nextStepEnabled = false
        set(value) {
            field = value
            cardNextStep.alpha = if (value) 1f else 0.4f
            cardNextStep.isClickable = value
            cardNextStep.isFocusable = value
        }

    private fun setWalkingState(walking: Boolean) {
        if (walking) {
            tvWalkLabel.text = t("Stop Walking")
            cardWalkBoundary.setCardBackgroundColor(Color.RED)
            ivWalkIcon.setColorFilter(Color.WHITE)
        } else {
            tvWalkLabel.text = t("Start Walking")
            cardWalkBoundary.setCardBackgroundColor("#1A3C2E".toColorInt())
            ivWalkIcon.clearColorFilter()
            ivWalkIcon.setColorFilter("#52B788".toColorInt())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        mapView          = view.findViewById(R.id.mapView)
        tvCalculatedArea = view.findViewById(R.id.tvCalculatedArea)
        cardWalkBoundary = view.findViewById(R.id.btnWalkBoundary)
        cardNextStep     = view.findViewById(R.id.btnNextStep)
        cardClearMap     = view.findViewById(R.id.btnClearMap)
        cardUndo         = view.findViewById(R.id.btnUndo)
        tvWalkLabel = cardWalkBoundary.findViewById(R.id.tvWalkLabel)
        ivWalkIcon  = cardWalkBoundary.findViewById(R.id.ivWalkIcon)
        view.findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val overlay = view.findViewById<View>(R.id.mapInstructionsOverlay)
        overlay.visibility = View.VISIBLE
        startInstructionAnimation(overlay)
        overlay.setOnClickListener {
            overlay.animate().alpha(0f).setDuration(300).withEndAction {
                overlay.visibility = View.GONE
                overlay.isClickable = false
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapView.mapboxMap.loadStyle(Style.SATELLITE_STREETS) {
            val annotationApi = mapView.annotations
            polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            circleAnnotationManager = annotationApi.createCircleAnnotationManager()
            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            pointAnnotationManager.addClickListener { annotation ->
                val farmData = savedFarmsDataMap[annotation.id]
                if (farmData != null) showFarmDetailsDialog(farmData)
                true
            }
            loadExistingFarms()
            circleAnnotationManager.addDragListener(object : OnCircleAnnotationDragListener {
                override fun onAnnotationDrag(annotation: com.mapbox.maps.plugin.annotation.Annotation<*>) {
                    val circle = annotation as CircleAnnotation
                    val index = circleIdToIndex[circle.id]
                    if (index != null && index < boundaryPoints.size) {
                        boundaryPoints[index] = LatLng(circle.point.latitude(), circle.point.longitude())
                        updatePolygon()
                    }
                }

                override fun onAnnotationDragStarted(annotation: com.mapbox.maps.plugin.annotation.Annotation<*>) {
                }

                override fun onAnnotationDragFinished(annotation: com.mapbox.maps.plugin.annotation.Annotation<*>) {
                    val circle = annotation as CircleAnnotation
                    val index = circleIdToIndex[circle.id]
                    if (index != null && index < boundaryPoints.size) {
                        boundaryPoints[index] = LatLng(circle.point.latitude(), circle.point.longitude())
                        updatePolygon()
                        calculateArea()
                    }
                }
            })

            mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = true
            }
            centerMapOnCurrentLocation()
            mapView.mapboxMap.addOnMapClickListener { point ->
                if (overlay.isVisible) {
                    overlay.visibility = View.GONE
                    overlay.isClickable = false
                }

                if (!isTracking) {
                    addPoint(point.latitude(), point.longitude(), true)
                } else {
                    Toast.makeText(requireContext(), t("Stop walking to tap manually"), Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        cardWalkBoundary.setOnClickListener { if (isTracking) stopTracking() else startTracking() }
        cardClearMap.setOnClickListener    { resetMap() }
        cardUndo.setOnClickListener        { undoLastPoint() }
        cardNextStep.setOnClickListener {
            val fieldLat = if (boundaryPoints.isNotEmpty()) boundaryPoints[0].latitude else 0.0
            val fieldLng = if (boundaryPoints.isNotEmpty()) boundaryPoints[0].longitude else 0.0
            val coordinatesList = boundaryPoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }
            val coordinatesJson = Gson().toJson(coordinatesList)
            val soilSheet = SoilBottomSheetFragment.newInstance(lastCalculatedAreaAcres, fieldLat, fieldLng, coordinatesJson)
            soilSheet.show(parentFragmentManager, "SoilSheet")
        }
        setupLocationCallback()
        if (langCode != TranslateLanguage.ENGLISH) {
            view.post {
                TranslationHelper.translateViewHierarchy(view, langCode) {
                    resetMap()
                }
            }
        } else {
            resetMap()
        }
    }

    private fun showFarmDetailsDialog(farm: FetchedFarm) {
        val translatedTitle = t("Saved Farm Details")
        val areaParts = farm.land_size.split(" ")
        val translatedArea = if (areaParts.size == 2) "${d(areaParts[0])} ${t(areaParts[1])}" else farm.land_size
        var message = "${t("Area")}: $translatedArea\n${t("Soil")}: ${t(farm.soil_type)}"

        if (farm.sand_pct != null && farm.silt_pct != null && farm.clay_pct != null) {
            val sandFmt = String.format(Locale.US, "%.1f", farm.sand_pct)
            val siltFmt = String.format(Locale.US, "%.1f", farm.silt_pct)
            val clayFmt = String.format(Locale.US, "%.1f", farm.clay_pct)
            message += "\n\n--- ${t("Satellite Analysis")} ---"
            message += "\n• ${t("Sand")}: ${d(sandFmt)}%"
            message += "\n• ${t("Silt")}: ${d(siltFmt)}%"
            message += "\n• ${t("Clay")}: ${d(clayFmt)}%"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(translatedTitle)
            .setMessage(message)
            .setPositiveButton(t("OK")) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun loadExistingFarms() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    val farms = SupabaseManager.client.postgrest["farms"]
                        .select {
                            filter { eq("farmer_id", user.id) }
                        }.decodeList<FetchedFarm>()

                    withContext(Dispatchers.Main) {
                        savedFarmsDataMap.clear()
                        farms.forEachIndexed { index, farm ->
                            drawExistingFarm(farm, index + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun drawExistingFarm(farm: FetchedFarm, farmNumber: Int) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Double>>>() {}.type
            val latLngList: List<Map<String, Double>>? = Gson().fromJson(farm.coordinates, type)
            if (latLngList != null && latLngList.size >= 3) {
                val points = latLngList.map { Point.fromLngLat(it["lng"]!!, it["lat"]!!) }.toMutableList()
                points.add(points.first())
                val polygon = Polygon.fromLngLats(listOf(points))
                polygonAnnotationManager.create(
                    PolygonAnnotationOptions()
                        .withGeometry(polygon)
                        .withFillColor("#442196F3".toColorInt())
                        .withFillOutlineColor("#2196F3")
                )
                var sumLat = 0.0; var sumLng = 0.0
                latLngList.forEach { sumLat += it["lat"]!!; sumLng += it["lng"]!! }
                val textAnnotation = pointAnnotationManager.create(
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(sumLng / latLngList.size, sumLat / latLngList.size))
                        .withTextField(d(farmNumber))
                        .withTextSize(12.0)
                        .withTextColor("#FFFFFF")
                        .withTextHaloColor("#000000")
                        .withTextHaloWidth(1.0)
                )
                savedFarmsDataMap[textAnnotation.id] = farm
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startInstructionAnimation(view: View) {
        val lottieIcon = view.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.iconMapAnim)
        lottieIcon.renderMode = com.airbnb.lottie.RenderMode.HARDWARE
        lottieIcon.setCacheComposition(true)
        lottieIcon.setMinAndMaxFrame(1, 433)
        lottieIcon.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
        lottieIcon.repeatMode  = com.airbnb.lottie.LottieDrawable.RESTART
        lottieIcon.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationRepeat(a: android.animation.Animator) { lottieIcon.frame = 1 }
            override fun onAnimationStart(a: android.animation.Animator)  {}
            override fun onAnimationEnd(a: android.animation.Animator)    {}
            override fun onAnimationCancel(a: android.animation.Animator) {}
        })
        lottieIcon.playAnimation()
    }

    private fun centerMapOnCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveCamera(location.latitude, location.longitude)
            } else {
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setMaxUpdates(1).build()
                fusedLocationClient.requestLocationUpdates(req, object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { moveCamera(it.latitude, it.longitude) }
                    }
                }, Looper.getMainLooper())
            }
        }
    }

    private fun moveCamera(lat: Double, lng: Double) {
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder().center(Point.fromLngLat(lng, lat)).zoom(16.0).build()
        )
    }

    private fun addPoint(lat: Double, lng: Double, isManual: Boolean) {
        boundaryPoints.add(LatLng(lat, lng))
        val currentIndex = boundaryPoints.size - 1
        if (isManual) createCircleAtPoint(lat, lng, currentIndex)
        updatePolygon()
        calculateArea()
    }

    private fun createCircleAtPoint(lat: Double, lng: Double, index: Int) {
        if (::circleAnnotationManager.isInitialized) {
            val circleOptions = CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(lng, lat))
                .withCircleRadius(8.0)
                .withCircleColor("#FFEE58")
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#ffffff")
                .withDraggable(true)
            val annotation = circleAnnotationManager.create(circleOptions)
            circleIdToIndex[annotation.id] = index
        }
    }

    @SuppressLint("SetTextI18n")
    private fun undoLastPoint() {
        if (boundaryPoints.isEmpty()) return
        boundaryPoints.removeAt(boundaryPoints.size - 1)
        if (::circleAnnotationManager.isInitialized) circleAnnotationManager.deleteAll()
        circleIdToIndex.clear()
        boundaryPoints.forEachIndexed { index, latLng ->
            val annotation = circleAnnotationManager.create(
                CircleAnnotationOptions()
                    .withPoint(Point.fromLngLat(latLng.longitude, latLng.latitude))
                    .withCircleRadius(8.0)
                    .withCircleColor("#FFEE58")
                    .withCircleStrokeWidth(2.0)
                    .withCircleStrokeColor("#ffffff")
                    .withDraggable(true)
            )
            circleIdToIndex[annotation.id] = index
        }
        updatePolygon()
        calculateArea()
        if (boundaryPoints.size < 3) {
            tvCalculatedArea.text = "${d("0.00")} ${t("Acres")}"
            nextStepEnabled = false
        }
    }

    private fun updatePolygon() {
        activePolygonAnnotation?.let {
            polygonAnnotationManager.delete(it)
            activePolygonAnnotation = null
        }
        if (boundaryPoints.size >= 3) {
            val points = boundaryPoints.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
            points.add(points.first())
            activePolygonAnnotation = polygonAnnotationManager.create(
                PolygonAnnotationOptions()
                    .withGeometry(Polygon.fromLngLats(listOf(points)))
                    .withFillColor("#4400FF00".toColorInt())
                    .withFillOutlineColor("#00FF00")
            )
        }
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun calculateArea() {
        if (boundaryPoints.size >= 3) {
            val areaMeters = SphericalUtil.computeArea(boundaryPoints)
            lastCalculatedAreaAcres = areaMeters * 0.000247105
            val areaGuntas = lastCalculatedAreaAcres * 40
            tvCalculatedArea.text = if (lastCalculatedAreaAcres < 1.0) {
                "${d(String.format(Locale.US, "%.2f", areaGuntas))} ${t("Guntas")}"
            } else {
                "${d(String.format(Locale.US, "%.2f", lastCalculatedAreaAcres))} ${t("Acres")}"
            }
            nextStepEnabled = true
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) {
                    addPoint(loc.latitude, loc.longitude, false)
                    mapView.mapboxMap.setCamera(
                        CameraOptions.Builder().center(Point.fromLngLat(loc.longitude, loc.latitude)).build()
                    )
                }
            }
        }
    }

    private fun simplifyPath(points: List<LatLng>, toleranceMeters: Double): List<LatLng> {
        if (points.size < 3) return points
        var dmax = 0.0
        var index = 0
        val end = points.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }
        return if (dmax > toleranceMeters) {
            val left = simplifyPath(points.subList(0, index + 1), toleranceMeters)
            val right = simplifyPath(points.subList(index, end + 1), toleranceMeters)
            val result = left.toMutableList()
            result.removeAt(result.size - 1)
            result.addAll(right)
            result
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(pt: LatLng, lineStart: LatLng, lineEnd: LatLng): Double {
        val area   = abs((lineEnd.latitude - lineStart.latitude) * pt.longitude - (lineEnd.longitude - lineStart.longitude) * pt.latitude + lineEnd.longitude * lineStart.latitude - lineEnd.latitude * lineStart.longitude)
        val bottom = hypot(lineEnd.latitude - lineStart.latitude, lineEnd.longitude - lineStart.longitude)
        return if (bottom == 0.0) 0.0 else (area / bottom) * 111320.0
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        isTracking = true
        setWalkingState(true)
        resetMap()
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).setMinUpdateDistanceMeters(2f).build(),
            locationCallback, Looper.getMainLooper()
        )
    }

    private fun stopTracking() {
        isTracking = false
        setWalkingState(false)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (boundaryPoints.isNotEmpty()) {
            val simplified = simplifyPath(boundaryPoints, 2.5)
            boundaryPoints.clear()
            boundaryPoints.addAll(simplified)
            circleAnnotationManager.deleteAll()
            circleIdToIndex.clear()
            boundaryPoints.forEachIndexed { index, latLng ->
                createCircleAtPoint(latLng.latitude, latLng.longitude, index)
            }
        }
        updatePolygon()
        calculateArea()
    }

    @SuppressLint("SetTextI18n")
    private fun resetMap() {
        boundaryPoints.clear()
        circleIdToIndex.clear()
        if (::polygonAnnotationManager.isInitialized) {
            activePolygonAnnotation?.let { polygonAnnotationManager.delete(it); activePolygonAnnotation = null }
        }
        if (::circleAnnotationManager.isInitialized) circleAnnotationManager.deleteAll()
        tvCalculatedArea.text = "${d("0.00")} ${t("Acres")}"
        nextStepEnabled = false
    }

    @SuppressLint("Lifecycle")
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    @SuppressLint("Lifecycle")
    override fun onStop() {
        super.onStop()
        mapView.onStop()
        if (isTracking) stopTracking()
    }
    @SuppressLint("Lifecycle")
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}