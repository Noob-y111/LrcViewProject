package com.example.lrcviewproject

import android.media.MediaPlayer
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.lrcviewproject.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val mediaPlayer = MediaPlayer()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.lrcView.toDealWithTheLyrics {
            val list = ArrayList<String>()
            try {
                val inputStream = applicationContext.assets.open("Despai - Wake (抖音原版).lrc")
                val reader = InputStreamReader(inputStream, "GBK")
                val bufferReader = BufferedReader(reader)
                bufferReader.forEachLine {
                    list.add(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@toDealWithTheLyrics list
        }

        binding.btn.setOnClickListener {
            val data = applicationContext.assets.openFd("Despai - Wake (抖音原版).mp3")
            mediaPlayer.setDataSource(data)
            mediaPlayer.prepareAsync()
        }

        binding.lrcView.onClickLine {
            mediaPlayer.seekTo(it)
        }

        var isRunning: Boolean
        mediaPlayer.apply {
            setOnPreparedListener {
                Toast.makeText(applicationContext, "开始播放", Toast.LENGTH_SHORT).show()
                it.start()
                isRunning = true
                thread {
                    while (isRunning) {
                        binding.lrcView.seekTo(mediaPlayer.currentPosition)
                        Thread.sleep(100)
                    }
                }
            }
            setOnCompletionListener {
                mediaPlayer.reset()
                mediaPlayer.release()
                isRunning = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.reset()
        mediaPlayer.release()
    }
}