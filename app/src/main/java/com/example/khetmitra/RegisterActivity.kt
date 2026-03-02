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
import io.github.jan.supabase.gotrue.providers.builtin.Phone
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private lateinit var btnRegister: MaterialButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLangCode = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(num: String): String {
        return TranslationHelper.convertDigits(num, currentLangCode)
    }

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
        val spinner = findViewById<Spinner>(R.id.spinnerState)

        val englishStates = arrayOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
            "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
            "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
            "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal"
        )

        for (i in englishStates.indices) {
            val stateEnglish = englishStates[i]
            if (stateEnglish.equals(detectedState, ignoreCase = true) || detectedState.contains(stateEnglish, ignoreCase = true)) {
                spinner.setSelection(i)
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

        val statePosition = findViewById<Spinner>(R.id.spinnerState).selectedItemPosition
        val incomePosition = findViewById<Spinner>(R.id.spinnerIncome).selectedItemPosition

        val englishStates = arrayOf("Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal")
        val englishIncomes = arrayOf("Below ₹50,000", "₹50,000 - ₹1,00,000", "₹1,00,000 - ₹3,00,000", "Above ₹3,00,000")

        val stateEnglish = if (statePosition >= 0) englishStates[statePosition] else "Unknown"
        val incomeEnglish = if (incomePosition >= 0) englishIncomes[incomePosition] else "Unknown"

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
                SupabaseManager.client.auth.signUpWith(Phone) {
                    this.phone = formattedPhone
                    this.password = password
                }

                val user = SupabaseManager.client.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id

                    try {
                        SupabaseManager.client.auth.updateUser {
                            this.email = email
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        throw Exception(t("This email is already registered to another account. Please use a different email."))
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

        val states = arrayOf(
            t("Andhra Pradesh"), t("Arunachal Pradesh"), t("Assam"), t("Bihar"), t("Chhattisgarh"),
            t("Goa"), t("Gujarat"), t("Haryana"), t("Himachal Pradesh"), t("Jammu and Kashmir"),
            t("Jharkhand"), t("Karnataka"), t("Kerala"), t("Madhya Pradesh"), t("Maharashtra"),
            t("Manipur"), t("Meghalaya"), t("Mizoram"), t("Nagaland"), t("Odisha"), t("Punjab"),
            t("Rajasthan"), t("Sikkim"), t("Tamil Nadu"), t("Telangana"), t("Tripura"), t("Uttar Pradesh"),
            t("Uttarakhand"), t("West Bengal")
        )
        findViewById<Spinner>(R.id.spinnerState).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, states)

        val incomes = arrayOf(
            d(t("Below ₹50,000")),
            d(t("₹50,000 - ₹1,00,000")),
            d(t("₹1,00,000 - ₹3,00,000")),
            d(t("Above ₹3,00,000"))
        )
        findViewById<Spinner>(R.id.spinnerIncome).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, incomes)
    }
}