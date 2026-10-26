package pl.pickpack.panel.data.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import pl.pickpack.panel.R
import java.util.ArrayDeque

/**
 * Shared FIFO audio queue for imienny + kurier alerts. Never overlaps tracks.
 */
class AlertSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Int>()
    private var player: MediaPlayer? = null
    private var playing = false

    fun enqueueUwagaImienny() = enqueue(R.raw.uwaga_imienny_status)

    fun enqueueOdebrano() = enqueue(R.raw.odebrano)

    fun enqueueCzekaNaOdbior() = enqueue(R.raw.czeka_na_odbior)

    /** Play sound for new courier button state (isActive = czeka / else odebrano). */
    fun enqueueCourierToggle(nowActive: Boolean) {
        if (nowActive) enqueueCzekaNaOdbior() else enqueueOdebrano()
    }

    fun enqueueKurier(courierId: String): Boolean {
        val res = kurierRawRes(courierId) ?: return false
        enqueue(res)
        return true
    }

    fun enqueue(rawResId: Int) {
        mainHandler.post {
            queue.addLast(rawResId)
            if (!playing) playNextLocked()
        }
    }

    /** Immediate test — queued like other alerts. */
    fun playUwagaImienny(): Boolean {
        enqueueUwagaImienny()
        return true
    }

    fun isPlaying(): Boolean = playing

    fun release() {
        mainHandler.post {
            queue.clear()
            releasePlayer()
            playing = false
        }
    }

    private fun playNextLocked() {
        val next = queue.pollFirst()
        if (next == null) {
            playing = false
            return
        }
        playing = true
        try {
            releasePlayer()
            val mp = MediaPlayer.create(appContext, next)
            if (mp == null) {
                playing = false
                playNextLocked()
                return
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener {
                releasePlayer()
                playing = false
                playNextLocked()
            }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                playing = false
                playNextLocked()
                true
            }
            player = mp
            mp.start()
        } catch (_: Exception) {
            releasePlayer()
            playing = false
            playNextLocked()
        }
    }

    private fun releasePlayer() {
        try {
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    companion object {
        fun kurierRawRes(courierId: String): Int? = when (courierId) {
            "orlen" -> R.raw.kurier_orlen
            "dhl" -> R.raw.kurier_dhl
            "olza" -> R.raw.kurier_olza
            "inpost" -> R.raw.kurier_inpost
            "dpd" -> R.raw.kurier_dpd
            "one_kurier" -> R.raw.kurier_one_kurier
            else -> null
        }
    }
}
