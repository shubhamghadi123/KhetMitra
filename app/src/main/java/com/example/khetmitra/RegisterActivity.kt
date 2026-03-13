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
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private var selectedGender: String = ""
    private var photoUri: Uri? = null
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var btnRegister: MaterialCardView
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var spinnerState: Spinner
    private lateinit var spinnerDistrict: Spinner
    private lateinit var spinnerIncome: Spinner
    private var stateList: List<StateRow> = emptyList()
    private var districtList: List<DistrictRow> = emptyList()

    private val englishIncomes = arrayOf(
        "Below ₹50,000", "₹50,000 - ₹1,00,000", "₹1,00,000 - ₹3,00,000", "Above ₹3,00,000"
    )

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) fetchLocationAndAutoSelectState() }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUri = it
            ivProfilePhoto.setImageURI(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) ivProfilePhoto.setImageURI(photoUri)
    }

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

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHints()
        }

        btnRegister     = findViewById(R.id.btnRegister)
        spinnerState    = findViewById(R.id.spinnerState)
        spinnerDistrict = findViewById(R.id.spinnerDistrict)
        spinnerIncome   = findViewById(R.id.spinnerIncome)

        setupProfilePhoto()
        setupTextFieldColors()
        setupGenderSelection()
        setupIncomeSpinner()
        setupDateSpinners()
        loadStatesFromSupabase()

        fusedLocationClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndAutoSelectState()
        } else {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        btnRegister.setOnClickListener { registerFarmer() }
    }

    private fun setupProfilePhoto() {
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        val btnPickPhoto = findViewById<MaterialCardView>(R.id.btnPickPhoto)

        btnPickPhoto.setOnClickListener { showPhotoPickerDialog() }
        ivProfilePhoto.setOnClickListener { showPhotoPickerDialog() }
    }

    private fun showPhotoPickerDialog() {
        AlertDialog.Builder(this)
            .setTitle(t("Upload Photo"))
            .setItems(arrayOf("📷  ${t("Take a Photo")}", "🖼️  ${t("Choose from Gallery")}")) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    private fun setupTextFieldColors() {
        val greenColor = "#52B788".toColorInt()
        val defaultGrey = "#E8EDE0".toColorInt()
        val bgColor = "#FFFFFF".toColorInt()

        val strokeColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(
                greenColor,
                defaultGrey
            )
        )

        val textInputIds = listOf(
            R.id.etFirstName, R.id.etLastName, R.id.etPhone,
            R.id.etPassword, R.id.etEmail, R.id.etFarmerId
        )

        textInputIds.forEach { id ->
            val editText = findViewById<TextInputEditText>(id)
            val textInputLayout = editText?.parent?.parent as? com.google.android.material.textfield.TextInputLayout

            textInputLayout?.let { til ->
                val blackColor = "#000000".toColorInt()
                til.setBoxBackgroundColor(bgColor)
                til.setBoxStrokeColorStateList(strokeColorStateList)
                til.defaultHintTextColor = ColorStateList.valueOf(blackColor)
            }
        }
    }

    private fun setupGenderSelection() {
        val cardMale   = findViewById<MaterialCardView>(R.id.cardMale)
        val cardFemale = findViewById<MaterialCardView>(R.id.cardFemale)
        val cardOther  = findViewById<MaterialCardView>(R.id.cardOther)
        val tvMale     = findViewById<TextView>(R.id.tvMaleLabel)
        val tvFemale   = findViewById<TextView>(R.id.tvFemaleLabel)
        val tvOther    = findViewById<TextView>(R.id.tvOtherLabel)

        val cards = listOf(
            Triple(cardMale,   tvMale,   "male"),
            Triple(cardFemale, tvFemale, "female"),
            Triple(cardOther,  tvOther,  "other")
        )

        val defaultStrokePx = (1.5f * resources.displayMetrics.density).toInt()
        val activeStrokePx = (2f * resources.displayMetrics.density).toInt()

        cards.forEach { (card, label, gender) ->
            card.setOnClickListener {
                cards.forEach { (c, l, _) ->
                    c.strokeColor = "#E8EDE0".toColorInt()
                    c.strokeWidth = defaultStrokePx
                    c.setCardBackgroundColor("#FFFFFF".toColorInt())
                    l.setTextColor("#1A3C2E".toColorInt())
                }

                card.strokeColor = "#52B788".toColorInt()
                card.strokeWidth = activeStrokePx
                card.setCardBackgroundColor("#F0FAF5".toColorInt())
                label.setTextColor("#52B788".toColorInt())
                selectedGender = gender
            }
        }
    }

    private fun loadStatesFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["states"]
                    .select {
                        filter { eq("status", 1) }
                        order("state_name", Order.ASCENDING)
                    }
                    .decodeList<StateRow>()

                withContext(Dispatchers.Main) {
                    stateList = result
                    spinnerState.adapter = ArrayAdapter(
                        this@RegisterActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        result.map { t(it.stateName) }
                    )
                    spinnerState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            loadDistrictsForState(stateList[position].stateId)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    fetchLocationAndAutoSelectState()
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Failed to load states: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, t("Failed to load states"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadDistrictsForState(stateId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["districts"]
                    .select {
                        filter {
                            eq("state_id", stateId)
                            eq("status", 1)
                        }
                        order("district_name", Order.ASCENDING)
                    }
                    .decodeList<DistrictRow>()

                withContext(Dispatchers.Main) {
                    districtList = result
                    spinnerDistrict.adapter = ArrayAdapter(
                        this@RegisterActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        result.map { t(it.districtName) }
                    )
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Failed to load districts: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, t("Failed to load districts"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchLocationAndAutoSelectState() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        if (stateList.isEmpty()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val addresses = Geocoder(this@RegisterActivity, Locale.getDefault())
                            .getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val detectedState = addresses[0].adminArea
                            if (detectedState != null) {
                                withContext(Dispatchers.Main) { selectStateInSpinner(detectedState) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RegisterActivity", "Geocoder failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun selectStateInSpinner(detectedState: String) {
        val index = stateList.indexOfFirst {
            it.stateName.equals(detectedState, ignoreCase = true) ||
                    detectedState.contains(it.stateName, ignoreCase = true)
        }
        if (index >= 0) spinnerState.setSelection(index)
    }

    private fun setupDateSpinners() {
        findViewById<Spinner>(R.id.spinnerDay).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            (1..31).map { d(it.toString()) }.toTypedArray()
        )
        findViewById<Spinner>(R.id.spinnerMonth).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf(t("Jan"), t("Feb"), t("Mar"), t("Apr"), t("May"), t("Jun"),
                t("Jul"), t("Aug"), t("Sep"), t("Oct"), t("Nov"), t("Dec"))
        )
        findViewById<Spinner>(R.id.spinnerYear).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            (1940..2026).map { d(it.toString()) }.reversed().toTypedArray()
        )
    }

    private fun setupIncomeSpinner() {
        spinnerIncome.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            englishIncomes.map { d(t(it)) }.toTypedArray()
        )
    }

    private fun translateHints() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        mapOf(
            R.id.etFirstName to "First Name", R.id.etLastName to "Last Name",
            R.id.etPhone to "Phone Number",   R.id.etPassword to "Password",
            R.id.etEmail to "Email",          R.id.etFarmerId to "Government Farmer ID"
        ).forEach { (id, hint) ->
            val et = findViewById<TextInputEditText>(id)
            (et?.parent?.parent as? com.google.android.material.textfield.TextInputLayout)?.hint = t(hint)
        }
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

    @SuppressLint("SetTextI18n")
    private fun registerFarmer() {
        val firstName = findViewById<TextInputEditText>(R.id.etFirstName).text.toString().trim()
        val lastName  = findViewById<TextInputEditText>(R.id.etLastName).text.toString().trim()
        val phone     = findViewById<TextInputEditText>(R.id.etPhone).text.toString().trim()
        val email     = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        val password  = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
        val farmerId  = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString().trim()

        val dayEnglish  = (findViewById<Spinner>(R.id.spinnerDay).selectedItemPosition + 1).toString()
        val monthIndex  = findViewById<Spinner>(R.id.spinnerMonth).selectedItemPosition
        val yearEnglish = (2026 - findViewById<Spinner>(R.id.spinnerYear).selectedItemPosition).toString()

        val statePos    = spinnerState.selectedItemPosition
        val districtPos = spinnerDistrict.selectedItemPosition
        val incomePos   = spinnerIncome.selectedItemPosition

        val stateEnglish    = if (statePos in stateList.indices) stateList[statePos].stateName else "Unknown"
        val districtEnglish = if (districtPos in districtList.indices) districtList[districtPos].districtName else "Unknown"
        val incomeEnglish   = if (incomePos >= 0) englishIncomes[incomePos] else "Unknown"

        if (firstName.isEmpty() || lastName.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, t("Please fill all required fields"), Toast.LENGTH_SHORT).show(); return
        }
        if (phone.length < 10) {
            Toast.makeText(this, t("Please enter a valid 10-digit phone number"), Toast.LENGTH_SHORT).show(); return
        }
        if (password.length <= 6) {
            Toast.makeText(this, t("Password must be more than 6 characters long"), Toast.LENGTH_SHORT).show(); return
        }
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            Toast.makeText(this, t("Password must include both letters and numbers"), Toast.LENGTH_SHORT).show(); return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, t("Please enter a valid email format"), Toast.LENGTH_SHORT).show(); return
        }

        val tvBtnLabel = btnRegister.findViewById<TextView>(R.id.tvBtnRegisterLabel)
        btnRegister.isClickable = false
        tvBtnLabel.text = t("Registering...")
        btnRegister.setCardBackgroundColor("#2D6A4F".toColorInt())

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseManager.client.auth.signUpWith(
                    io.github.jan.supabase.gotrue.providers.builtin.Email
                ) {
                    this.email = email
                    this.password = password
                }

                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    try {
                        SupabaseManager.client.auth.updateUser {
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
                                SupabaseManager.client.storage["profile-photos"]
                                    .upload(path, bytes, upsert = true)
                                photoUrl = SupabaseManager.client.storage["profile-photos"]
                                    .publicUrl(path)
                            }
                        } catch (e: Exception) {
                            Log.e("RegisterActivity", "Photo upload failed: ${e.message}")
                        }
                    }

                    SupabaseManager.client.postgrest["farmers"].insert(
                        FarmerProfile(
                            id = user.id,
                            first_name = firstName,
                            last_name = lastName,
                            phone_number = phone,
                            email = email,
                            gov_farmer_id = farmerId.ifEmpty { null },
                            date_of_birth = formatDob(dayEnglish, monthIndex, yearEnglish),
                            state_location = stateEnglish,
                            district = districtEnglish,
                            annual_income_range = incomeEnglish,
                            gender = selectedGender,
                            profile_photo_url = photoUrl
                        )
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, t("Registration Successful!"), Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        btnRegister.isClickable = true
                        tvBtnLabel.text = t("Complete Registration")
                        btnRegister.setCardBackgroundColor("#52B788".toColorInt())
                        Toast.makeText(this@RegisterActivity, t("Login blocked. Check Supabase settings."), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    btnRegister.isClickable = true
                    tvBtnLabel.text = t("Complete Registration")
                    btnRegister.setCardBackgroundColor("#52B788".toColorInt())
                    val err = e.message?.lowercase() ?: ""
                    if (err.contains("already registered") || err.contains("already exists") ||
                        (err.contains("in use") && !err.contains("email"))) {
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java)
                            .putExtra("REGISTERED_PHONE", phone))
                        finish()
                    } else {
                        Log.e("SupabaseError", "Registration Error: ", e)
                        Toast.makeText(this@RegisterActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun formatDob(day: String, monthIndex: Int, year: String) =
        "$year-${(monthIndex + 1).toString().padStart(2, '0')}-${day.padStart(2, '0')}"
}