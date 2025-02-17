package com.example.ccnpro

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ccnpro.databinding.ActivityDetectedBinding
import com.example.ccnpro.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DetectedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetectedBinding
    private var imageFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize UI elements visibility.
        binding.progressBar.visibility = View.VISIBLE
        binding.imageView.visibility = View.GONE
        binding.resultTextView.visibility = View.GONE

        imageFilePath = intent.getStringExtra("IMAGE_PATH")
        imageFilePath?.let { path ->
            val imageFile = File(path)
            if (imageFile.exists()) {
                loadAndDisplayImage(path)
                uploadImageToApi(imageFile)
            } else {
                handleErrorState("Image file not found")
            }
        } ?: run {
            handleErrorState("No image path provided")
        }
    }

    private fun loadAndDisplayImage(filePath: String) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(filePath, this)
                inSampleSize = calculateInSampleSize(this, 1024, 1024)
                inJustDecodeBounds = false
            }

            BitmapFactory.decodeFile(filePath, options)?.let { bitmap ->
                binding.imageView.apply {
                    visibility = View.VISIBLE
                    setImageBitmap(bitmap)
                }
            } ?: handleErrorState("Failed to decode image")
        } catch (e: Exception) {
            handleErrorState("Error loading image: ${e.localizedMessage}")
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun uploadImageToApi(imageFile: File) {
        lifecycleScope.launch {
            try {
                // Show loading state.
                binding.progressBar.visibility = View.VISIBLE
                binding.resultTextView.visibility = View.GONE

                val mediaType = when (imageFile.extension.lowercase(Locale.getDefault())) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    else -> "image/*"
                }.toMediaTypeOrNull()

                val requestFile = imageFile.asRequestBody(mediaType)
                // Ensure that the form-data key "file" matches what your API expects.
                val imagePart = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.uploadImage(imagePart)
                }

                if (response.isSuccessful) {
                    // Extract the 'disease' field from the response.
                    handleApiResponse(true, response.body()?.disease)
                } else {
                    // Get error details from the API.
                    val errorDetails = response.errorBody()?.string() ?: response.message()
                    handleApiResponse(false, errorDetails)
                }
            } catch (e: Exception) {
                handleNetworkError(e)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.resultTextView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * If success is true, message should contain the disease.
     * If false, message is the error details.
     */
    private fun handleApiResponse(success: Boolean, message: String?) {
        runOnUiThread {
            if (success) {
                val resultText = message?.let { "Disease: $it" } ?: "No prediction available"
                binding.resultTextView.text = resultText
            } else {
                binding.resultTextView.text = "API request failed: $message"
            }
        }
    }

    private fun handleNetworkError(exception: Exception) {
        runOnUiThread {
            val errorMessage = when (exception) {
                is java.net.ConnectException -> "Connection failed. Check your internet"
                is java.net.SocketTimeoutException -> "Request timed out"
                else -> "Error: ${exception.localizedMessage}"
            }
            binding.resultTextView.text = errorMessage
        }
    }

    private fun handleErrorState(message: String) {
        runOnUiThread {
            binding.imageView.apply {
                visibility = View.VISIBLE
                setImageResource(android.R.drawable.ic_dialog_alert)
            }
            binding.resultTextView.text = message
            binding.progressBar.visibility = View.GONE
            binding.resultTextView.visibility = View.VISIBLE
        }
    }
}
