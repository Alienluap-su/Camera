package com.example.samcam

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider

class CamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ProcessCameraProvider.getInstance(this)
        } catch (e: Exception) {
        }
    }
}
