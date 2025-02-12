package com.example.ccnpro

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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

    // Request codes for camera and gallery.
    private val CAMERA_REQUEST_CODE = 100
    private val GALLERY_REQUEST_CODE = 101

    // Variable to store the path of the captured image file.
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using view binding.
        binding = ActivityGetimgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the fragment type from the intent extra.
        // Default to WheatFragment if the extra is missing or unrecognized.
        val fragmentType = intent.getStringExtra("FRAGMENT_TYPE")
        val fragment: Fragment = when (fragmentType) {
            "WheatFragment" -> WheatFragment()
            else -> WheatFragment() // Default fragment to avoid exception.
        }

        // Load the fragment into the container with ID cvbanner.
        supportFragmentManager.beginTransaction()
            .replace(R.id.cvbanner, fragment)
            .commit()

        // Set up the Camera button click listener.
        binding.camera.setOnClickListener {
            dispatchTakePictureIntent()
        }

        // Set up the Gallery button click listener.
        binding.gallery.setOnClickListener {
            val galleryIntent = Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)
        }
    }

    /**
     * Launches the camera intent to capture a full-resolution image.
     */
    private fun dispatchTakePictureIntent() {
        val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            // Create the file where the photo should be saved.
            val photoFile: File? = try {
                createImageFile().also { file ->
                    currentPhotoPath = file.absolutePath
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
                null
            }

            photoFile?.let {
                // Get a content URI for the photo file using FileProvider.
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider", // Must match your manifest configuration.
                    it
                )
                cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
            }
        }
    }

    /**
     * Creates a temporary image file to store the captured photo.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name with a timestamp.
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", // Prefix.
            ".jpg",               // Suffix.
            storageDir            // Directory.
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    // The full-resolution image is saved at currentPhotoPath.
                    currentPhotoPath?.let { path ->
                        val imageFile = File(path)
                        if (imageFile.exists()) {
                            // Decode the image file into a Bitmap (for preview purposes only).
                            val fullResBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            showImagePreviewDialog(photo = fullResBitmap)
                        }
                    }
                }
                GALLERY_REQUEST_CODE -> {
                    // Retrieve the selected image URI from the gallery.
                    val selectedImageUri: Uri? = data?.data
                    selectedImageUri?.let {
                        showImagePreviewDialog(uri = it)
                    }
                }
            }
        }
    }

    /**
     * Displays a preview dialog for the selected image.
     *
     * @param photo A Bitmap of the image (for camera capture preview).
     * @param uri   The URI of the image (for gallery selection preview).
     */
    private fun showImagePreviewDialog(photo: android.graphics.Bitmap? = null, uri: Uri? = null) {
        // Inflate the preview dialog layout.
        val dialogView = layoutInflater.inflate(R.layout.ip, null)
        val imageView = dialogView.findViewById<android.widget.ImageView>(R.id.previewImageView)

        // Set the image in the preview dialog.
        when {
            photo != null -> imageView.setImageBitmap(photo)
            uri != null -> imageView.setImageURI(uri)
        }

        // Build and show the alert dialog.
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                moveToNextStep()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * Moves to the next step/activity with the selected image.
     *
     * Instead of passing the Bitmap (which can be large), we pass the file path.
     */
    private fun moveToNextStep() {
        val intent = Intent(this, DetectedActivity::class.java)
        // Pass the file path of the captured image.
        currentPhotoPath?.let { path ->
            intent.putExtra("IMAGE_PATH", path)
        }
        startActivity(intent)
    }

    /**
     * Sets the color of various UI elements.
     */
    fun setColor(color_x: Int, color_y: Int) {
        binding.camera.setCardBackgroundColor(color_x)
        binding.gallery.setCardBackgroundColor(color_x)
        binding.tvCamera.setTextColor(color_y)
        binding.tvGallery.setTextColor(color_y)
    }
}
