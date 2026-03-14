package com.example.khetmitra

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import java.net.URL
import java.util.Calendar

class ProfileActivity : AppCompatActivity() {
    private var isEditMode = false
    private var langCode: String = TranslateLanguage.ENGLISH
    private var selectedGender: String = ""
    private var photoUri: Uri? = null
    private var currentPhotoUrl: String? = null
    private var stateList: List<StateRow> = emptyList()
    private var districtList: List<DistrictRow> = emptyList()
    private val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var spinnerDay: Spinner
    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var spinnerIncome: Spinner
    private lateinit var spinnerState: Spinner
    private lateinit var spinnerDistrict: Spinner
    private val englishDays   = (1..31).map { it.toString() }.toTypedArray()
    private val englishYears  = (1940..currentYear).map { it.toString() }.reversed().toTypedArray()
    private val englishIncomes = arrayOf("Below ₹50,000", "₹50,000 - ₹1,00,000", "₹1,00,000 - ₹3,00,000", "Above ₹3,00,000")
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
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        bindViews()
        if (langCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHints()
        }
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialCardView>(R.id.btnEditProfile).setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
                photoUri = null
                fetchProfileFromSupabase()
                Toast.makeText(this, t("Edit Mode Disabled. Unsaved changes reverted."), Toast.LENGTH_SHORT).show()
            } else {
                toggleEditMode(true)
                Toast.makeText(this, t("Edit Mode Enabled"), Toast.LENGTH_SHORT).show()
            }
        }
        setupProfilePhoto()
        setupGenderSelection()
        setupDateSpinners()
        setupIncomeSpinner()
        loadStatesFromSupabase()
        toggleEditMode(false)
        findViewById<MaterialCardView>(R.id.btnSaveProfile).setOnClickListener {
            saveProfileToSupabase()
        }
    }

    private fun bindViews() {
        ivProfilePhoto  = findViewById(R.id.ivProfilePhoto)
        spinnerDay      = findViewById(R.id.spinnerDay)
        spinnerMonth    = findViewById(R.id.spinnerMonth)
        spinnerYear     = findViewById(R.id.spinnerYear)
        spinnerIncome   = findViewById(R.id.spinnerIncome)
        spinnerState    = findViewById(R.id.spinnerState)
        spinnerDistrict = findViewById(R.id.spinnerDistrict)
    }

    private fun Spinner.applyCustomStyle(items: List<String>) {
        val adapter = ArrayAdapter(context, R.layout.custom_spinner_item, items)
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        this.adapter = adapter
        this.setPopupBackgroundResource(R.drawable.bg_spinner_dropdown)
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
                    .select {
                        filter { eq("status", 1) }
                        order("state_name", Order.ASCENDING)
                    }.decodeList<StateRow>()

                val translatedStates = result.map { state ->
                    async { translateDynamicText(state.stateName) }
                }.awaitAll()
                withContext(Dispatchers.Main) {
                    stateList = result
                    spinnerState.applyCustomStyle(translatedStates)
                    spinnerState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position in stateList.indices) {
                                loadDistrictsForState(stateList[position].stateId)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    fetchProfileFromSupabase()
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Failed to load states: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, t("Failed to load states"), Toast.LENGTH_SHORT).show()
                    fetchProfileFromSupabase()
                }
            }
        }
    }

    private fun loadDistrictsForState(stateId: Int, preselectDistrict: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["districts"]
                    .select {
                        filter { eq("state_id", stateId); eq("status", 1) }
                        order("district_name", Order.ASCENDING)
                    }.decodeList<DistrictRow>()

                val translatedDistricts = result.map { district ->
                    async { translateDynamicText(district.districtName) }
                }.awaitAll()
                withContext(Dispatchers.Main) {
                    districtList = result
                    spinnerDistrict.applyCustomStyle(translatedDistricts)
                    if (preselectDistrict != null) {
                        val index = districtList.indexOfFirst { it.districtName.equals(preselectDistrict, ignoreCase = true) }
                        if (index >= 0) spinnerDistrict.setSelection(index)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Failed to load districts: ${e.message}")
            }
        }
    }

    private fun setupProfilePhoto() {
        findViewById<MaterialCardView>(R.id.btnPickPhoto)?.setOnClickListener {
            if (isEditMode) showPhotoPickerDialog()
        }
        ivProfilePhoto.setOnClickListener { if (isEditMode) showPhotoPickerDialog() }
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
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
                    2 -> {
                        photoUri = null
                        currentPhotoUrl = ""
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
        val f = File(cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", f)
        photoUri = uri; cameraLauncher.launch(uri)
    }

    private fun loadProfilePhoto(url: String?) {
        if (url.isNullOrBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bm = BitmapFactory.decodeStream(
                    URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }.getInputStream()
                )
                withContext(Dispatchers.Main) { ivProfilePhoto.setImageBitmap(bm) }
            } catch (e: Exception) { Log.e("ProfileActivity", "Photo load failed", e) }
        }
    }

    private fun setupGenderSelection() {
        genderTriples().forEach { (card, label, gender) ->
            card.setOnClickListener {
                if (!isEditMode) return@setOnClickListener
                resetGenderCards(genderTriples(), "#52B788".toColorInt(), "#FFFFFF".toColorInt(), "#1A3C2E".toColorInt())
                applyGenderHighlight(card, label)
                selectedGender = gender
                if (photoUri == null && currentPhotoUrl.isNullOrEmpty()) {
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

    private fun highlightGender(gender: String?) {
        if (gender.isNullOrBlank()) return
        selectedGender = gender.lowercase()
        val greenColor = "#52B788".toColorInt()
        val defaultGrey = "#E8EDE0".toColorInt()
        val activeBorderColor = if (isEditMode) greenColor else defaultGrey
        val unselectedTextColor = if (isEditMode) "#1A3C2E".toColorInt() else "#888888".toColorInt()
        resetGenderCards(genderTriples(), activeBorderColor, "#FFFFFF".toColorInt(), unselectedTextColor)
        genderTriples().find { it.third == selectedGender }?.let { (c, l, _) -> applyGenderHighlight(c, l) }
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

    private fun toggleEditMode(enabled: Boolean) {
        isEditMode = enabled

        val greenColor = "#52B788".toColorInt()
        val defaultGrey = "#E8EDE0".toColorInt()
        val bgColor = "#FFFFFF".toColorInt()
        val activeBorderColor = if (enabled) greenColor else defaultGrey

        val strokeColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(greenColor, defaultGrey, activeBorderColor)
        )

        listOf(R.id.etFirstName, R.id.etLastName, R.id.etPhone, R.id.etEmail, R.id.etFarmerId).forEach { id ->
            val view = findViewById<TextInputEditText>(id)
            view.isEnabled = enabled
            view.isFocusableInTouchMode = enabled
            view.isCursorVisible = enabled

            (view.parent?.parent as? TextInputLayout)?.let { til ->
                til.isEnabled = enabled
                til.setBoxBackgroundColor(bgColor)
                til.setBoxStrokeColorStateList(strokeColorStateList)
                til.boxStrokeColor = activeBorderColor
            }
        }

        listOf(spinnerState, spinnerDistrict, spinnerDay, spinnerMonth, spinnerYear, spinnerIncome).forEach { spinner ->
            spinner.isEnabled = enabled
            spinner.isClickable = enabled

            val parentCard = spinner.parent as? MaterialCardView
            parentCard?.strokeColor = activeBorderColor
        }

        val gt = genderTriples()
        val unselectedTextColor = if (enabled) "#1A3C2E".toColorInt() else "#888888".toColorInt()
        resetGenderCards(gt, activeBorderColor, bgColor, unselectedTextColor)
        gt.find { it.third == selectedGender }?.let { (c, l, _) ->
            applyGenderHighlight(c, l)
        }

        findViewById<MaterialCardView>(R.id.btnSaveProfile).visibility = if (enabled) View.VISIBLE else View.GONE
        findViewById<MaterialCardView>(R.id.btnPickPhoto)?.visibility = if (enabled) View.VISIBLE else View.GONE

        val editIcon = (findViewById<MaterialCardView>(R.id.btnEditProfile).getChildAt(0) as? android.widget.ImageView)
        editIcon?.setImageResource(if (enabled) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_edit)
    }

    private fun translateScreenInstant(view: View) {
        if (langCode == TranslateLanguage.ENGLISH) return
        if (view is TextView && view !is TextInputEditText) {
            val text = view.text.toString()
            if (text.isNotEmpty()) view.text = t(text)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) translateScreenInstant(view.getChildAt(i))
        }
    }

    private suspend fun translateDynamicText(text: String): String =
        suspendCancellableCoroutine { continuation ->
            if (langCode == TranslateLanguage.ENGLISH) {
                continuation.resumeWith(Result.success(text))
                return@suspendCancellableCoroutine
            }
            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(langCode)
                .build()
            val client = com.google.mlkit.nl.translate.Translation.getClient(options)
            client.downloadModelIfNeeded().addOnSuccessListener {
                client.translate(text)
                    .addOnSuccessListener { result -> continuation.resumeWith(Result.success(result)) }
                    .addOnFailureListener { continuation.resumeWith(Result.success(text)) }
            }.addOnFailureListener {
                continuation.resumeWith(Result.success(text))
            }
        }

    private fun translateHints() {
        if (langCode == TranslateLanguage.ENGLISH) return
        mapOf(
            R.id.etFirstName to "First Name",
            R.id.etLastName  to "Last Name",
            R.id.etPhone     to "Phone Number",
            R.id.etEmail     to "Email Address",
            R.id.etFarmerId  to "Government Farmer ID"
        ).forEach { (id, hint) ->
            val view = findViewById<View>(id)
            val til = view?.parent?.parent as? TextInputLayout
            til?.hint = t(hint)
            if (id == R.id.etPhone && til?.prefixText != null) {
                til.prefixText = d(til.prefixText.toString())
            }
        }
    }

    private fun fetchProfileFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
                val profile = SupabaseManager.client.postgrest["farmers"]
                    .select { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<FarmerProfile>()
                withContext(Dispatchers.Main) { if (profile != null) populateUI(profile) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, t("Failed to load: ") + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateUI(profile: FarmerProfile) {
        findViewById<TextInputEditText>(R.id.etFirstName).setText(profile.first_name)
        findViewById<TextInputEditText>(R.id.etLastName).setText(profile.last_name)
        findViewById<TextInputEditText>(R.id.etEmail).setText(profile.email)
        findViewById<TextInputEditText>(R.id.etPhone).setText(d(profile.phone_number))
        findViewById<TextInputEditText>(R.id.etFarmerId).setText(d(profile.gov_farmer_id ?: ""))
        val dob = profile.date_of_birth
        if (dob.isNotBlank()) {
            val parts = dob.split("-")
            if (parts.size == 3) {
                val year  = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                val day   = parts[2].toIntOrNull()
                if (day != null && day in 1..31) spinnerDay.setSelection(day - 1)
                if (month != null && month in 1..12) spinnerMonth.setSelection(month - 1)
                if (year != null) {
                    val yearIndex = englishYears.indexOf(year.toString())
                    if (yearIndex >= 0) spinnerYear.setSelection(yearIndex)
                }
            }
        }

        val incomeIndex = englishIncomes.indexOfFirst { it.equals(profile.annual_income_range, ignoreCase = true) }
        if (incomeIndex >= 0) spinnerIncome.setSelection(incomeIndex)
        val stateIndex = stateList.indexOfFirst { it.stateName.equals(profile.state_location, ignoreCase = true) }
        if (stateIndex >= 0) {
            spinnerState.setSelection(stateIndex)
            loadDistrictsForState(stateList[stateIndex].stateId, preselectDistrict = profile.district)
        }

        highlightGender(profile.gender)
        currentPhotoUrl = profile.profile_photo_url
        if (currentPhotoUrl.isNullOrEmpty()) {
            when (profile.gender.lowercase()) {
                "male" -> ivProfilePhoto.setImageResource(R.drawable.default_male_farmer)
                "female" -> ivProfilePhoto.setImageResource(R.drawable.default_female_farmer)
                "other" -> ivProfilePhoto.setImageResource(R.drawable.default_other_farmer)
                else -> ivProfilePhoto.setImageResource(R.drawable.round_person_24)
            }
        } else {
            loadProfilePhoto(currentPhotoUrl)
        }
    }

    private fun reverseTranslateDigits(input: String): String {
        var output = input
        for (i in 0..9) {
            output = output.replace(d(i.toString()), i.toString())
        }
        return output
    }

    private fun saveProfileToSupabase() {
        val btnSave    = findViewById<MaterialCardView>(R.id.btnSaveProfile)
        val tvBtnLabel = btnSave.findViewById<TextView>(R.id.tvBtnSaveLabel)
        val progress   = btnSave.findViewById<ProgressBar>(R.id.progressSave)
        btnSave.isClickable = false
        tvBtnLabel.text = t("Saving...")
        progress.visibility = View.VISIBLE
        btnSave.setCardBackgroundColor("#2D6A4F".toColorInt())
        val dayEnglish    = englishDays[spinnerDay.selectedItemPosition].padStart(2, '0')
        val monthEnglish  = (spinnerMonth.selectedItemPosition + 1).toString().padStart(2, '0')
        val yearEnglish   = englishYears[spinnerYear.selectedItemPosition]
        val dobEnglish    = "$yearEnglish-$monthEnglish-$dayEnglish"
        val incomeEnglish = englishIncomes[spinnerIncome.selectedItemPosition]
        val statePos      = spinnerState.selectedItemPosition
        val stateEnglish  = if (statePos in stateList.indices) stateList[statePos].stateName else ""
        val districtPos   = spinnerDistrict.selectedItemPosition
        val districtEnglish = if (districtPos in districtList.indices) districtList[districtPos].districtName else ""
        val phoneRaw    = findViewById<TextInputEditText>(R.id.etPhone).text.toString()
        val farmerIdRaw = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString()
        val phoneEnglish    = reverseTranslateDigits(phoneRaw)
        val farmerIdEnglish = reverseTranslateDigits(farmerIdRaw)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
                var photoUrl = currentPhotoUrl
                photoUri?.let { uri ->
                    try {
                        val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        if (bytes != null) {
                            val path = "${user.id}.jpg"
                            SupabaseManager.client.storage["profile-photos"].upload(path, bytes, upsert = true)
                            photoUrl = SupabaseManager.client.storage["profile-photos"].publicUrl(path)
                        }
                    } catch (e: Exception) { Log.e("ProfileActivity", "Photo upload failed: ${e.message}") }
                }
                SupabaseManager.client.postgrest["farmers"].upsert(
                    FarmerProfile(
                        id                  = user.id,
                        first_name          = findViewById<TextInputEditText>(R.id.etFirstName).text.toString().trim(),
                        last_name           = findViewById<TextInputEditText>(R.id.etLastName).text.toString().trim(),
                        phone_number        = phoneEnglish,
                        email               = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim(),
                        gov_farmer_id       = farmerIdEnglish.ifEmpty { null },
                        gender              = selectedGender,
                        profile_photo_url   = photoUrl,
                        date_of_birth       = dobEnglish,
                        state_location      = stateEnglish,
                        district            = districtEnglish,
                        annual_income_range = incomeEnglish
                    )
                )
                withContext(Dispatchers.Main) {
                    currentPhotoUrl = photoUrl; photoUri = null
                    Toast.makeText(this@ProfileActivity, t("Profile Updated!"), Toast.LENGTH_SHORT).show()

                    btnSave.isClickable = true
                    tvBtnLabel.text = t("Save Changes")
                    progress.visibility = View.GONE
                    btnSave.setCardBackgroundColor("#52B788".toColorInt())
                    toggleEditMode(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSave.isClickable = true
                    tvBtnLabel.text = t("Save Changes")
                    progress.visibility = View.GONE
                    btnSave.setCardBackgroundColor("#52B788".toColorInt())
                    Toast.makeText(this@ProfileActivity, t("Save failed: ") + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}