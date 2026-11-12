package com.mytv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesActivity : AppCompatActivity() {

    private lateinit var seasonAdapter: RowAdapter<Season>
    private lateinit var episodeAdapter: RowAdapter<Item>
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        val seriesId = intent.getStringExtra("id") ?: return finish()
        findViewById<TextView>(R.id.title).text = intent.getStringExtra("name")
        status = findViewById(R.id.status)

        val seasonList = findViewById<RecyclerView>(R.id.seasons)
        val episodeList = findViewById<RecyclerView>(R.id.episodes)
        seasonAdapter = RowAdapter(
            label = { it.name },
            onFocus = { pos, s -> showSeason(pos, s) },
            onClick = { pos, s ->
                showSeason(pos, s)
                episodeList.post { episodeList.getChildAt(0)?.requestFocus() }
            },
        )
        episodeAdapter = RowAdapter(
            label = { it.name },
            image = { it.logo },
            onClick = { pos, _ ->
                PlayQueue.items = episodeAdapter.items
                PlayQueue.index = pos
                PlayQueue.live = false
                startActivity(Intent(this, PlayerActivity::class.java))
            },
        )
        seasonList.layoutManager = LinearLayoutManager(this)
        seasonList.adapter = seasonAdapter
        episodeList.layoutManager = LinearLayoutManager(this)
        episodeList.adapter = episodeAdapter

        status.setText(R.string.loading)
        lifecycleScope.launch {
            try {
                val seasons = withContext(Dispatchers.IO) { Repo.seriesInfo(seriesId) }
                if (seasons.isEmpty()) {
                    status.setText(R.string.empty)
                    return@launch
                }
                seasonAdapter.items = seasons
                showSeason(0, seasons[0])
                seasonList.post { seasonList.getChildAt(0)?.requestFocus() }
            } catch (e: Exception) {
                status.text = Net.describe(e)
            }
        }
    }

    private fun showSeason(pos: Int, season: Season) {
        if (seasonAdapter.selected == pos) return
        seasonAdapter.selected = pos
        episodeAdapter.items = season.episodes
        status.visibility = View.GONE
    }
}
