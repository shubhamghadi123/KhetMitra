package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Phone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private var currentLangCode = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        lifecycleScope.launch {
            SupabaseManager.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> {
                        goToHomeScreen()
                    }
                    is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> {
                        setContentView(R.layout.activity_login)

                        if (currentLangCode != TranslateLanguage.ENGLISH) {
                            val rootView = findViewById<View>(android.R.id.content)
                            translateScreenInstant(rootView)
                            translateHints()
                        }

                        setupUI()
                    }
                    else -> {
                    }
                }
            }
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

    private fun translateHints() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return

        val inputs = mapOf(
            R.id.etLoginMobile to "Mobile Number",
            R.id.etLoginPassword to "Password"
        )

        for ((id, englishHint) in inputs) {
            val editText = findViewById<TextInputEditText>(id)
            val textInputLayout = editText?.parent?.parent as? com.google.android.material.textfield.TextInputLayout
            textInputLayout?.hint = t(englishHint)
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
            Toast.makeText(this, t("User is already registered, enter password"), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, t("Please enter both mobile number and password"), Toast.LENGTH_SHORT).show()
            return
        }

        val formattedPhone = if (mobile.startsWith("+")) mobile else "+91$mobile"
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.isEnabled = false
        btnLogin.text = t("Logging in...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.signInWith(Phone) {
                    this.phone = formattedPhone
                    this.password = password
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, t("Login Successful!"), Toast.LENGTH_SHORT).show()
                    goToHomeScreen()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is CancellationException) throw e

                    Toast.makeText(this@LoginActivity, t("Login Failed: ") + e.message, Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = t("Login")
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