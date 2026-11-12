package com.mytv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var typeGroup: RadioGroup
    private lateinit var xtreamBox: View
    private lateinit var m3uBox: View
    private lateinit var server: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var m3u: EditText
    private lateinit var hls: RadioButton
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView
    private lateinit var loginBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        typeGroup = findViewById(R.id.typeGroup)
        xtreamBox = findViewById(R.id.xtreamBox)
        m3uBox = findViewById(R.id.m3uBox)
        server = findViewById(R.id.server)
        user = findViewById(R.id.user)
        pass = findViewById(R.id.pass)
        m3u = findViewById(R.id.m3uUrl)
        hls = findViewById(R.id.rbHls)
        progress = findViewById(R.id.progress)
        error = findViewById(R.id.error)
        loginBtn = findViewById(R.id.btnLogin)

        Prefs.load()?.let { a ->
            typeGroup.check(if (a.type == Account.TYPE_M3U) R.id.rbM3u else R.id.rbXtream)
            server.setText(a.server)
            user.setText(a.user)
            pass.setText(a.pass)
            m3u.setText(a.m3uUrl)
            if (a.format == "m3u8") hls.isChecked = true
        }
        typeGroup.setOnCheckedChangeListener { _, _ -> updateBoxes() }
        updateBoxes()
        loginBtn.setOnClickListener { login() }
    }

    private fun isM3u() = typeGroup.checkedRadioButtonId == R.id.rbM3u

    private fun updateBoxes() {
        xtreamBox.visibility = if (isM3u()) View.GONE else View.VISIBLE
        m3uBox.visibility = if (isM3u()) View.VISIBLE else View.GONE
    }

    private fun withScheme(url: String): String {
        val u = url.trim()
        return if (u.startsWith("http://", true) || u.startsWith("https://", true)) u else "http://$u"
    }

    private fun login() {
        val account = if (isM3u()) {
            if (m3u.text.isBlank()) return showError("اكتب رابط قائمة M3U.")
            Account(Account.TYPE_M3U, m3uUrl = withScheme(m3u.text.toString()))
        } else {
            if (server.text.isBlank() || user.text.isBlank() || pass.text.isBlank()) {
                return showError("اكتب رابط السيرفر واسم المستخدم وكلمة المرور.")
            }
            Account(
                Account.TYPE_XTREAM,
                server = withScheme(server.text.toString()),
                user = user.text.toString().trim(),
                pass = pass.text.toString().trim(),
                format = if (hls.isChecked) "m3u8" else "ts",
            )
        }

        error.visibility = View.GONE
        progress.visibility = View.VISIBLE
        loginBtn.isEnabled = false
        lifecycleScope.launch {
            val previous = Repo.account
            val result = withContext(Dispatchers.IO) {
                try {
                    Repo.account = account
                    Repo.login()
                } catch (e: Exception) {
                    Net.describe(e) to ""
                }
            }
            progress.visibility = View.GONE
            loginBtn.isEnabled = true
            val (err, info) = result
            if (err != null) {
                Repo.account = previous
                showError(err)
            } else {
                val saved = account.copy(info = info)
                Prefs.save(saved)
                Repo.account = saved
                startActivity(Intent(this@LoginActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
    }

    private fun showError(msg: String) {
        error.text = msg
        error.visibility = View.VISIBLE
    }
}
