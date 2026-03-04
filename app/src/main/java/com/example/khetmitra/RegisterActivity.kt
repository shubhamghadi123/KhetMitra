package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private lateinit var btnRegister: MaterialButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var spinnerState: Spinner
    private lateinit var spinnerDistrict: Spinner
    private lateinit var spinnerIncome: Spinner

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(num: String): String {
        return TranslationHelper.convertDigits(num, currentLangCode)
    }

    private val englishStates = arrayOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
        "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
        "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
        "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
        "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
        "Uttarakhand", "West Bengal"
    )

    private val englishDistrictsMap = mapOf(
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchLocationAndAutoSelectState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            translateScreenInstant(rootView)
            translateHints()
        }

        btnRegister = findViewById(R.id.btnRegister)
        spinnerState = findViewById(R.id.spinnerState)
        spinnerDistrict = findViewById(R.id.spinnerDistrict)
        spinnerIncome = findViewById(R.id.spinnerIncome)
        setupSpinners()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndAutoSelectState()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        btnRegister.setOnClickListener {
            registerFarmer()
        }
    }

    private fun translateHints() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return

        val inputs = mapOf(
            R.id.etFirstName to "First Name",
            R.id.etLastName to "Last Name",
            R.id.etPhone to "Phone Number",
            R.id.etPassword to "Password",
            R.id.etEmail to "Email",
            R.id.etFarmerId to "Government Farmer ID"
        )

        for ((id, englishHint) in inputs) {
            val editText = findViewById<TextInputEditText>(id)
            val textInputLayout = editText?.parent?.parent as? com.google.android.material.textfield.TextInputLayout
            textInputLayout?.hint = t(englishHint)
        }
    }

    private fun translateScreenInstant(view: View) {
        if (currentLangCode == TranslateLanguage.ENGLISH) return

        if (view is TextView) {
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

    private fun fetchLocationAndAutoSelectState() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(this@RegisterActivity, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                        if (!addresses.isNullOrEmpty()) {
                            val detectedState = addresses[0].adminArea
                            if (detectedState != null) {
                                withContext(Dispatchers.Main) {
                                    selectStateInSpinner(detectedState)
                                }
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
        for (i in englishStates.indices) {
            val stateEnglish = englishStates[i]
            if (stateEnglish.equals(detectedState, ignoreCase = true) || detectedState.contains(stateEnglish, ignoreCase = true)) {
                spinnerState.setSelection(i)
                break
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun registerFarmer() {
        val firstName = findViewById<TextInputEditText>(R.id.etFirstName).text.toString().trim()
        val lastName = findViewById<TextInputEditText>(R.id.etLastName).text.toString().trim()
        val phone = findViewById<TextInputEditText>(R.id.etPhone).text.toString().trim()
        val email = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
        val farmerId = findViewById<TextInputEditText>(R.id.etFarmerId).text.toString().trim()

        val dayIndex = findViewById<Spinner>(R.id.spinnerDay).selectedItemPosition
        val dayEnglish = if (dayIndex >= 0) (dayIndex + 1).toString() else "01"

        val monthIndex = findViewById<Spinner>(R.id.spinnerMonth).selectedItemPosition

        val yearIndex = findViewById<Spinner>(R.id.spinnerYear).selectedItemPosition
        val yearEnglish = if (yearIndex >= 0) (2026 - yearIndex).toString() else "1990"

        val statePosition = spinnerState.selectedItemPosition
        val districtPosition = spinnerDistrict.selectedItemPosition
        val incomePosition = spinnerIncome.selectedItemPosition

        val stateEnglish = if (statePosition >= 0) englishStates[statePosition] else "Unknown"
        val incomeEnglish = if (incomePosition >= 0) englishIncomes[incomePosition] else "Unknown"

        val districtsForStateEng = englishDistrictsMap[stateEnglish] ?: arrayOf("Other")
        val districtEnglish = if (districtPosition >= 0 && districtPosition < districtsForStateEng.size) districtsForStateEng[districtPosition] else "Unknown"

        if (firstName.isEmpty() || lastName.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, t("Please fill all required fields"), Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length < 10) {
            Toast.makeText(this, t("Please enter a valid 10-digit phone number"), Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length <= 6) {
            Toast.makeText(this, t("Password must be more than 6 characters long"), Toast.LENGTH_SHORT).show()
            return
        }
        val hasLetter = password.any { it.isLetter() }
        val hasNumber = password.any { it.isDigit() }
        if (!hasLetter || !hasNumber) {
            Toast.makeText(this, t("Password must include both letters and numbers"), Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, t("Please enter a valid email format"), Toast.LENGTH_SHORT).show()
            return
        }

        val dob = formatDob(dayEnglish, monthIndex, yearEnglish)
        val formattedPhone = if (phone.startsWith("+")) phone else "+91$phone"

        btnRegister.isEnabled = false
        btnRegister.text = t("Registering...")

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseManager.client.auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }

                val user = SupabaseManager.client.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id

                    try {
                        SupabaseManager.client.auth.updateUser {
                            this.phone = formattedPhone
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        throw Exception("This phone number is already linked to another account.")
                    }

                    val newProfile = FarmerProfile(
                        id = userId,
                        first_name = firstName,
                        last_name = lastName,
                        phone_number = phone,
                        email = email,
                        gov_farmer_id = farmerId.ifEmpty { null },
                        date_of_birth = dob,
                        state_location = stateEnglish,
                        district = districtEnglish,
                        annual_income_range = incomeEnglish
                    )

                    SupabaseManager.client.postgrest["farmers"].insert(newProfile)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, t("Registration Successful!"), Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        btnRegister.isEnabled = true
                        btnRegister.text = t("Register")
                        Toast.makeText(this@RegisterActivity, t("Login blocked. Check Supabase settings."), Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                withContext(Dispatchers.Main) {
                    btnRegister.isEnabled = true
                    btnRegister.text = t("Register")

                    val errorMsg = e.message?.lowercase() ?: t("")

                    if (errorMsg.contains("already registered") || errorMsg.contains("already exists") || errorMsg.contains("in use") && !errorMsg.contains("email")) {
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        intent.putExtra("REGISTERED_PHONE", phone)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e("SupabaseError", "Registration Error: ", e)
                        Toast.makeText(this@RegisterActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun formatDob(day: String, monthIndex: Int, year: String): String {
        val monthNumber = (monthIndex + 1).toString().padStart(2, '0')
        val paddedDay = day.padStart(2, '0')
        return "$year-$monthNumber-$paddedDay"
    }

    private fun setupSpinners() {
        val days = (1..31).map { d(it.toString()) }.toTypedArray()
        findViewById<Spinner>(R.id.spinnerDay).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)

        val months = arrayOf(t("Jan"), t("Feb"), t("Mar"), t("Apr"), t("May"), t("Jun"), t("Jul"), t("Aug"), t("Sep"), t("Oct"), t("Nov"), t("Dec"))
        findViewById<Spinner>(R.id.spinnerMonth).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        val years = (1940..2026).map { d(it.toString()) }.reversed().toTypedArray()
        findViewById<Spinner>(R.id.spinnerYear).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)

        val translatedStates = englishStates.map { t(it) }.toTypedArray()
        spinnerState.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, translatedStates)

        spinnerState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val stateEng = englishStates[position]
                val districtsEng = englishDistrictsMap[stateEng] ?: arrayOf("Other")

                val districtsTranslated = districtsEng.map { t(it) }.toTypedArray()
                spinnerDistrict.adapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_spinner_dropdown_item, districtsTranslated)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val translatedIncomes = englishIncomes.map { d(t(it)) }.toTypedArray()
        spinnerIncome.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, translatedIncomes)
    }
}