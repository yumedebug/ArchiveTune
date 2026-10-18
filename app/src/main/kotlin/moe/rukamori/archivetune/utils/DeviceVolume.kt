/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 端末のメディア音量（[AudioManager.STREAM_MUSIC]）。
 *
 * 全画面プレイヤーの音量スライダーはここへ繋ぐ。音量つまみ・ハードキー・
 * 他アプリのどれで変えられても同じ値を指すため、画面のスライダーと
 * 端末の音量が食い違わない。
 *
 * 再生エンジン（ExoPlayer の `volume`）には触れない。あちらは曲ごとの
 * フェード等が使う内部値で、端末の音量とは別の層である。
 *
 * 値の購読は設定値の監視と短い間隔の読み直しで行う。[sync] はどちらからも
 * 呼ばれるので、自分で書いた直後以外の変化にも追従する。
 */
@Stable
class DeviceVolume internal constructor(
    private val audioManager: AudioManager,
) {
    /** 現在の段数（0 〜 [maxLevel]）。 */
    var level: Int by mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        private set

    /** 最大段数。0 で割らないよう最低 1 に寄せる。 */
    val maxLevel: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    /** 0〜1 の割合。スライダーの位置はこれで表す。 */
    val fraction: Float
        get() = (level.toFloat() / maxLevel.toFloat()).coerceIn(0f, 1f)

    /** 消音（0 段）か。アイコンの出し分けに使う。 */
    val isMuted: Boolean
        get() = level <= 0

    /**
     * 0〜1 の割合で端末音量を書き換える。
     *
     * 端末側の音量パネルは出さない（`flags = 0`）。書いたあとは必ず読み直し、
     * 端末が丸めた値・拒否した値をそのまま表示へ返す。
     */
    fun setFraction(value: Float) {
        val target = (value.coerceIn(0f, 1f) * maxLevel).roundToInt()
        if (target == level) return
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        sync()
    }

    /** 端末から読み直す。 */
    internal fun sync() {
        level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
}

/**
 * 端末のメディア音量を購読する。
 *
 * 端末側で変わった値（音量キー・音量パネル・他アプリ・Bluetooth の絶対音量）を
 * 画面へ反映するために2段構えにする。
 *
 *  - [Settings.System.CONTENT_URI] の監視。音量は `Settings.System` に書かれる
 *    ので、その書き込みで読み直す
 *  - 念のため1秒ごとの読み直し。書かれ方を変える端末（絶対音量など）でも
 *    表示が実際の音量からずれないようにする
 *
 * 値が変わっていなければ Compose の状態は通知しないので、読み直しの負担は
 * その場の1回の読み取りだけである。画面を離れるとどちらも止める（音量は
 * 端末が持つ値なので、画面側から書き戻すものは無い）。
 */
@Composable
fun rememberDeviceVolume(): DeviceVolume {
    val context = LocalContext.current
    val volume =
        remember(context) {
            DeviceVolume(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        }

    DisposableEffect(volume, context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    volume.sync()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        volume.sync()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(volume) {
        while (true) {
            delay(VOLUME_POLL_MS)
            volume.sync()
        }
    }

    return volume
}

/** 音量を読み直す間隔。表示が実際の値からずれないための保険。 */
private const val VOLUME_POLL_MS = 1000L
