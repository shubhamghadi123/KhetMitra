package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private var selectedGender: String = ""
    private var photoUri: Uri? = null
    private var stateList: List<StateRow> = emptyList()
    private var districtList: List<DistrictRow> = emptyList()
    private val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var locationAutoSelectDone = false
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var btnRegister: MaterialCardView
    private lateinit var progressRegister: ProgressBar
    private lateinit var tvBtnRegisterLabel: TextView
    private lateinit var spinnerState: Spinner
    private lateinit var spinnerDistrict: Spinner
    private lateinit var spinnerIncome: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilFarmerId: TextInputLayout
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    private val englishIncomes = arrayOf(
        "Below ₹50,000", "₹50,000 - ₹1,00,000", "₹1,00,000 - ₹3,00,000", "Above ₹3,00,000"
    )

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && stateList.isNotEmpty()) fetchLocationAndAutoSelectState()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { photoUri = it; ivProfilePhoto.setImageURI(it) } }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) ivProfilePhoto.setImageURI(photoUri) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, t("Camera permission denied"), Toast.LENGTH_SHORT).show()
    }

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(num: String): String = TranslationHelper.convertDigits(num, currentLangCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        bindViews()
        if (currentLangCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHints()
        }
        setupProfilePhoto()
        setupTextFieldColors()
        setupGenderSelection()
        setupSpinnerColors()
        setupIncomeSpinner()
        setupDateSpinners()
        loadStatesFromSupabase()

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        btnRegister.setOnClickListener { registerFarmer() }
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun bindViews() {
        btnRegister        = findViewById(R.id.btnRegister)
        progressRegister   = findViewById(R.id.progressRegister)
        tvBtnRegisterLabel = btnRegister.findViewById(R.id.tvBtnRegisterLabel)
        spinnerState       = findViewById(R.id.spinnerState)
        spinnerDistrict    = findViewById(R.id.spinnerDistrict)
        spinnerIncome      = findViewById(R.id.spinnerIncome)
        spinnerYear        = findViewById(R.id.spinnerYear)
        tilFirstName       = findViewById(R.id.tilFirstName)
        tilLastName        = findViewById(R.id.tilLastName)
        tilPhone           = findViewById(R.id.tilPhone)
        tilPassword        = findViewById(R.id.tilPassword)
        tilEmail           = findViewById(R.id.tilEmail)
        tilFarmerId        = findViewById(R.id.tilFarmerId)
        ivProfilePhoto     = findViewById(R.id.ivProfilePhoto)
    }

    private fun setupProfilePhoto() {
        val btnPickPhoto = findViewById<MaterialCardView>(R.id.btnPickPhoto)
        btnPickPhoto.setOnClickListener { showPhotoPickerDialog() }
        ivProfilePhoto.setOnClickListener { showPhotoPickerDialog() }
    }

    private fun showPhotoPickerDialog() {
        AlertDialog.Builder(this)
            .setTitle(t("Upload Photo"))
            .setItems(arrayOf(
                "📷  ${t("Take a Photo")}",
                "🖼️  ${t("Choose from Gallery")}",
                "🗑️  ${t("Remove Photo")}"
            )) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
                    2 -> {
                        photoUri = null
                        when (selectedGender) {
                            "male" -> ivProfilePhoto.setImageResource(R.drawable.default_male_farmer)
                            "female" -> ivProfilePhoto.setImageResource(R.drawable.default_female_farmer)
                            "other" -> ivProfilePhoto.setImageResource(R.drawable.default_other_farmer)
                            else -> ivProfilePhoto.setImageResource(R.drawable.round_person_24)
                        }
                    }
                }
            }.show()
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    private fun setupTextFieldColors() {
        val greenColor  = "#52B788".toColorInt()
        val defaultGrey = "#E8EDE0".toColorInt()
        val bgColor     = "#FFFFFF".toColorInt()
        val strokeStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(greenColor, defaultGrey, greenColor)
        )
        listOf(tilFirstName, tilLastName, tilPhone, tilPassword, tilEmail, tilFarmerId).forEach { til ->
            til.setBoxBackgroundColor(bgColor)
            til.setBoxStrokeColorStateList(strokeStateList)
            til.boxStrokeColor = greenColor
        }
    }

    private fun genderTriples() = listOf(
        Triple(findViewById<MaterialCardView>(R.id.cardMale),   findViewById<TextView>(R.id.tvMaleLabel),   "male"),
        Triple(findViewById<MaterialCardView>(R.id.cardFemale), findViewById<TextView>(R.id.tvFemaleLabel), "female"),
        Triple(findViewById<MaterialCardView>(R.id.cardOther),  findViewById<TextView>(R.id.tvOtherLabel),  "other")
    )

    private fun resetGenderCards(
        cards: List<Triple<MaterialCardView, TextView, String>>,
        strokeColor: Int = "#E8EDE0".toColorInt(),
        bgColor: Int     = "#FFFFFF".toColorInt(),
        textColor: Int   = "#1A3C2E".toColorInt()
    ) {
        val strokePx = (1.5f * resources.displayMetrics.density).toInt()
        cards.forEach { (c, l, _) ->
            c.strokeColor = strokeColor
            c.strokeWidth = strokePx
            c.setCardBackgroundColor(bgColor)
            l.setTextColor(textColor)
        }
    }

    private fun applyGenderHighlight(
        card: MaterialCardView,
        label: TextView,
        strokeColor: Int = "#52B788".toColorInt(),
        bgColor: Int = "#F0FAF5".toColorInt(),
        textColor: Int = "#52B788".toColorInt()
    ) {
        val strokePx = (2f * resources.displayMetrics.density).toInt()
        card.strokeColor = strokeColor
        card.strokeWidth = strokePx
        card.setCardBackgroundColor(bgColor)
        label.setTextColor(textColor)
    }

    private fun setupGenderSelection() {
        val greenColor = "#52B788".toColorInt()
        val whiteColor = "#FFFFFF".toColorInt()
        val darkTextColor = "#1A3C2E".toColorInt()
        resetGenderCards(genderTriples(), greenColor, whiteColor, darkTextColor)
        genderTriples().forEach { (card, label, gender) ->
            card.setOnClickListener {
                resetGenderCards(genderTriples(), greenColor, whiteColor, darkTextColor)
                applyGenderHighlight(card, label)
                selectedGender = gender
                if (photoUri == null) {
                    when (selectedGender) {
                        "male" -> ivProfilePhoto.setImageResource(R.drawable.default_male_farmer)
                        "female" -> ivProfilePhoto.setImageResource(R.drawable.default_female_farmer)
                        "other" -> ivProfilePhoto.setImageResource(R.drawable.default_other_farmer)
                        else -> ivProfilePhoto.setImageResource(R.drawable.round_person_24)
                    }
                }
            }
        }
    }

    private fun setupSpinnerColors() {
        val greenColor = "#52B788".toColorInt()
        listOf(
            R.id.spinnerDay, R.id.spinnerMonth, R.id.spinnerYear,
            R.id.spinnerState, R.id.spinnerDistrict, R.id.spinnerIncome
        ).forEach { id ->
            val spinner = findViewById<Spinner>(id)
            val parentCard = spinner.parent as? MaterialCardView
            parentCard?.strokeColor = greenColor
        }
    }

    private fun setupDateSpinners() {
        findViewById<Spinner>(R.id.spinnerDay).applyCustomStyle(
            (1..31).map { d(it.toString()) }
        )
        findViewById<Spinner>(R.id.spinnerMonth).applyCustomStyle(
            listOf(
                t("Jan"), t("Feb"), t("Mar"), t("Apr"), t("May"), t("Jun"),
                t("Jul"), t("Aug"), t("Sep"), t("Oct"), t("Nov"), t("Dec")
            )
        )
        spinnerYear.applyCustomStyle(
            (1940..currentYear).map { d(it.toString()) }.reversed()
        )
    }

    private fun setupIncomeSpinner() {
        spinnerIncome.applyCustomStyle(
            englishIncomes.map { d(t(it)) }
        )
    }

    private fun loadStatesFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["states"]
                    .select { filter { eq("status", 1) }; order("state_name", Order.ASCENDING) }
                    .decodeList<StateRow>()
                val translatedStates = result.map { state ->
                    async { translateDynamicText(state.stateName) }
                }.awaitAll()
                withContext(Dispatchers.Main) {
                    stateList = result
                    spinnerState.applyCustomStyle(translatedStates)
                    spinnerState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            loadDistrictsForState(stateList[position].stateId)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    if (ContextCompat.checkSelfPermission(this@RegisterActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fetchLocationAndAutoSelectState()
                    }
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Failed to load states: ${e.message}")
                withContext(Dispatchers.Main) { Toast.makeText(this@RegisterActivity, t("Failed to load states"), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun loadDistrictsForState(stateId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["districts"]
                    .select { filter { eq("state_id", stateId); eq("status", 1) }; order("district_name", Order.ASCENDING) }
                    .decodeList<DistrictRow>()
                val translatedDistricts = result.map { district ->
                    async { translateDynamicText(district.districtName) }
                }.awaitAll()
                withContext(Dispatchers.Main) {
                    districtList = result
                    spinnerDistrict.applyCustomStyle(translatedDistricts)
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Failed to load districts: ${e.message}")
                withContext(Dispatchers.Main) { Toast.makeText(this@RegisterActivity, t("Failed to load districts"), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndAutoSelectState() {
        if (locationAutoSelectDone || stateList.isEmpty()) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                processLocationForState(location)
            } else {
                getLastKnownLocationForState()
            }
        }.addOnFailureListener {
            getLastKnownLocationForState()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocationForState() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                processLocationForState(location)
            }
        }.addOnFailureListener {
            Log.e("RegisterActivity", "Both current and last location failed.")
        }
    }

    private fun processLocationForState(location: android.location.Location) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val addresses = Geocoder(this@RegisterActivity, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val detectedState = addresses[0].adminArea
                    if (detectedState != null) {
                        withContext(Dispatchers.Main) {
                            selectStateInSpinner(detectedState)
                            locationAutoSelectDone = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Geocoder failed: ${e.message}")
            }
        }
    }

    private fun selectStateInSpinner(detectedState: String) {
        val index = stateList.indexOfFirst {
            it.stateName.equals(detectedState, ignoreCase = true) || detectedState.contains(it.stateName, ignoreCase = true)
        }
        if (index >= 0) spinnerState.setSelection(index)
    }

    private fun Spinner.applyCustomStyle(items: List<String>) {
        val adapter = ArrayAdapter(context, R.layout.custom_spinner_item, items)
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        this.adapter = adapter
        this.setPopupBackgroundResource(R.drawable.bg_spinner_dropdown)
    }

    private fun translateHints() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        mapOf(
            tilFirstName to "First Name",
            tilLastName  to "Last Name",
            tilPhone     to "Phone Number",
            tilPassword  to "Password",
            tilEmail     to "Email",
            tilFarmerId  to "Government Farmer ID"
        ).forEach { (til, hint) -> til.hint = t(hint) }
    }

    private fun translateScreenInstant(view: View) {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        if (view is TextView) {
            val text = view.text.toString()
            if (text.isNotEmpty()) view.text = t(text)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) translateScreenInstant(view.getChildAt(i))
        }
    }

    private suspend fun translateDynamicText(text: String): String =
        suspendCancellableCoroutine { continuation ->
            if (currentLangCode == TranslateLanguage.ENGLISH) {
                continuation.resumeWith(Result.success(text))
                return@suspendCancellableCoroutine
            }
            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(currentLangCode)
                .build()
            val client = com.google.mlkit.nl.translate.Translation.getClient(options)
            client.downloadModelIfNeeded().addOnSuccessListener {
                client.translate(text)
                    .addOnSuccessListener { result -> continuation.resumeWith(Result.success(result)) }
                    .addOnFailureListener { continuation.resumeWith(Result.success(text)) }
            }.addOnFailureListener { continuation.resumeWith(Result.success(text)) }
        }

    private fun registerFarmer() {
        val firstName = findViewById<TextInputEditText>(R.id.etFirstName).text.toString().trim()
        val lastName  = findViewById<TextInputEditText>(R.id.etLastName).text.toString().trim()
        val phone     = findViewById<TextInputEditText>(R.id.etPhone).text.toString().trim()
        val email     = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        val password  = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
        val farmerId  = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString().trim()
        val dayEnglish   = (findViewById<Spinner>(R.id.spinnerDay).selectedItemPosition + 1).toString()
        val monthIndex   = findViewById<Spinner>(R.id.spinnerMonth).selectedItemPosition
        val yearEnglish  = (currentYear - spinnerYear.selectedItemPosition).toString()
        val statePos     = spinnerState.selectedItemPosition
        val districtPos  = spinnerDistrict.selectedItemPosition
        val incomePos    = spinnerIncome.selectedItemPosition
        val stateEnglish    = if (statePos in stateList.indices) stateList[statePos].stateName else "Unknown"
        val districtEnglish = if (districtPos in districtList.indices) districtList[districtPos].districtName else "Unknown"
        val incomeEnglish   = if (incomePos >= 0) englishIncomes[incomePos] else "Unknown"
        var hasError = false
        fun TextInputLayout.require(value: String, msg: String): Boolean {
            return if (value.isEmpty()) { error = t(msg); hasError = true; false } else { error = null; true }
        }
        tilFirstName.require(firstName, "First name is required")
        tilLastName.require(lastName, "Last name is required")
        tilEmail.require(email, "Email is required")
        tilPassword.require(password, "Password is required")
        tilPhone.require(phone, "Phone number is required")
        if (phone.isNotEmpty() && phone.length < 10) { tilPhone.error = t("Enter a valid 10-digit phone number"); hasError = true }
        if (password.isNotEmpty()) {
            when {
                password.length <= 6 -> { tilPassword.error = t("Password must be more than 6 characters"); hasError = true }
                !password.any { it.isLetter() } || !password.any { it.isDigit() } -> { tilPassword.error = t("Password must include letters and numbers"); hasError = true }
                else -> tilPassword.error = null
            }
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { tilEmail.error = t("Enter a valid email address"); hasError = true }
        if (selectedGender.isEmpty()) { Toast.makeText(this, t("Please select a gender"), Toast.LENGTH_SHORT).show(); hasError = true }
        if (districtList.isEmpty()) { Toast.makeText(this, t("Please wait for districts to load"), Toast.LENGTH_SHORT).show(); hasError = true }
        if (hasError) return
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                    this.email = email; this.password = password
                }
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    try {
                        SupabaseManager.client.auth.updateUser {
                            @Suppress("SetTextI18n")
                            this.phone = if (phone.startsWith("+")) phone else "+91$phone"
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        throw Exception("This phone number is already linked to another account.")
                    }
                    var photoUrl: String? = null
                    photoUri?.let { uri ->
                        try {
                            val bytes = contentResolver.openInputStream(uri)?.readBytes()
                            if (bytes != null) {
                                val path = "${user.id}.jpg"
                                SupabaseManager.client.storage["profile-photos"].upload(path, bytes, upsert = true)
                                photoUrl = SupabaseManager.client.storage["profile-photos"].publicUrl(path)
                            }
                        } catch (e: Exception) { Log.e("RegisterActivity", "Photo upload failed: ${e.message}") }
                    }
                    SupabaseManager.client.postgrest["farmers"].insert(
                        FarmerProfile(
                            id                  = user.id,
                            first_name          = firstName,
                            last_name           = lastName,
                            phone_number        = phone,
                            email               = email,
                            gov_farmer_id       = farmerId.ifEmpty { null },
                            date_of_birth       = formatDob(dayEnglish, monthIndex, yearEnglish),
                            state_location      = stateEnglish,
                            district            = districtEnglish,
                            annual_income_range = incomeEnglish,
                            gender              = selectedGender,
                            profile_photo_url   = photoUrl
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, t("Registration Successful!"), Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        Toast.makeText(this@RegisterActivity, t("Login blocked. Check Supabase settings."), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    val err = e.message?.lowercase() ?: ""
                    if (err.contains("already registered") || err.contains("already exists") || (err.contains("in use") && !err.contains("email"))) {
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java).putExtra("REGISTERED_PHONE", phone))
                        finish()
                    } else {
                        Log.e("SupabaseError", "Registration Error: ", e)
                        Toast.makeText(this@RegisterActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        btnRegister.isClickable = !loading
        progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
        tvBtnRegisterLabel.text = if (loading) t("Registering...") else t("Complete Registration")
        btnRegister.setCardBackgroundColor(if (loading) "#2D6A4F".toColorInt() else "#52B788".toColorInt())
    }

    private fun formatDob(day: String, monthIndex: Int, year: String) =
        "$year-${(monthIndex + 1).toString().padStart(2, '0')}-${day.padStart(2, '0')}"
}