package com.muslimislam.aitouch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * First screen shown when the app opens. Asks for the access PIN and
 * verifies it against the HF Space server (which checks it against the
 * ACCESS_PIN repository secret). Only proceeds to MainActivity once valid.
 *
 * If already verified in a previous session, skips straight through —
 * so the user doesn't have to re-enter the PIN every single time the
 * app is reopened (only after data is cleared or reinstalled).
 */
class PinLockActivity : AppCompatActivity() {

    private lateinit var etPin: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnUnlock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AppStore.isPinVerified(this)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_pin_lock)
        etPin = findViewById(R.id.etPin)
        tvStatus = findViewById(R.id.tvPinStatus)
        btnUnlock = findViewById(R.id.btnUnlock)

        etPin.setText(AppStore.loadPin(this))

        btnUnlock.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (pin.isEmpty()) {
                tvStatus.text = "PIN likhna zaroori hai"
                return@setOnClickListener
            }
            btnUnlock.isEnabled = false
            btnUnlock.text = "Checking..."
            tvStatus.text = ""

            BackendClient.verifyPin(this, pin) { valid, error ->
                btnUnlock.isEnabled = true
                btnUnlock.text = "Unlock 🔓"
                if (valid) {
                    AppStore.savePin(this, pin)
                    AppStore.setPinVerified(this, true)
                    goToMain()
                } else {
                    tvStatus.text = error ?: "Galat PIN"
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
