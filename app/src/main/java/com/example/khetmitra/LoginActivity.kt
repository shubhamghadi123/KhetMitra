package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Phone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            SupabaseManager.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> {
                        goToHomeScreen()
                    }
                    is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> {
                        setContentView(R.layout.activity_login)
                        setupUI()
                    }
                    else -> {
                    }
                }
            }
        }
    }

    private fun setupUI() {
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val etLoginMobile = findViewById<TextInputEditText>(R.id.etLoginMobile)

        val prefilledPhone = intent.getStringExtra("REGISTERED_PHONE")
        if (!prefilledPhone.isNullOrEmpty()) {
            etLoginMobile.setText(prefilledPhone)
            Toast.makeText(this, "User is already registered, enter password", Toast.LENGTH_LONG).show()
        }

        btnLogin.setOnClickListener {
            loginFarmer()
        }

        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loginFarmer() {
        val mobile = findViewById<TextInputEditText>(R.id.etLoginMobile).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.etLoginPassword).text.toString()

        if (mobile.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both mobile number and password", Toast.LENGTH_SHORT).show()
            return
        }

        val formattedPhone = if (mobile.startsWith("+")) mobile else "+91$mobile"
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.isEnabled = false
        btnLogin.text = "Logging in..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.signInWith(Phone) {
                    this.phone = formattedPhone
                    this.password = password
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    goToHomeScreen()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is CancellationException) throw e

                    Toast.makeText(this@LoginActivity, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"
                }
            }
        }
    }

    private fun goToHomeScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}