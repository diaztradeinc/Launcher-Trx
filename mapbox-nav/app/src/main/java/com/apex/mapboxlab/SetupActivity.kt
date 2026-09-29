package com.apex.mapboxlab

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = UiScale.context(this)
        val prefs = getSharedPreferences("mapbox-setup", MODE_PRIVATE)
        val body = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun label(text: String, size: Float) = TextView(ui).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12); body.addView(this)
        }
        label("TRX APEX · MAPBOX LAB", 24f)
        label("0.1.0 · Device-test prototype", 16f)
        label("Enter your Mapbox public access token (starts with pk.). It stays in this app on this device. Do not enter a secret token. Maps, search and route simulation require internet and use your Mapbox account.", 16f)
        val token = EditText(ui).apply {
            hint = "Mapbox public token: pk.…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setText(prefs.getString("public-token", getString(R.string.mapbox_public_token)))
            tag = "public-token"
        }
        body.addView(token)
        fun open(simulation: Boolean) {
            val value = token.text.toString().trim()
            if (!PrototypePolicy.publicTokenValid(value)) {
                token.error = "Enter a public Mapbox token beginning pk. (not sk.)."
                return
            }
            prefs.edit().putString("public-token", value).apply()
            startActivity(Intent(this, NavigationActivity::class.java).putExtra("simulation", simulation))
        }
        body.addView(Button(ui).apply { text = "Open 3D route simulation"; tag = "open-simulation"; setOnClickListener { open(true) } })
        body.addView(Button(ui).apply { text = "Open live GPS test"; setOnClickListener { open(false) } })
        body.addView(Button(ui).apply { text = "Forget saved token"; setOnClickListener { prefs.edit().remove("public-token").apply(); token.text.clear() } })
        label("Simulation is labeled throughout. Live mode requires precise location and pauses when this screen is backgrounded. No background guidance in this first prototype.\n\nUses the original red pickup proxy from your earlier lab. Detailed 3D lanes, flyovers and a licensed TRX model are not included.", 15f)
        val scroll = ScrollView(ui).apply { setBackgroundColor(Color.rgb(16, 21, 28)); addView(body) }
        setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
