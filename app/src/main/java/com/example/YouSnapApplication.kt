package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class YouSnapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Ensure Firebase initializes safely at the Application level before any Activity/ViewModel
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                    Log.d("YouSnapApp", "Default FirebaseApp initialized successfully")
                } catch (e: Throwable) {
                    Log.w("YouSnapApp", "Default FirebaseApp init notice: ${e.message}")
                }
                
                // Fallback explicit initialization if default could not load google-services.json
                if (FirebaseApp.getApps(this).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:87251634633:android:b0490cfd2b846fadc74018")
                        .setApiKey("AIzaSyC8Kf0iuclEjg2OGgn8pgx-FQESSsMwvEY")
                        .setProjectId("yousnap-app")
                        .setStorageBucket("yousnap-app.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.d("YouSnapApp", "Explicit FirebaseApp options initialized successfully")
                }
            }
        } catch (e: Throwable) {
            Log.e("YouSnapApp", "Handled Firebase initialization: ${e.message}")
        }
    }
}
