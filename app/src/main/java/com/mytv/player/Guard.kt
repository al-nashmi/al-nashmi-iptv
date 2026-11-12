package com.mytv.player

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Checks a movie or series against the age settings before it opens. */
object Guard {

    fun open(activity: AppCompatActivity, item: Item, proceed: () -> Unit) {
        if (item.kind != Kind.MOVIES && item.kind != Kind.SERIES) return proceed()
        if (Prefs.maxAge >= 18 && !Prefs.warnMature) return proceed()
        Toast.makeText(activity, R.string.checking_rating, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val rating = withContext(Dispatchers.IO) { AgeRating.lookup(item) }
            decide(activity, rating, proceed)
        }
    }

    private fun decide(activity: AppCompatActivity, r: Rating, proceed: () -> Unit) {
        val limit = Prefs.maxAge
        val age = r.age
        when {
            age != null && age > limit ->
                blocked(activity, activity.getString(R.string.rating_too_high, r.label, age, limit), proceed)
            age == null && limit < 18 -> when (Prefs.unknownPolicy) {
                "block" -> blocked(activity, activity.getString(R.string.rating_unknown), proceed)
                "warn" -> warn(activity, activity.getString(R.string.rating_unknown_warn), proceed)
                else -> proceed()
            }
            age != null && age >= 13 && Prefs.warnMature ->
                warn(activity, activity.getString(R.string.rating_warn, r.label, age), proceed)
            else -> {
                if (age != null) Toast.makeText(activity, activity.getString(R.string.rating_ok, r.label), Toast.LENGTH_SHORT).show()
                proceed()
            }
        }
    }

    private fun warn(activity: AppCompatActivity, message: String, proceed: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.warning)
            .setMessage(message)
            .setPositiveButton(R.string.continue_) { _, _ -> proceed() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun blocked(activity: AppCompatActivity, message: String, proceed: () -> Unit) {
        val b = AlertDialog.Builder(activity)
            .setTitle(R.string.blocked)
            .setNegativeButton(R.string.cancel, null)
        if (Prefs.pin != null) {
            b.setMessage(message)
            b.setPositiveButton(R.string.unlock_with_pin) { _, _ -> Pin.ask(activity, proceed) }
        } else {
            b.setMessage(message + "\n\n" + activity.getString(R.string.set_pin_to_unlock))
        }
        b.show()
    }
}

/** Parent PIN prompts. */
object Pin {
    private fun field(activity: AppCompatActivity, hint: Int) = EditText(activity).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setHint(hint)
    }

    private fun wrap(activity: AppCompatActivity, vararg views: EditText) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        views.forEach { addView(it) }
    }

    /** Runs [onOk] after the correct PIN is entered (or straight away when no PIN is set). */
    fun ask(activity: AppCompatActivity, onOk: () -> Unit) {
        val pin = Prefs.pin ?: return onOk()
        val input = field(activity, R.string.pin_hint)
        AlertDialog.Builder(activity)
            .setTitle(R.string.enter_pin)
            .setView(wrap(activity, input))
            .setPositiveButton(R.string.ok) { _, _ ->
                if (input.text.toString() == pin) onOk()
                else Toast.makeText(activity, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    fun create(activity: AppCompatActivity, onDone: () -> Unit) {
        val first = field(activity, R.string.new_pin_hint)
        val second = field(activity, R.string.confirm_pin_hint)
        AlertDialog.Builder(activity)
            .setTitle(R.string.set_pin)
            .setView(wrap(activity, first, second))
            .setPositiveButton(R.string.ok) { _, _ ->
                val p = first.text.toString()
                when {
                    p.length < 4 -> Toast.makeText(activity, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
                    p != second.text.toString() -> Toast.makeText(activity, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                    else -> { Prefs.pin = p; onDone() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        first.requestFocus()
    }
}
