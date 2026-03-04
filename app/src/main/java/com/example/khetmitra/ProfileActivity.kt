package com.example.khetmitra

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var chipGroupCrops: ChipGroup
    private val selectedCrops = mutableListOf<String>()
    private var isEditMode = false
    private var langCode: String = TranslateLanguage.ENGLISH
    private var englishLandSize: String = ""

    private val englishStates = arrayOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
        "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
        "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
        "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
        "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
        "Uttarakhand", "West Bengal"
    )

    private val englishDistricts = mapOf(
        "Maharashtra" to arrayOf("Pune", "Nashik", "Nagpur", "Aurangabad", "Solapur", "Mumbai", "Kolhapur", "Other"),
        "Punjab" to arrayOf("Ludhiana", "Amritsar", "Jalandhar", "Patiala", "Bathinda", "Other"),
        "Gujarat" to arrayOf("Ahmedabad", "Surat", "Rajkot", "Vadodara", "Bhavnagar", "Other"),
        "Madhya Pradesh" to arrayOf("Bhopal", "Indore", "Gwalior", "Jabalpur", "Ujjain", "Other"),
        "Rajasthan" to arrayOf("Jaipur", "Jodhpur", "Udaipur", "Ajmer", "Kota", "Other")
    )
    private val englishIncomes = arrayOf(
        "Below ₹50,000",
        "₹50,000 - ₹1,00,000",
        "₹1,00,000 - ₹3,00,000",
        "Above ₹3,00,000"
    )
    private val englishSoilTypes = arrayOf(
        "Alluvial Soil",
        "Black / Regur Soil",
        "Red & Yellow Soil",
        "Laterite Soil",
        "Arid / Desert Soil",
        "Mountain / Forest Soil",
        "Saline & Alkaline Soil",
        "Peaty & Marshy Soil",
        "Unknown Soil"
    )

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        if (langCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            translateScreenInstant(rootView)
            translateHints()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        setupDropdowns()

        chipGroupCrops = findViewById(R.id.chipGroupCrops)
        setupAddCropButton()

        val btnEditProfile = findViewById<ImageView>(R.id.btnEditProfile)
        btnEditProfile.setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
                Toast.makeText(this, t("Edit Mode Disabled"), Toast.LENGTH_SHORT).show()
            } else {
                toggleEditMode(true)
                Toast.makeText(this, t("Edit Mode Enabled"), Toast.LENGTH_SHORT).show()
            }
        }

        fetchProfileFromSupabase()
        toggleEditMode(false)

        findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            saveProfileToSupabase()
        }
    }

    private fun translateScreenInstant(view: View) {
        if (langCode == TranslateLanguage.ENGLISH) return

        if (view is TextView && view !is TextInputEditText && view !is AutoCompleteTextView) {
            val originalText = view.text.toString()
            if (originalText.isNotEmpty()) {
                view.text = t(originalText)
            }
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                translateScreenInstant(view.getChildAt(i))
            }
        }
    }

    private fun translateHints() {
        if (langCode == TranslateLanguage.ENGLISH) return

        val inputs = mapOf(
            R.id.etFirstName to "First Name",
            R.id.etLastName to "Last Name",
            R.id.etPhone to "Phone Number",
            R.id.etEmail to "Email Address",
            R.id.etFarmerId to "Government Farmer ID",
            R.id.etDob to "DD/MM/YYYY",
            R.id.dropdownIncome to "Income",
            R.id.dropdownState to "State",
            R.id.dropdownDistrict to "District / Village",
            R.id.etLandSize to "Total Land Size",
            R.id.dropdownSoil to "Soil Type"
        )

        for ((id, englishHint) in inputs) {
            val view = findViewById<View>(id)
            val textInputLayout = view?.parent?.parent as? com.google.android.material.textfield.TextInputLayout
            textInputLayout?.hint = t(englishHint)

            if (id == R.id.etPhone && textInputLayout?.prefixText != null) {
                textInputLayout.prefixText = d(textInputLayout.prefixText.toString())
            }
        }
    }

    private fun toggleEditMode(enabled: Boolean) {
        isEditMode = enabled

        val fields = listOf(
            R.id.etFirstName, R.id.etLastName, R.id.etPhone, R.id.etEmail,
            R.id.etFarmerId, R.id.etDob, R.id.dropdownState, R.id.dropdownIncome,
            R.id.dropdownDistrict, R.id.dropdownSoil
        )

        for (id in fields) {
            val view = findViewById<View>(id)
            view.isEnabled = enabled
            view.isClickable = enabled
            view.isFocusable = enabled

            if (view is TextInputEditText) {
                view.isFocusableInTouchMode = enabled
                view.isCursorVisible = enabled
            }

            val textInputLayout = view.parent?.parent as? com.google.android.material.textfield.TextInputLayout
            textInputLayout?.isEnabled = enabled
        }

        findViewById<MaterialButton>(R.id.btnSaveProfile).visibility = if (enabled) View.VISIBLE else View.GONE
        findViewById<Chip>(R.id.chipAddCrop).visibility = if (enabled) View.VISIBLE else View.GONE

        for (i in 0 until chipGroupCrops.childCount) {
            val chip = chipGroupCrops.getChildAt(i) as? Chip
            if (chip?.id != R.id.chipAddCrop) {
                chip?.isCloseIconVisible = enabled
            }
        }

        val editIcon = findViewById<ImageView>(R.id.btnEditProfile)
        if (enabled) {
            editIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            editIcon.setImageResource(android.R.drawable.ic_menu_edit)
        }
    }

    private class NoFilterAdapter(context: Context, private val items: Array<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {
        override fun getFilter(): android.widget.Filter {
            return object : android.widget.Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = items
                        count = items.size
                    }
                }
                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupDropdowns() {
        val dropdownState = findViewById<AutoCompleteTextView>(R.id.dropdownState)
        val dropdownDistrict = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict)
        val dropdownIncome = findViewById<AutoCompleteTextView>(R.id.dropdownIncome)
        val dropdownSoil = findViewById<AutoCompleteTextView>(R.id.dropdownSoil)

        val translatedStates = englishStates.map { t(it) }.toTypedArray()
        val translatedIncomes = englishIncomes.map { d(t(it)) }.toTypedArray()
        val translatedSoilTypes = englishSoilTypes.map { t(it) }.toTypedArray()

        dropdownState.setAdapter(NoFilterAdapter(this, translatedStates))
        dropdownIncome.setAdapter(NoFilterAdapter(this, translatedIncomes))
        dropdownSoil.setAdapter(NoFilterAdapter(this, translatedSoilTypes))

        dropdownState.setOnItemClickListener { _, _, position, _ ->
            val selectedStateTranslated = dropdownState.adapter.getItem(position).toString()
            val stateEng = getEnglishFromTranslated(selectedStateTranslated, englishStates)
            val districtsEng = englishDistricts[stateEng] ?: arrayOf("Other")
            val translatedDistricts = districtsEng.map { t(it) }.toTypedArray()

            dropdownDistrict.setAdapter(ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, translatedDistricts))
            dropdownDistrict.setText("", false)
        }
    }

    private fun setupAddCropButton() {
        val chipAddCrop = findViewById<Chip>(R.id.chipAddCrop)
        val commonCropsEnglish = arrayOf("Wheat", "Cotton", "Sugarcane", "Rice", "Maize", "Soybean", "Mustard")

        val commonCropsTranslated = commonCropsEnglish.map { t(it) }.toTypedArray()

        chipAddCrop.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(t("Select a Crop"))
                .setItems(commonCropsTranslated) { _, which ->
                    val cropNameEnglish = commonCropsEnglish[which]
                    if (!selectedCrops.contains(cropNameEnglish)) {
                        addCropChip(cropNameEnglish, isEditMode)
                        selectedCrops.add(cropNameEnglish)
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }
    }

    private fun addCropChip(cropNameEnglish: String, showCloseIcon: Boolean = true) {
        val chip = Chip(this)

        chip.text = t(cropNameEnglish)
        chip.isCloseIconVisible = showCloseIcon
        chip.setChipBackgroundColorResource(android.R.color.white)
        chip.setOnCloseIconClickListener {
            if (isEditMode) {
                chipGroupCrops.removeView(chip)
                selectedCrops.remove(cropNameEnglish)
            }
        }

        val addCropIndex = chipGroupCrops.childCount - 1
        chipGroupCrops.addView(chip, addCropIndex)
    }

    private fun fetchProfileFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()

                if (user != null) {
                    val profile = SupabaseManager.client.postgrest["farmers"]
                        .select { filter { eq("id", user.id) } }
                        .decodeSingleOrNull<FarmerProfile>()

                    withContext(Dispatchers.Main) {
                        if (profile != null) populateUI(profile)
                    }
                }
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
        findViewById<TextInputEditText>(R.id.etPhone).setText(d(profile.phone_number ?: ""))
        findViewById<TextInputEditText>(R.id.etFarmerId).setText(d(profile.gov_farmer_id ?: ""))
        findViewById<TextInputEditText>(R.id.etDob).setText(d(profile.date_of_birth ?: ""))

        findViewById<AutoCompleteTextView>(R.id.dropdownState).setText(t(profile.state_location ?: ""), false)
        findViewById<AutoCompleteTextView>(R.id.dropdownIncome).setText(d(t(profile.annual_income_range ?: "")), false)
        findViewById<AutoCompleteTextView>(R.id.dropdownSoil).setText(t(profile.soil_type ?: ""), false)

        val stateEng = profile.state_location ?: ""
        val districtsEng = englishDistricts[stateEng] ?: arrayOf("Other")
        val translatedDistricts = districtsEng.map { t(it) }.toTypedArray()
        val dropdownDistrict = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict)
        dropdownDistrict.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, translatedDistricts))
        dropdownDistrict.setText(t(profile.district ?: ""), false)

        val landSizeInput = findViewById<TextInputEditText>(R.id.etLandSize)
        val landSizeLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilLandSize)

        englishLandSize = profile.land_size ?: ""
        if (englishLandSize.isNotEmpty()) {
            val parts = englishLandSize.split(" ")
            if (parts.size >= 2) {
                val numberValue = parts[0]
                val unitText = parts[1]
                landSizeInput.setText(d(numberValue))
                landSizeLayout?.suffixText = t(unitText)
            } else {
                landSizeInput.setText(d(englishLandSize))
                landSizeLayout?.suffixText = null
            }
        } else {
            landSizeInput.setText(t("Not Added"))
            landSizeLayout?.suffixText = null
        }

        val savedCropsStr = profile.crops ?: ""
        if (savedCropsStr.isNotEmpty()) {
            val crops = savedCropsStr.split(",")
            for (crop in crops) {
                val trimmedCrop = crop.trim()
                if (trimmedCrop.isNotBlank() && !selectedCrops.contains(trimmedCrop)) {
                    addCropChip(trimmedCrop, isEditMode)
                    selectedCrops.add(trimmedCrop)
                }
            }
        }
    }

    private fun getEnglishFromTranslated(translated: String, englishArray: Array<String>): String {
        val translatedArray = englishArray.map { t(it) }
        val index = translatedArray.indexOf(translated)
        return if (index >= 0) englishArray[index] else translated
    }

    private fun saveProfileToSupabase() {
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProfile)
        btnSave.isEnabled = false
        btnSave.text = t("Saving...")

        val currentStateUI = findViewById<AutoCompleteTextView>(R.id.dropdownState).text.toString()
        val currentDistrictUI = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict).text.toString()
        val currentIncomeUI = findViewById<AutoCompleteTextView>(R.id.dropdownIncome).text.toString()
        val currentSoilUI = findViewById<AutoCompleteTextView>(R.id.dropdownSoil).text.toString()

        val currentDobUI = findViewById<TextInputEditText>(R.id.etDob).text.toString()
        val currentPhoneUI = findViewById<TextInputEditText>(R.id.etPhone).text.toString()
        val currentFarmerIdUI = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString()

        val stateEnglish = getEnglishFromTranslated(currentStateUI, englishStates)
        val districtsForState = englishDistricts[stateEnglish] ?: arrayOf("Other")
        val districtEnglish = getEnglishFromTranslated(currentDistrictUI, districtsForState)
        val soilEnglish = getEnglishFromTranslated(currentSoilUI, englishSoilTypes)

        val incomeTranslatedList = englishIncomes.map { d(t(it)) }
        val incomeIndex = incomeTranslatedList.indexOf(currentIncomeUI)
        val incomeEnglish = if (incomeIndex >= 0) englishIncomes[incomeIndex] else currentIncomeUI

        var dobEnglish = currentDobUI
        for (i in 0..9) {
            dobEnglish = dobEnglish.replace(d(i.toString()), i.toString())
        }

        var phoneEnglish = currentPhoneUI
        for (i in 0..9) {
            phoneEnglish = phoneEnglish.replace(d(i.toString()), i.toString())
        }

        var farmerIdEnglish = currentFarmerIdUI
        for (i in 0..9) {
            farmerIdEnglish = farmerIdEnglish.replace(d(i.toString()), i.toString())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    val updatedProfile = FarmerProfile(
                        id = user.id,
                        first_name = findViewById<TextInputEditText>(R.id.etFirstName).text.toString(),
                        last_name = findViewById<TextInputEditText>(R.id.etLastName).text.toString(),
                        phone_number = phoneEnglish,
                        email = findViewById<TextInputEditText>(R.id.etEmail).text.toString(),
                        gov_farmer_id = farmerIdEnglish,
                        date_of_birth = dobEnglish,
                        state_location = stateEnglish,
                        annual_income_range = incomeEnglish,
                        district = districtEnglish,
                        land_size = englishLandSize,
                        soil_type = soilEnglish,
                        crops = selectedCrops.joinToString(",")
                    )

                    SupabaseManager.client.postgrest["farmers"].upsert(updatedProfile)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileActivity, t("Profile Updated!"), Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        btnSave.text = t("Save Changes")
                        toggleEditMode(false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSave.isEnabled = true
                    btnSave.text = t("Save Changes")
                    Toast.makeText(this@ProfileActivity, t("Save failed: ") + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}