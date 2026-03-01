package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResetPasswordVerifyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password_verify)

        val etOtp = findViewById<TextInputEditText>(R.id.etOtpCode)
        val etNewPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmReset)

        val userEmail = intent.getStringExtra("user_email") ?: ""

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Session error. Please request a new code.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnConfirm.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()

            if (otp.isEmpty() || otp.length != 6) {
                Toast.makeText(this, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword.length <= 6) {
                Toast.makeText(this, "Password must be more than 6 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hasLetter = newPassword.any { it.isLetter() }
            val hasNumber = newPassword.any { it.isDigit() }
            if (!hasLetter || !hasNumber) {
                Toast.makeText(this, "Password must include both letters and numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performPasswordReset(userEmail, otp, newPassword, btnConfirm)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun performPasswordReset(email: String, otp: String, pass: String, button: MaterialButton) {
        button.isEnabled = false
        button.text = "Verifying..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email,
                    token = otp
                )
                SupabaseManager.client.auth.updateUser {
                    password = pass
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ResetPasswordVerifyActivity, "Password Reset Successful!", Toast.LENGTH_SHORT).show()
                    SupabaseManager.client.auth.signOut()

                    val intent = Intent(this@ResetPasswordVerifyActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is CancellationException) throw e
                    Log.e("ResetPasswordError", "Failed: ${e.message}", e)
                    button.isEnabled = true
                    button.text = "Reset Password"
                    Toast.makeText(this@ResetPasswordVerifyActivity, "Invalid OTP or Session Expired", Toast.LENGTH_LONG).show()}
            }
        }
    }
}