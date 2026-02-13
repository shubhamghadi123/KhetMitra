package com.example.khetmitra

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.maps.android.SphericalUtil
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import androidx.core.view.isVisible
import androidx.core.graphics.toColorInt

class FieldMeasurementFragment : Fragment(R.layout.fragment_field_measurement) {

    private lateinit var mapView: MapView
    private lateinit var polygonAnnotationManager: PolygonAnnotationManager
    private lateinit var circleAnnotationManager: CircleAnnotationManager
    private val boundaryPoints = mutableListOf<LatLng>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false
    private var lastCalculatedAreaAcres: Double = 0.0

    private lateinit var tvCalculatedArea: TextView
    private lateinit var btnWalkBoundary: MaterialButton
    private lateinit var btnNextStep: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        mapView = view.findViewById(R.id.mapView)
        tvCalculatedArea = view.findViewById(R.id.tvCalculatedArea)
        btnWalkBoundary = view.findViewById(R.id.btnWalkBoundary)
        btnNextStep = view.findViewById(R.id.btnNextStep)
        val btnClearMap = view.findViewById<MaterialButton>(R.id.btnClearMap)
        val overlay = view.findViewById<View>(R.id.mapInstructionsOverlay)

        // Setup Overlay
        overlay.visibility = View.VISIBLE
        startInstructionAnimation(overlay)

        overlay.setOnClickListener {
            overlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    overlay.visibility = View.GONE
                    overlay.isClickable = false // Stop blocking touches
                }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        mapView.mapboxMap.loadStyle(Style.SATELLITE_STREETS) {
            // Initialize Annotation Managers
            val annotationApi = mapView.annotations
            polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            circleAnnotationManager = annotationApi.createCircleAnnotationManager()

            mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = true
            }

            centerMapOnCurrentLocation()

            // THE ONLY CLICK LISTENER YOU NEED
            mapView.mapboxMap.addOnMapClickListener { point ->
                // If instructions are still there, hide them on first tap
                if (overlay.isVisible) {
                    overlay.visibility = View.GONE
                    overlay.isClickable = false
                }

                // Logic for adding points
                if (!isTracking) {
                    addPoint(point.latitude(), point.longitude(), true)
                } else {
                    Toast.makeText(requireContext(), "Stop walking to tap manually", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        // Button Listeners
        btnWalkBoundary.setOnClickListener { if (isTracking) stopTracking() else startTracking() }
        btnClearMap.setOnClickListener { resetMap() }
        btnNextStep.setOnClickListener {
            val soilSheet = SoilBottomSheetFragment.newInstance(lastCalculatedAreaAcres)
            soilSheet.show(parentFragmentManager, "SoilSheet")
        }

        setupLocationCallback()
    }

    private fun startInstructionAnimation(view: View) {
        val lottieIcon = view.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.iconMapAnim)
        lottieIcon.setRenderMode(com.airbnb.lottie.RenderMode.HARDWARE)
        lottieIcon.setCacheComposition(true)
        lottieIcon.setMinAndMaxFrame(1, 433)

        lottieIcon.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
        lottieIcon.repeatMode = com.airbnb.lottie.LottieDrawable.RESTART

        lottieIcon.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: android.animation.Animator) {
                lottieIcon.frame = 1
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
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
                // If lastLocation is null, request a single update
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMaxUpdates(1)
                    .build()

                fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { moveCamera(it.latitude, it.longitude) }
                    }
                }, Looper.getMainLooper())
            }
        }
    }

    private fun moveCamera(lat: Double, lng: Double) {
        val cameraOptions = CameraOptions.Builder()
            .center(Point.fromLngLat(lng, lat))
            .zoom(16.0)
            .build()
        mapView.mapboxMap.setCamera(cameraOptions)
    }

    private fun addPoint(lat: Double, lng: Double, isManual: Boolean) {
        boundaryPoints.add(LatLng(lat, lng))

        if (isManual) {
            if (::circleAnnotationManager.isInitialized) {
                val circleOptions = CircleAnnotationOptions()
                    .withPoint(Point.fromLngLat(lng, lat))
                    .withCircleRadius(8.0) // Make it slightly larger for better visibility
                    .withCircleColor("#FFEE58") // Yellow
                    .withCircleStrokeWidth(2.0)
                    .withCircleStrokeColor("#ffffff")

                circleAnnotationManager.create(circleOptions)
            }
        }
        updatePolygon()
        calculateArea()
    }

    private fun updatePolygon() {
        polygonAnnotationManager.deleteAll()
        if (boundaryPoints.size >= 3) {
            val points = boundaryPoints.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
            points.add(points.first())
            val polygon = Polygon.fromLngLats(listOf(points))

            val polygonOptions = PolygonAnnotationOptions()
                .withGeometry(polygon)
                .withFillColor("#4400FF00".toColorInt())
                .withFillOutlineColor("#00FF00")

            polygonAnnotationManager.create(polygonOptions)
        }
    }

    private fun calculateArea() {
        if (boundaryPoints.size >= 3) {
            val areaMeters = SphericalUtil.computeArea(boundaryPoints)
            lastCalculatedAreaAcres = areaMeters * 0.000247105
            tvCalculatedArea.text = String.format("%.2f Acres", lastCalculatedAreaAcres)
            btnNextStep.isEnabled = true
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) {
                    addPoint(loc.latitude, loc.longitude, false)
                    mapView.mapboxMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(loc.longitude, loc.latitude)).build())
                }
            }
        }
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        isTracking = true
        btnWalkBoundary.text = "Stop Walking"
        btnWalkBoundary.setBackgroundColor(Color.RED)
        resetMap()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).setMinUpdateDistanceMeters(2f).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopTracking() {
        isTracking = false
        btnWalkBoundary.text = "Start Walking"
        btnWalkBoundary.setBackgroundColor(Color.parseColor("#2E7D32"))
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun resetMap() {
        boundaryPoints.clear()
        polygonAnnotationManager.deleteAll()
        circleAnnotationManager.deleteAll()
        tvCalculatedArea.text = "0.00 Acres"
        btnNextStep.isEnabled = false
    }

    override fun onStart() {
        super.onStart();
        mapView.onStart()
    }
    override fun onStop() {
        super.onStop();
        mapView.onStop();
        if (isTracking) stopTracking()
    }
    override fun onDestroy() {
        super.onDestroy();
        mapView.onDestroy()
    }
}