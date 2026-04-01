package com.example.khetmitra

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var btnSendOTP: MaterialCardView
    private lateinit var tvBtnSendLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tilResetEmail: TextInputLayout
    private lateinit var etResetEmail: TextInputEditText
    private var currentLangCode = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        btnSendOTP    = findViewById(R.id.btnSendOTP)
        tvBtnSendLabel = btnSendOTP.findViewById(R.id.tvBtnSendLabel)
        progressBar   = btnSendOTP.findViewById(R.id.progressBar)
        tilResetEmail = findViewById(R.id.tilResetEmail)
        etResetEmail  = findViewById(R.id.etResetEmail)
        setupTextFieldColors()

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHints()
        }

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvBackToLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        btnSendOTP.setOnClickListener {
            val email = etResetEmail.text.toString().trim()
            if (email.isEmpty()) {
                tilResetEmail.error = t("Email is required")
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilResetEmail.error = t("Please enter a valid email address")
                return@setOnClickListener
            }
            tilResetEmail.error = null
            sendResetEmail(email)
        }
    }

    private fun setupTextFieldColors() {
        val greenColor  = "#52B788".toColorInt()
        val defaultGrey = "#E8EDE0".toColorInt()
        val bgColor     = "#FFFFFF".toColorInt()
        val strokeStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf() // Default state
            ),
            intArrayOf(greenColor, defaultGrey, greenColor)
        )
        tilResetEmail.setBoxBackgroundColor(bgColor)
        tilResetEmail.setBoxStrokeColorStateList(strokeStateList)
        tilResetEmail.boxStrokeColor = greenColor
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

    private fun translateHints() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        tilResetEmail.hint = t("Email")
    }

    private fun sendResetEmail(email: String) {
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.resetPasswordForEmail(email = email)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        t("Reset code sent to your email!"),
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(this@ForgotPasswordActivity, ResetPasswordVerifyActivity::class.java)
                            .putExtra("user_email", email)
                    )
                    finish()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForgotPasswordActivity, t("Error: ") + e.message, Toast.LENGTH_LONG).show()
                    setLoadingState(false)
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        btnSendOTP.isClickable   = !loading
        progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
        tvBtnSendLabel.text      = if (loading) t("Sending...") else t("Send Reset Code")
        btnSendOTP.setCardBackgroundColor(
            if (loading) "#2D6A4F".toColorInt() else "#52B788".toColorInt()
        )
    }
}