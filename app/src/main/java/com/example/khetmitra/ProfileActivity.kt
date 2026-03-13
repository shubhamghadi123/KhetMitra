package com.example.khetmitra

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class ProfileActivity : AppCompatActivity() {

    private var isEditMode = false
    private var langCode: String = TranslateLanguage.ENGLISH
    private var selectedGender: String = ""
    private var photoUri: Uri? = null
    private var currentPhotoUrl: String? = null
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var dropdownDay: AutoCompleteTextView
    private lateinit var dropdownMonth: AutoCompleteTextView
    private lateinit var dropdownYear: AutoCompleteTextView
    private lateinit var dropdownIncome: AutoCompleteTextView
    private var stateList: List<StateRow> = emptyList()
    private var districtList: List<DistrictRow> = emptyList()
    private val englishMonths = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec")
    private val englishDays   = (1..31).map { it.toString() }.toTypedArray()
    private val englishYears  = (1940..2026).map { it.toString() }.reversed().toTypedArray()
    private val englishIncomes = arrayOf(
        "Below ₹50,000", "₹50,000 - ₹1,00,000", "₹1,00,000 - ₹3,00,000", "Above ₹3,00,000"
    )

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

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }
    fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        if (langCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHints()
        }

        ivProfilePhoto  = findViewById(R.id.ivProfilePhoto)
        dropdownDay     = findViewById(R.id.dropdownDay)
        dropdownMonth   = findViewById(R.id.dropdownMonth)
        dropdownYear    = findViewById(R.id.dropdownYear)
        dropdownIncome  = findViewById(R.id.dropdownIncome)

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialCardView>(R.id.btnEditProfile).setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
                Toast.makeText(this, t("Edit Mode Disabled"), Toast.LENGTH_SHORT).show()
            } else {
                toggleEditMode(true)
                Toast.makeText(this, t("Edit Mode Enabled"), Toast.LENGTH_SHORT).show()
            }
        }

        setupProfilePhoto()
        setupGenderSelection()
        setupDobDropdowns()
        setupIncomeDropdown()
        loadStatesFromSupabase()
        toggleEditMode(false)

        findViewById<MaterialCardView>(R.id.btnSaveProfile).setOnClickListener {
            saveProfileToSupabase()
        }
    }

    private fun setupDobDropdowns() {
        dropdownDay.setAdapter(NoFilterAdapter(this, englishDays.map { d(it) }.toTypedArray()))
        dropdownMonth.setAdapter(NoFilterAdapter(this,
            englishMonths.map { t(it) }.toTypedArray()))
        dropdownYear.setAdapter(NoFilterAdapter(this, englishYears.map { d(it) }.toTypedArray()))
    }

    private fun setupIncomeDropdown() {
        dropdownIncome.setAdapter(
            NoFilterAdapter(this, englishIncomes.map { d(t(it)) }.toTypedArray()))
    }

    private fun loadStatesFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["states"]
                    .select {
                        filter { eq("status", 1) }
                        order("state_name", Order.ASCENDING)
                    }.decodeList<StateRow>()

                withContext(Dispatchers.Main) {
                    stateList = result
                    findViewById<AutoCompleteTextView>(R.id.dropdownState).setAdapter(
                        NoFilterAdapter(this@ProfileActivity, result.map { t(it.stateName) }.toTypedArray())
                    )
                    findViewById<AutoCompleteTextView>(R.id.dropdownState)
                        .setOnItemClickListener { _, _, pos, _ ->
                            if (pos in stateList.indices)
                                loadDistrictsForState(stateList[pos].stateId)
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

    private fun loadDistrictsForState(stateId: Int, preselect: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SupabaseManager.client.postgrest["districts"]
                    .select {
                        filter { eq("state_id", stateId); eq("status", 1) }
                        order("district_name", Order.ASCENDING)
                    }.decodeList<DistrictRow>()

                withContext(Dispatchers.Main) {
                    districtList = result
                    val dd = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict)
                    dd.setAdapter(ArrayAdapter(this@ProfileActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        result.map { t(it.districtName) }))
                    if (preselect != null) {
                        val match = result.find { it.districtName.equals(preselect, ignoreCase = true) }
                        if (match != null) dd.setText(t(match.districtName), false)
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
            .setItems(arrayOf("📷  ${t("Take a Photo")}", "🖼️  ${t("Choose from Gallery")}")) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
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
                    URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                        .getInputStream())
                withContext(Dispatchers.Main) { ivProfilePhoto.setImageBitmap(bm) }
            } catch (e: Exception) { Log.e("ProfileActivity", "Photo load failed", e) }
        }
    }

    private fun setupGenderSelection() {
        genderTriples().forEach { (card, label, gender) ->
            card.setOnClickListener {
                if (!isEditMode) return@setOnClickListener

                resetGenderCards(
                    cards = genderTriples(),
                    strokeColor = "#52B788".toColorInt(),
                    bgColor = "#FFFFFF".toColorInt(),
                    textColor = "#1A3C2E".toColorInt()
                )

                applyGenderHighlight(card, label)
                selectedGender = gender
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
        genderTriples().find { it.third == selectedGender }
            ?.let { (c, l, _) -> applyGenderHighlight(c, l) }
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

    private class NoFilterAdapter(ctx: Context, private val items: Array<String>) :
        ArrayAdapter<String>(ctx, android.R.layout.simple_dropdown_item_1line, items) {
        override fun getFilter() = object : android.widget.Filter() {
            override fun performFiltering(c: CharSequence?) =
                FilterResults().apply { values = items; count = items.size }
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(c: CharSequence?, r: FilterResults?) = notifyDataSetChanged()
        }
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
            intArrayOf(
                greenColor,
                defaultGrey,
                activeBorderColor
            )
        )

        listOf(R.id.etFirstName, R.id.etLastName, R.id.etPhone, R.id.etEmail,
            R.id.etFarmerId, R.id.dropdownState, R.id.dropdownDistrict,
            R.id.dropdownDay, R.id.dropdownMonth, R.id.dropdownYear,
            R.id.dropdownIncome).forEach { id ->

            val view = findViewById<View>(id)
            view.isEnabled = enabled
            view.isClickable = enabled
            view.isFocusable = enabled

            if (view is TextInputEditText) {
                view.isFocusableInTouchMode = enabled
                view.isCursorVisible = enabled
            }

            (view.parent?.parent as? TextInputLayout)?.let { til ->
                til.isEnabled = enabled
                til.setBoxBackgroundColor(bgColor)
                til.setBoxStrokeColorStateList(strokeColorStateList)
                til.boxStrokeColor = activeBorderColor
            }
        }

        val gt = genderTriples()
        val unselectedTextColor = if (enabled) "#1A3C2E".toColorInt() else "#888888".toColorInt()
        resetGenderCards(gt, activeBorderColor, bgColor, unselectedTextColor)
        gt.find { it.third == selectedGender }?.let { (c, l, _) ->
            applyGenderHighlight(c, l)
        }

        findViewById<MaterialCardView>(R.id.btnSaveProfile).visibility =
            if (enabled) View.VISIBLE else View.GONE
        findViewById<MaterialCardView>(R.id.btnPickPhoto)?.visibility =
            if (enabled) View.VISIBLE else View.GONE

        val editIcon = (findViewById<MaterialCardView>(R.id.btnEditProfile)
            .getChildAt(0) as? android.widget.ImageView)
        editIcon?.setImageResource(
            if (enabled) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_edit)
    }

    private fun translateScreenInstant(view: View) {
        if (langCode == TranslateLanguage.ENGLISH) return
        if (view is TextView && view !is TextInputEditText && view !is AutoCompleteTextView) {
            val text = view.text.toString()
            if (text.isNotEmpty()) view.text = t(text)
        }
        if (view is android.view.ViewGroup)
            for (i in 0 until view.childCount) translateScreenInstant(view.getChildAt(i))
    }

    private fun translateHints() {
        if (langCode == TranslateLanguage.ENGLISH) return
        mapOf(
            R.id.etFirstName      to "First Name",
            R.id.etLastName       to "Last Name",
            R.id.etPhone          to "Phone Number",
            R.id.etEmail          to "Email Address",
            R.id.etFarmerId       to "Government Farmer ID",
            R.id.dropdownState    to "State",
            R.id.dropdownDistrict to "District",
            R.id.dropdownDay      to "Day",
            R.id.dropdownMonth    to "Month",
            R.id.dropdownYear     to "Year",
            R.id.dropdownIncome   to "Annual Income"
        ).forEach { (id, hint) ->
            val view = findViewById<View>(id)
            val til = view?.parent?.parent as? TextInputLayout
            til?.hint = t(hint)
            if (id == R.id.etPhone && til?.prefixText != null)
                til.prefixText = d(til.prefixText.toString())
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
                if (day != null && day in 1..31)
                    dropdownDay.setText(d(day.toString()), false)
                if (month != null && month in 1..12)
                    dropdownMonth.setText(t(englishMonths[month - 1]), false)
                if (year != null)
                    dropdownYear.setText(d(year.toString()), false)
            }
        }

        val incomeMatch = englishIncomes.find {
            it.equals(profile.annual_income_range, ignoreCase = true)
        }
        if (incomeMatch != null)
            dropdownIncome.setText(d(t(incomeMatch)), false)

        val stateMatch = stateList.find { it.stateName.equals(profile.state_location, ignoreCase = true) }
        if (stateMatch != null) {
            findViewById<AutoCompleteTextView>(R.id.dropdownState).setText(t(stateMatch.stateName), false)
            loadDistrictsForState(stateMatch.stateId, preselect = profile.district)
        } else {
            findViewById<AutoCompleteTextView>(R.id.dropdownState).setText(t(profile.state_location), false)
        }

        highlightGender(profile.gender)
        currentPhotoUrl = profile.profile_photo_url
        loadProfilePhoto(currentPhotoUrl)
    }

    private fun saveProfileToSupabase() {
        val btnSave    = findViewById<MaterialCardView>(R.id.btnSaveProfile)
        val tvBtnLabel = btnSave.findViewById<TextView>(R.id.tvBtnSaveLabel)
        btnSave.isClickable = false
        tvBtnLabel.text = t("Saving...")
        btnSave.setCardBackgroundColor("#2D6A4F".toColorInt())
        var dayText   = dropdownDay.text.toString()
        var yearText  = dropdownYear.text.toString()
        val monthText = dropdownMonth.text.toString()
        for (i in 0..9) {
            val local = d(i.toString())
            dayText  = dayText.replace(local, i.toString())
            yearText = yearText.replace(local, i.toString())
        }
        val monthIndex = englishMonths.indexOfFirst { t(it) == monthText }
            .takeIf { it >= 0 } ?: 0
        val dobEnglish = "$yearText-${(monthIndex + 1).toString().padStart(2, '0')}-${dayText.padStart(2, '0')}"
        val incomeUI  = dropdownIncome.text.toString()
        val incomeEnglish = englishIncomes.find { d(t(it)) == incomeUI } ?: incomeUI
        val stateUI    = findViewById<AutoCompleteTextView>(R.id.dropdownState).text.toString()
        val districtUI = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict).text.toString()
        val stateEnglish    = stateList.find { t(it.stateName) == stateUI }?.stateName ?: stateUI
        val districtEnglish = districtList.find { t(it.districtName) == districtUI }?.districtName ?: districtUI
        var phoneEnglish    = findViewById<TextInputEditText>(R.id.etPhone).text.toString()
        var farmerIdEnglish = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString()
        for (i in 0..9) {
            val local = d(i.toString())
            phoneEnglish    = phoneEnglish.replace(local, i.toString())
            farmerIdEnglish = farmerIdEnglish.replace(local, i.toString())
        }

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
                        first_name          = findViewById<TextInputEditText>(R.id.etFirstName).text.toString(),
                        last_name           = findViewById<TextInputEditText>(R.id.etLastName).text.toString(),
                        phone_number        = phoneEnglish,
                        email               = findViewById<TextInputEditText>(R.id.etEmail).text.toString(),
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
                    btnSave.setCardBackgroundColor("#52B788".toColorInt())
                    toggleEditMode(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSave.isClickable = true
                    tvBtnLabel.text = t("Save Changes")
                    btnSave.setCardBackgroundColor("#52B788".toColorInt())
                    Toast.makeText(this@ProfileActivity, t("Save failed: ") + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}