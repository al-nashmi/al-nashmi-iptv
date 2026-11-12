package com.mytv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = Repo.account
        if (account == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val isM3u = account.type == Account.TYPE_M3U
        findViewById<TextView>(R.id.accountInfo).text = buildString {
            append(if (isM3u) "قائمة M3U" else "Xtream: ${account.user}")
            if (account.info.isNotEmpty()) append("  •  ").append(account.info)
        }

        val live = findViewById<Button>(R.id.btnLive)
        val movies = findViewById<Button>(R.id.btnMovies)
        val series = findViewById<Button>(R.id.btnSeries)
        live.setOnClickListener { open(Kind.LIVE) }
        movies.setOnClickListener { open(Kind.MOVIES) }
        series.setOnClickListener { open(Kind.SERIES) }
        if (isM3u) {
            movies.visibility = View.GONE
            series.visibility = View.GONE
        }
        live.requestFocus()

        findViewById<Button>(R.id.btnAccount).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun open(kind: Kind) {
        startActivity(Intent(this, BrowseActivity::class.java).putExtra("kind", kind.name))
    }
}
