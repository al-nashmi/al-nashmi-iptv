package com.mytv.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var info: TextView
    private lateinit var error: TextView
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { info.visibility = View.GONE }
    private var retries = 0
    private var resumePosition = 0L

    private val live get() = PlayQueue.live

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        info = findViewById(R.id.info)
        error = findViewById(R.id.error)
        playerView.useController = !live
        if (PlayQueue.items.isEmpty()) finish()
    }

    override fun onStart() {
        super.onStart()
        if (PlayQueue.items.isEmpty()) return
        val dataSource = OkHttpDataSource.Factory(Net.client)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .build()
        p.setAudioAttributes(AudioAttributes.DEFAULT, true)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    error.visibility = View.GONE
                } else if (state == Player.STATE_ENDED && !live) {
                    next(1)
                }
            }

            override fun onPlayerError(e: PlaybackException) = handleError(e)
        })
        playerView.player = p
        player = p
        play(resumePosition)
    }

    override fun onStop() {
        super.onStop()
        player?.let { resumePosition = if (live) 0 else it.currentPosition; it.release() }
        player = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun play(startAt: Long = 0) {
        val p = player ?: return
        val item = PlayQueue.items[PlayQueue.index]
        val url = Repo.playUrl(item)
        val media = MediaItem.Builder().setUri(url).apply {
            if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        error.visibility = View.GONE
        p.setMediaItem(media, startAt)
        p.prepare()
        p.playWhenReady = true
        showInfo()
    }

    private fun handleError(e: PlaybackException) {
        val p = player ?: return
        // Live streams drop now and then; retry a few times before showing an error.
        if (live && retries < 3) {
            retries++
            handler.postDelayed({ p.seekToDefaultPosition(); p.prepare() }, 2000)
            return
        }
        error.text = getString(R.string.play_error, e.errorCodeName)
        error.visibility = View.VISIBLE
    }

    private fun next(step: Int) {
        val size = PlayQueue.items.size
        if (size == 0) return
        if (!live && PlayQueue.index + step !in 0 until size) return
        PlayQueue.index = (PlayQueue.index + step + size) % size
        retries = 0
        play()
    }

    private fun showInfo() {
        val item = PlayQueue.items[PlayQueue.index]
        info.text = if (live) "${PlayQueue.index + 1}  ${item.name}" else item.name
        info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, 4000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (live) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { next(1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { next(-1); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> { showInfo(); return true }
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> { next(1); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { next(-1); return true }
            }
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }
}
