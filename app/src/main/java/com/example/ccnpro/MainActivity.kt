package com.example.ccnpro

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ccnpro.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.wheat.setOnClickListener {
            val intent = Intent(this, GetimgActivity::class.java)
            intent.putExtra("FRAGMENT_TYPE", "WheatFragment")
            startActivity(intent)
        }
        setContentView(binding.root)
    }
}