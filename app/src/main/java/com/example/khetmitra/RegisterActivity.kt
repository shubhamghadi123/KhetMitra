package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Spinner
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
        val adapter = spinner.adapter

        for (i in 0 until adapter.count) {
            val spinnerItem = adapter.getItem(i).toString()
            if (spinnerItem.equals(detectedState, ignoreCase = true) || detectedState.contains(spinnerItem, ignoreCase = true)) {
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

        val day = findViewById<Spinner>(R.id.spinnerDay).selectedItem?.toString() ?: "01"
        val month = findViewById<Spinner>(R.id.spinnerMonth).selectedItem?.toString() ?: "Jan"
        val year = findViewById<Spinner>(R.id.spinnerYear).selectedItem?.toString() ?: "1990"
        val state = findViewById<Spinner>(R.id.spinnerState).selectedItem?.toString() ?: "Unknown"
        val income = findViewById<Spinner>(R.id.spinnerIncome).selectedItem?.toString() ?: "Unknown"

        // --- 1. STRICT PRODUCTION VALIDATION ---
        if (firstName.isEmpty() || lastName.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (phone.length < 10) {
            Toast.makeText(this, "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length <= 6) {
            Toast.makeText(this, "Password must be more than 6 characters long", Toast.LENGTH_SHORT).show()
            return
        }
        val hasLetter = password.any { it.isLetter() }
        val hasNumber = password.any { it.isDigit() }
        if (!hasLetter || !hasNumber) {
            Toast.makeText(this, "Password must include both letters and numbers", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email format", Toast.LENGTH_SHORT).show()
            return
        }

        val dob = formatDob(day, month, year)
        val formattedPhone = if (phone.startsWith("+")) phone else "+91$phone"

        btnRegister.isEnabled = false
        btnRegister.text = "Registering..."

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
                        throw Exception("This email is already registered to another account. Please use a different email.")
                    }

                    val newProfile = FarmerProfile(
                        id = userId,
                        first_name = firstName,
                        last_name = lastName,
                        phone_number = phone,
                        email = email,
                        gov_farmer_id = farmerId.ifEmpty { null },
                        date_of_birth = dob,
                        state_location = state,
                        annual_income_range = income
                    )

                    SupabaseManager.client.postgrest["farmers"].insert(newProfile)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Registration Successful!", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Register"
                        Toast.makeText(this@RegisterActivity, "Login blocked. Check Supabase settings.", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                withContext(Dispatchers.Main) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "Register"

                    val errorMsg = e.message?.lowercase() ?: ""

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

    private fun formatDob(day: String, month: String, year: String): String {
        val monthNumber = when (month.take(3).lowercase()) {
            "jan" -> "01"; "feb" -> "02"; "mar" -> "03"; "apr" -> "04"
            "may" -> "05"; "jun" -> "06"; "jul" -> "07"; "aug" -> "08"
            "sep" -> "09"; "oct" -> "10"; "nov" -> "11"; "dec" -> "12"
            else -> "01"
        }
        val paddedDay = day.padStart(2, '0')
        return "$year-$monthNumber-$paddedDay"
    }

    private fun setupSpinners() {
        val days = (1..31).map { it.toString() }.toTypedArray()
        findViewById<Spinner>(R.id.spinnerDay).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)

        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        findViewById<Spinner>(R.id.spinnerMonth).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        val years = (1940..2026).map { it.toString() }.reversed().toTypedArray()
        findViewById<Spinner>(R.id.spinnerYear).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)

        val states = arrayOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
            "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
            "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
            "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal"
        )
        findViewById<Spinner>(R.id.spinnerState).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, states)

        val incomes = arrayOf(
            "Below ₹50,000",
            "₹50,000 - ₹1,00,000",
            "₹1,00,000 - ₹3,00,000",
            "Above ₹3,00,000"
        )
        findViewById<Spinner>(R.id.spinnerIncome).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, incomes)
    }
}