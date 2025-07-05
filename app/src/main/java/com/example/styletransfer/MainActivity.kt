
package com.example.styletransfer

import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    private val styleModels = listOf("candy.tflite", "mosaic.tflite", "rain_princess.tflite")
    private var currentModel: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StyleTransferApp() }
    }

    @Composable
    fun StyleTransferApp() {
        var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var styledBitmap by remember { mutableStateOf<Bitmap?>(null) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                selectedBitmap = bitmap
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Select Photo")
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow {
                items(styleModels.size) { idx ->
                    Button(onClick = {
                        applyStyle(styleModels[idx], selectedBitmap)?.let {
                            styledBitmap = it
                        } ?: run {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to apply style",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Text(styleModels[idx].removeSuffix(".tflite"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            styledBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Styled Image",
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: run {
                selectedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Original Image",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    private fun applyStyle(modelFile: String, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        try {
            currentModel = Interpreter(loadModelFile(modelFile))
            val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4)
                .order(ByteOrder.nativeOrder())
            for (y in 0 until 256) {
                for (x in 0 until 256) {
                    val pixel = scaled.getPixel(x, y)
                    inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f)
                    inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255f)
                }
            }
            val output = Array(1) { Array(256) { Array(256) { FloatArray(3) } } }
            currentModel!!.run(inputBuffer, output)
            val styledBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            for (y in 0 until 256) {
                for (x in 0 until 256) {
                    val r = (output[0][y][x][0] * 255).toInt().coerceIn(0, 255)
                    val g = (output[0][y][x][1] * 255).toInt().coerceIn(0, 255)
                    val b = (output[0][y][x][2] * 255).toInt().coerceIn(0, 255)
                    styledBmp.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
                }
            }
            return styledBmp
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val afd = assets.openFd(fileName)
        val fis = afd.createInputStream()
        val channel = fis.channel
        return channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}
