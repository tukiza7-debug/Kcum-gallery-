package com.kcum.gallery.view

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.kcum.gallery.R
import com.kcum.gallery.util.SecurityUtils

/**
 * Skrin PIN / Biometrik.
 *
 * MOD:
 * - MODE_VERIFY : sahkan PIN sedia ada (kunci app, buka album peribadi)
 * - MODE_SETUP  : tetapkan PIN baru (masukkan 2 kali)
 * - MODE_CHANGE : sahkan PIN lama dahulu, kemudian tetapkan PIN baru
 *
 * Jika biometrik tersedia & diaktifkan, prompt cap jari ditawarkan dalam MODE_VERIFY.
 */
class PinActivity : AppCompatActivity() {

    private lateinit var mode: String
    private lateinit var etPin: EditText
    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var txtError: TextView
    private lateinit var btnOk: MaterialButton
    private lateinit var btnBiometric: ImageButton

    // Keadaan dalaman untuk aliran SETUP / CHANGE
    private var step: Int = STEP_FIRST
    private var firstPin: String? = null

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_VERIFY = "verify"
        const val MODE_SETUP = "setup"
        const val MODE_CHANGE = "change"

        private const val STEP_FIRST = 0
        private const val STEP_CONFIRM = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VERIFY
        etPin = findViewById(R.id.et_pin)
        txtTitle = findViewById(R.id.txt_pin_title)
        txtSubtitle = findViewById(R.id.txt_pin_subtitle)
        txtError = findViewById(R.id.txt_pin_error)
        btnOk = findViewById(R.id.btn_pin_ok)
        btnBiometric = findViewById(R.id.btn_biometric)

        btnOk.setOnClickListener { handleAction() }
        btnBiometric.setOnClickListener { showBiometric() }

        updateUiForStep()

        // Tawarkan biometrik secara automatik dalam mod sahkan
        if (mode == MODE_VERIFY && isBiometricAvailable()) {
            btnBiometric.visibility = View.VISIBLE
            showBiometric()
        } else {
            btnBiometric.visibility = View.GONE
        }
    }

    private fun updateUiForStep() {
        when (mode) {
            MODE_VERIFY -> {
                txtTitle.setText(R.string.pin_verify_title)
                txtSubtitle.setText(R.string.pin_enter_hint)
                btnOk.setText(R.string.ok)
            }
            MODE_SETUP -> {
                if (step == STEP_FIRST) {
                    txtTitle.setText(R.string.pin_setup_title)
                    txtSubtitle.setText(R.string.pin_enter_hint)
                } else {
                    txtTitle.setText(R.string.pin_setup_title)
                    txtSubtitle.setText(R.string.pin_confirm_hint)
                }
                btnOk.setText(R.string.done)
            }
            MODE_CHANGE -> {
                if (step == STEP_FIRST) {
                    txtTitle.setText(R.string.pin_old_title)
                    txtSubtitle.setText(R.string.pin_old_hint)
                } else {
                    txtTitle.setText(R.string.pin_setup_title)
                    txtSubtitle.setText(R.string.pin_enter_hint)
                }
                btnOk.setText(R.string.done)
            }
        }
        txtError.visibility = View.GONE
        etPin.setText("")
        etPin.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etPin, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun handleAction() {
        val pin = etPin.text.toString()
        if (pin.length < 4) {
            showError(getString(R.string.pin_too_short))
            return
        }
        when (mode) {
            MODE_VERIFY -> {
                if (SecurityUtils.verifyPin(this, pin)) {
                    finishWithSuccess()
                } else {
                    showError(getString(R.string.pin_wrong))
                }
            }
            MODE_SETUP -> {
                if (step == STEP_FIRST) {
                    firstPin = pin
                    step = STEP_CONFIRM
                    updateUiForStep()
                } else {
                    if (pin == firstPin) {
                        SecurityUtils.setPin(this, pin)
                        Toast.makeText(this, R.string.pin_set_ok, Toast.LENGTH_SHORT).show()
                        finishWithSuccess()
                    } else {
                        showError(getString(R.string.pin_mismatch))
                        step = STEP_FIRST
                        firstPin = null
                    }
                }
            }
            MODE_CHANGE -> {
                if (step == STEP_FIRST) {
                    if (SecurityUtils.verifyPin(this, pin)) {
                        step = STEP_CONFIRM
                        updateUiForStep()
                    } else {
                        showError(getString(R.string.pin_wrong))
                    }
                } else {
                    SecurityUtils.setPin(this, pin)
                    Toast.makeText(this, R.string.pin_set_ok, Toast.LENGTH_SHORT).show()
                    finishWithSuccess()
                }
            }
        }
    }

    private fun isBiometricAvailable(): Boolean {
        return SecurityUtils.canUseBiometric(this) &&
            SecurityUtils.hasPin(this) &&
            com.kcum.gallery.data.PrefsRepository.get(this).biometricEnabled
    }

    private fun showBiometric() {
        SecurityUtils.showBiometricPrompt(
            this,
            onSuccess = { finishWithSuccess() },
            onError = { message ->
                showError(message)
            }
        )
    }

    private fun showError(message: String) {
        txtError.text = message
        txtError.visibility = View.VISIBLE
        etPin.setText("")
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK)
        finish()
    }
}
