package com.example.ccnpro

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.ccnpro.Fragments.WheatFragment
import com.example.ccnpro.databinding.ActivityGetimgBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class GetimgActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGetimgBinding
    private val CAMERA_REQUEST_CODE = 100
    private val GALLERY_REQUEST_CODE = 101
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetimgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Attach the fragment (default to WheatFragment)
        val fragmentType = intent.getStringExtra("FRAGMENT_TYPE")
        val fragment: Fragment = when (fragmentType) {
            "WheatFragment" -> WheatFragment()
            else -> WheatFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.cvbanner, fragment)
            .commit()

        // Directly launch camera or gallery without permission checks.
        binding.camera.setOnClickListener {
            Log.d("GetimgActivity", "Camera card clicked")
            Toast.makeText(this, "Camera card clicked", Toast.LENGTH_SHORT).show()
            dispatchTakePictureIntent()
        }

        binding.gallery.setOnClickListener {
            Log.d("GetimgActivity", "Gallery card clicked")
            Toast.makeText(this, "Gallery card clicked", Toast.LENGTH_SHORT).show()
            openGallery()
        }
    }

    /**
     * Launches the camera to take a picture.
     */
    private fun dispatchTakePictureIntent() {
        Log.d("GetimgActivity", "Dispatching camera intent")
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                Log.e("GetimgActivity", "Error creating image file", ex)
                null
            }
            if (photoFile != null) {
                currentPhotoPath = photoFile.absolutePath
                try {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.ccnpro.fileprovider", // Must match your Manifest.
                        photoFile
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                } catch (ex: Exception) {
                    Log.e("GetimgActivity", "Error getting URI from FileProvider", ex)
                    Toast.makeText(this, "Error launching camera", Toast.LENGTH_SHORT).show()
                }
                try {
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                } catch (ex: Exception) {
                    Log.e("GetimgActivity", "Error starting camera activity", ex)
                    Toast.makeText(this, "Error starting camera activity", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Creates a temporary image file in the app's external files directory.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
    }

    /**
     * Opens the gallery so the user can select an image.
     */
    private fun openGallery() {
        Log.d("GetimgActivity", "Opening gallery")
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (galleryIntent.resolveActivity(packageManager) != null) {
            try {
                startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)
            } catch (ex: Exception) {
                Log.e("GetimgActivity", "Error starting gallery activity", ex)
                Toast.makeText(this, "Error opening gallery", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("GetimgActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> handleCameraResult()
                GALLERY_REQUEST_CODE -> data?.data?.let { handleGalleryResult(it) }
            }
        } else {
            Log.d("GetimgActivity", "Result was not OK")
        }
    }

    /**
     * Handles the result when a photo is taken.
     */
    private fun handleCameraResult() {
        Log.d("GetimgActivity", "Handling camera result")
        currentPhotoPath?.let { path ->
            val imageFile = File(path)
            if (imageFile.exists()) {
                showImagePreviewDialog(filePath = path)
            } else {
                Toast.makeText(this, "Image file does not exist", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
    }

    /**
     * Handles the result when an image is selected from the gallery.
     */
    private fun handleGalleryResult(uri: Uri) {
        Log.d("GetimgActivity", "Handling gallery result: $uri")
        // Convert the URI to a file path if possible.
        val filePath = getRealPathFromURI(uri)
        if (filePath != null) {
            currentPhotoPath = filePath
        }
        showImagePreviewDialog(uri = uri)
    }

    /**
     * Converts a content URI to a file path.
     */
    private fun getRealPathFromURI(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        Log.d("GetimgActivity", "Real path from URI: $path")
        return path
    }

    /**
     * Displays a preview dialog of the selected or captured image.
     */
    private fun showImagePreviewDialog(filePath: String? = null, uri: Uri? = null) {
        Log.d("GetimgActivity", "Showing image preview dialog")
        val dialogView = layoutInflater.inflate(R.layout.ip, null)
        val previewImageView = dialogView.findViewById<android.widget.ImageView>(R.id.previewImageView)
        when {
            filePath != null -> loadScaledBitmap(filePath, previewImageView)
            uri != null -> previewImageView.setImageURI(uri)
            else -> {
                Toast.makeText(this, "No image available", Toast.LENGTH_SHORT).show()
                return
            }
        }
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                moveToNextStep()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                cleanUpTempFile()
            }
            .show()
    }

    /**
     * Loads a scaled bitmap from the provided file path into the ImageView.
     */
    private fun loadScaledBitmap(filePath: String, imageView: android.widget.ImageView) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(filePath, options)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("GetimgActivity", "Error loading scaled bitmap", e)
        }
    }

    /**
     * Calculates the sample size for scaling the image.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Proceeds to the next activity by passing along the image path.
     */
    private fun moveToNextStep() {
        currentPhotoPath?.let { path ->
            Log.d("GetimgActivity", "Moving to next step with image path: $path")
            val intent = Intent(this, DetectedActivity::class.java)
            intent.putExtra("IMAGE_PATH", path)
            startActivity(intent)
        } ?: Toast.makeText(this, "No image to proceed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Deletes the temporary file if the user cancels.
     */
    private fun cleanUpTempFile() {
        currentPhotoPath?.let {
            val file = File(it)
            if (file.exists()) {
                file.delete()
                Log.d("GetimgActivity", "Temporary file deleted")
            }
            currentPhotoPath = null
        }
    }

    /**
     * Example method to change the color of UI elements.
     */
    fun setColor(colorX: Int, colorY: Int) {
        binding.camera.setCardBackgroundColor(colorX)
        binding.gallery.setCardBackgroundColor(colorX)
        binding.tvCamera.setTextColor(colorY)
        binding.tvGallery.setTextColor(colorY)
    }
}
