package com.mytv.player

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Parental settings; MainActivity only opens it after the PIN is entered. */
class ParentalActivity : AppCompatActivity() {

    private val ages = mapOf(R.id.age0 to 0, R.id.age7 to 7, R.id.age12 to 12, R.id.age15 to 15, R.id.age18 to 18)
    private val policies = mapOf(R.id.unkAllow to "allow", R.id.unkWarn to "warn", R.id.unkBlock to "block")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parental)
        val filter = findViewById<CheckBox>(R.id.cbFilter)
        val music = findViewById<CheckBox>(R.id.cbMusic)
        val warn = findViewById<CheckBox>(R.id.cbWarn)
        val ageGroup = findViewById<RadioGroup>(R.id.ageGroup)
        val unknownGroup = findViewById<RadioGroup>(R.id.unknownGroup)
        val words = findViewById<EditText>(R.id.customWords)
        val tmdb = findViewById<EditText>(R.id.tmdbKey)

        filter.isChecked = Prefs.filterOn
        music.isChecked = Prefs.blockMusic
        warn.isChecked = Prefs.warnMature
        ageGroup.check(ages.entries.firstOrNull { it.value == Prefs.maxAge }?.key ?: R.id.age18)
        unknownGroup.check(policies.entries.firstOrNull { it.value == Prefs.unknownPolicy }?.key ?: R.id.unkWarn)
        words.setText(Prefs.customWords)
        tmdb.setText(Prefs.tmdbKey)
        filter.requestFocus()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.filterOn = filter.isChecked
            Prefs.blockMusic = music.isChecked
            Prefs.warnMature = warn.isChecked
            Prefs.maxAge = ages[ageGroup.checkedRadioButtonId] ?: 18
            Prefs.unknownPolicy = policies[unknownGroup.checkedRadioButtonId] ?: "warn"
            Prefs.customWords = words.text.toString()
            Prefs.tmdbKey = tmdb.text.toString().trim()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.btnPin).setOnClickListener { Pin.create(this) {} }
    }
}
