package com.example.ccnpro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ccnpro.databinding.ActivityDetectedBinding
import com.example.ccnpro.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class DetectedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetectedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the file path of the captured image passed from GetimgActivity.
        val imagePath: String? = intent.getStringExtra("IMAGE_PATH")

        if (imagePath != null) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                // Decode and display the image.
                val finalBitmap: Bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                binding.imageView.setImageBitmap(finalBitmap)

                // Post the image to the API using Retrofit.
                postImageWithRetrofit(imageFile)
            } else {
                binding.resultTextView.text = "Image file does not exist."
            }
        } else {
            binding.resultTextView.text = "No image data received."
        }
    }

    /**
     * Posts the given image file to the API endpoint using Retrofit.
     */
    private fun postImageWithRetrofit(imageFile: File) {
        // Create the multipart body part from the image file.
        val multipartBody = createMultipartBody(imageFile)

        // Launch a coroutine to execute the network request.
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.uploadImage(multipartBody)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    // Update the UI on the main thread.
                    runOnUiThread {
                        binding.resultTextView.text = "Prediction: ${apiResponse?.prediction}"
                    }
                } else {
                    runOnUiThread {
                        binding.resultTextView.text = "Error: ${response.errorBody()?.string()}"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.resultTextView.text = "Exception: ${e.localizedMessage}"
                }
            }
        }
    }

    /**
     * Converts a File into a MultipartBody.Part suitable for a multipart/form-data request.
     */
    private fun createMultipartBody(file: File, formFieldName: String = "file"): MultipartBody.Part {
        val mediaType = "image/png".toMediaTypeOrNull()  // Adjust media type if needed.
        val requestBody = file.asRequestBody(mediaType)
        return MultipartBody.Part.createFormData(formFieldName, file.name, requestBody)
    }
}
