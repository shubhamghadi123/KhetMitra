package com.example.khetmitra

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
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
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResetPasswordVerifyActivity : AppCompatActivity() {
    private lateinit var btnConfirmReset: MaterialCardView
    private lateinit var tvBtnConfirmLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tilOtpCode: TextInputLayout
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etOtpCode: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private var currentLangCode = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.convertDigits(text, currentLangCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password_verify)

        TranslationHelper.initTranslations(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        btnConfirmReset    = findViewById(R.id.btnConfirmReset)
        tvBtnConfirmLabel  = btnConfirmReset.findViewById(R.id.tvBtnConfirmLabel)
        progressBar        = btnConfirmReset.findViewById(R.id.progressBar)
        tilOtpCode         = findViewById(R.id.tilOtpCode)
        tilNewPassword     = findViewById(R.id.tilNewPassword)
        etOtpCode          = findViewById(R.id.etOtpCode)
        etNewPassword      = findViewById(R.id.etNewPassword)

        setupTextFieldColors()
        if (currentLangCode != TranslateLanguage.ENGLISH) {
            translateScreenInstant(findViewById(android.R.id.content))
            translateHintsAndHelpers()
        }
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
        val userEmail = intent.getStringExtra("user_email") ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(this, t("Session error. Please request a new code."), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnConfirmReset.setOnClickListener {
            val otp         = etOtpCode.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()
            var hasError = false
            if (otp.isEmpty() || otp.length != 6) {
                tilOtpCode.error = d(t("Please enter a valid 6-digit code"))
                hasError = true
            } else {
                tilOtpCode.error = null
            }
            when {
                newPassword.length <= 6 -> {
                    tilNewPassword.error = d(t("Password must be more than 6 characters"))
                    hasError = true
                }
                !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() } -> {
                    tilNewPassword.error = t("Password must include letters and numbers")
                    hasError = true
                }
                else -> tilNewPassword.error = null
            }
            if (hasError) return@setOnClickListener
            performPasswordReset(userEmail, otp, newPassword)
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
        listOf(tilOtpCode, tilNewPassword).forEach { til ->
            til.setBoxBackgroundColor(bgColor)
            til.setBoxStrokeColorStateList(strokeStateList)
            til.boxStrokeColor = greenColor
        }
    }

    private fun translateScreenInstant(view: View) {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        if (view is TextView) {
            val text = view.text.toString()
            if (text.isNotEmpty()) view.text = d(t(text))
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) translateScreenInstant(view.getChildAt(i))
        }
    }

    private fun translateHintsAndHelpers() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return
        tilOtpCode.hint = d(t("6-Digit Reset Code"))
        tilOtpCode.helperText = d(t("Check your email inbox for the 6-digit code"))
        tilNewPassword.hint = d(t("New Password"))
        tilNewPassword.helperText = d(t("Must be more than 6 characters with letters and numbers"))
    }

    private fun performPasswordReset(email: String, otp: String, pass: String) {
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.verifyEmailOtp(
                    type  = OtpType.Email.RECOVERY,
                    email = email,
                    token = otp
                )
                SupabaseManager.client.auth.updateUser {
                    password = pass
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ResetPasswordVerifyActivity,
                        t("Password Reset Successful!"),
                        Toast.LENGTH_SHORT
                    ).show()
                    SupabaseManager.client.auth.signOut()
                    startActivity(
                        Intent(this@ResetPasswordVerifyActivity, LoginActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ResetPasswordError", "Failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    tilOtpCode.error = t("Invalid code or session expired. Please try again.")
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        btnConfirmReset.isClickable  = !loading
        progressBar.visibility       = if (loading) View.VISIBLE else View.GONE
        tvBtnConfirmLabel.text       = if (loading) t("Verifying...") else t("Update Password")
        btnConfirmReset.setCardBackgroundColor(
            if (loading) "#2D6A4F".toColorInt() else "#52B788".toColorInt()
        )
    }
}