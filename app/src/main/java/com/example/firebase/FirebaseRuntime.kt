package com.example.firebase

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

/**
 * Safely checks whether real Firebase backend configuration is active at runtime.
 * Standalone CI/GitHub APK builds compiled without a provisioned google-services.json
 * use a placeholder project ID ("ci-local-placeholder") so compilation succeeds while
 * runtime cleanly operates in Local Guest Mode + direct NVIDIA NIM mode without crashing.
 */
object FirebaseRuntime {
    const val CI_PLACEHOLDER_PROJECT_ID = "ci-local-placeholder"
    const val CI_PLACEHOLDER_CLIENT_ID = "ci-local-web-client-id"

    fun isFirebaseConfigured(context: Context): Boolean {
        return try {
            val app = FirebaseApp.getApps(context.applicationContext).firstOrNull()
                ?: FirebaseApp.initializeApp(context.applicationContext)
                ?: return false
            val projectId = app.options.projectId.orEmpty()
            val apiKey = app.options.apiKey
            projectId.isNotBlank() &&
                projectId != CI_PLACEHOLDER_PROJECT_ID &&
                apiKey.isNotBlank() &&
                apiKey != "ci-local-api-key"
        } catch (_: Exception) {
            false
        }
    }

    fun authOrNull(context: Context): FirebaseAuth? {
        if (!isFirebaseConfigured(context)) return null
        return try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
    }

    fun currentUserOrNull(context: Context): FirebaseUser? {
        return authOrNull(context)?.currentUser
    }
}
