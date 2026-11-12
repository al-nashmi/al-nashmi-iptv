package com.mytv.player

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Categories on one side, the streams of the focused category on the other. */
class BrowseActivity : AppCompatActivity() {

    private lateinit var kind: Kind
    private lateinit var catList: RecyclerView
    private lateinit var itemList: RecyclerView
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var catAdapter: RowAdapter<Category>
    private lateinit var itemAdapter: RowAdapter<Item>

    private var categoryItems: List<Item> = emptyList()
    private var currentCategory: String? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        kind = Kind.valueOf(intent.getStringExtra("kind") ?: Kind.LIVE.name)

        findViewById<TextView>(R.id.title).setText(when (kind) {
            Kind.LIVE -> R.string.live
            Kind.MOVIES -> R.string.movies
            else -> R.string.series
        })
        catList = findViewById(R.id.categories)
        itemList = findViewById(R.id.items)
        status = findViewById(R.id.status)
        search = findViewById(R.id.search)

        catAdapter = RowAdapter(
            label = { it.name },
            onFocus = { pos, c -> selectCategory(pos, c, debounce = true) },
            onClick = { pos, c ->
                selectCategory(pos, c, debounce = false)
                itemList.post { itemList.getChildAt(0)?.requestFocus() }
            },
        )
        itemAdapter = RowAdapter(
            label = { it.name },
            image = { it.logo },
            badge = { if (Prefs.isFavorite(it)) "★" else "" },
            onLongClick = { pos, item -> toggleFavorite(pos, item) },
            onClick = { pos, item -> openItem(pos, item) },
        )
        catList.layoutManager = LinearLayoutManager(this)
        catList.adapter = catAdapter
        itemList.layoutManager = LinearLayoutManager(this)
        itemList.adapter = itemAdapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        loadCategories()
    }

    private fun loadCategories() {
        status.setText(R.string.loading)
        status.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val cats = withContext(Dispatchers.IO) { Repo.categories(kind) }
                catAdapter.items = listOf(
                    Category(Repo.CAT_ALL, getString(R.string.all)),
                    Category(Repo.CAT_FAV, getString(R.string.favorites)),
                ) + cats
                selectCategory(0, catAdapter.items[0], debounce = false)
                catList.post { catList.getChildAt(0)?.requestFocus() }
            } catch (e: Exception) {
                status.text = Net.describe(e)
            }
        }
    }

    private fun selectCategory(pos: Int, category: Category, debounce: Boolean) {
        if (category.id == currentCategory) return
        currentCategory = category.id
        catAdapter.selected = pos
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            if (debounce) delay(250)
            status.setText(R.string.loading)
            status.visibility = View.VISIBLE
            try {
                categoryItems = withContext(Dispatchers.IO) { Repo.items(kind, category.id) }
                applyFilter()
            } catch (e: Exception) {
                categoryItems = emptyList()
                itemAdapter.items = emptyList()
                status.text = Net.describe(e)
            }
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        val shown = if (q.isEmpty()) categoryItems else categoryItems.filter { it.name.contains(q, ignoreCase = true) }
        itemAdapter.items = shown
        itemList.scrollToPosition(0)
        if (shown.isEmpty()) {
            status.setText(if (currentCategory == Repo.CAT_FAV && q.isEmpty()) R.string.no_favorites else R.string.empty)
            status.visibility = View.VISIBLE
        } else {
            status.visibility = View.GONE
        }
    }

    private fun openItem(pos: Int, item: Item) = Guard.open(this, item) {
        if (item.kind == Kind.SERIES) {
            startActivity(Intent(this, SeriesActivity::class.java)
                .putExtra("id", item.id)
                .putExtra("name", item.name))
        } else {
            PlayQueue.items = itemAdapter.items
            PlayQueue.index = pos
            PlayQueue.live = item.kind == Kind.LIVE
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }

    private fun toggleFavorite(pos: Int, item: Item) {
        val added = Prefs.toggleFavorite(item)
        Toast.makeText(this, if (added) R.string.fav_added else R.string.fav_removed, Toast.LENGTH_SHORT).show()
        itemAdapter.notifyItemChanged(pos)
    }
}
