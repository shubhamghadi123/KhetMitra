package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val btnSendOTP = findViewById<MaterialButton>(R.id.btnSendOTP)
        val etEmail = findViewById<TextInputEditText>(R.id.etResetEmail)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnSendOTP.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address"
                return@setOnClickListener
            }

            sendResetEmail(email, btnSendOTP, progressBar)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun sendResetEmail(email: String, button: MaterialButton, progress: ProgressBar) {
        button.isEnabled = false
        button.text = "Sending..."
        progress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseManager.client.auth.resetPasswordForEmail(email = email)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Reset code sent to your email!",
                        Toast.LENGTH_LONG
                    ).show()

                    val intent = Intent(this@ForgotPasswordActivity, ResetPasswordVerifyActivity::class.java)
                    intent.putExtra("user_email", email)
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is CancellationException) throw e

                    Toast.makeText(this@ForgotPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    button.isEnabled = true
                    button.text = "Send OTP"
                    progress.visibility = View.GONE
                }
            }
        }
    }
}