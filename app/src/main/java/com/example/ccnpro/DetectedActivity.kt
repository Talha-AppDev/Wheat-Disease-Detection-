package com.example.ccnpro

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
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

    // Function to check internet connectivity
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Function to show the default Android No Internet dialog
    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.nowifi)
            .setTitle("No Internet Connection")
            .setMessage("Please turn on your Internet connection.")
            .setCancelable(false)
            .setPositiveButton("Wi-Fi Settings") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Mobile Data") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun uploadImageToApi(imageFile: File) {

        // Check for internet connection
        if (!isInternetAvailable()) {
            showNoInternetDialog()
        }
        else
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
                // Convert message to lowercase for comparison and consistency.
                val responseLabel = message?.lowercase() ?: "unknown"

                // Update the result text view.
                if (responseLabel != "healthy") {
                    binding.resultTextView.text = "Disease: $responseLabel"
                } else {
                    binding.resultTextView.text = "Healthy"
                }
                binding.search.visibility = View.VISIBLE
                binding.search.setClickable(true)
                binding.search.setOnClickListener { v ->
                    try {
                        // Encode the search query to ensure it's URL-safe
                        val query = URLEncoder.encode(responseLabel + " wheat plant", "UTF-8")
                        // Construct the Google search URL with the query parameter
                        val url = "https://www.google.com/search?q=$query"
                        // Create an intent to view the URL
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )
                        startActivity(intent)
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                }
                // Update the description text view with detailed info.
                updateWheatCondition(responseLabel, binding.descrptiontext)
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


    fun updateWheatCondition(response: String, descrptiontext: TextView) {
        descrptiontext.visibility = View.VISIBLE
        when (response.lowercase()) {
            "aphid" -> descrptiontext.text = "Aphid:\nSmall sap-sucking insects causing yellowing and stunted growth.\nMonitor for rapid spread."
            "black rust" -> descrptiontext.text = "Black Rust:\nFungal disease with dark, rust-colored pustules.\nCan weaken the plant if untreated."
            "blast" -> descrptiontext.text = "Blast:\nFungal infection that forms lesions on leaves and spikes.\nMay lead to reduced yield."
            "brown rust" -> descrptiontext.text = "Brown Rust:\nRust-colored spots on leaves reduce photosynthesis.\nEarly detection is important."
            "common root rot" -> descrptiontext.text = "Common Root Rot:\nFungal disease attacking the roots.\nLeads to poor nutrient uptake and stunted growth."
            "fusarium head blight" -> descrptiontext.text = "Fusarium Head Blight:\nAffects wheat heads, causing shriveled kernels.\nRisk of mycotoxin contamination."
            "healthy" -> descrptiontext.text = "Healthy:\nThe wheat plant shows no disease symptoms.\nKeep up regular monitoring."
            "leaf blight" -> descrptiontext.text = "Leaf Blight:\nCauses necrotic lesions on leaves.\nReduces overall plant vigor if severe."
            "mildew" -> descrptiontext.text = "Mildew:\nFungal infection with a powdery white coating on leaves.\nMay affect photosynthesis if widespread."
            "mite" -> descrptiontext.text = "Mite:\nTiny pests feeding on plant sap.\nResults in discoloration and potential leaf drop."
            "septoria" -> descrptiontext.text = "Septoria:\nFungal leaf spot disease with dark lesions.\nCan lead to early defoliation."
            "smut" -> descrptiontext.text = "Smut:\nFungal disease producing dark, powdery spores on grains.\nAffects grain quality and yield."
            "stem fly" -> descrptiontext.text = "Stem fly:\nInsect pest that damages stems.\nMay cause lodging and weakened plant structure."
            "tan spot" -> descrptiontext.text = "Tan spot:\nCauses tan lesions on leaves that may merge.\nSignificantly reduces photosynthetic area."
            "yellow rust" -> descrptiontext.text = "Yellow Rust:\nFungal infection with yellowish pustules on leaves.\nReduces overall plant vigor."
            else -> descrptiontext.text = "Unknown condition:\nPlease verify the diagnosis or input value."
        }
    }

}
