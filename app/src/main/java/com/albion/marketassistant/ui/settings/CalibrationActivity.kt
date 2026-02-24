package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.db.CalibrationDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalibrationActivity : AppCompatActivity() {

    private val db by lazy { CalibrationDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Reusing main layout for simplicity

        // In a full version, you'd have input fields here to change numbers.
        // For now, this button just resets your coordinates to the defaults.
        findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            saveDefaultCalibration()
        }
    }

    private fun saveDefaultCalibration() {
        CoroutineScope(Dispatchers.IO).launch {
            db.calibrationDao().insertCalibration(CalibrationData())
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CalibrationActivity, "Calibration Reset to Defaults!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
