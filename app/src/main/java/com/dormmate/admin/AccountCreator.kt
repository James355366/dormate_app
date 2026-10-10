package com.dormmate.admin

import android.content.Context
import com.dormmate.model.Role
import com.dormmate.model.User
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AccountCreator {

    interface Callback {
        fun onSuccess()
        fun onFailure(message: String)
    }

    private const val SECONDARY_APP_NAME = "account-creator"

    @JvmStatic
    @JvmOverloads
    fun create(
        context: Context,
        name: String,
        email: String,
        password: String,
        role: Role,
        roomNumber: String = "",
        adminUid: String? = null,
        sendResetEmail: Boolean,
        callback: Callback
    ) {
        val secondaryAuth: FirebaseAuth = try {
            FirebaseAuth.getInstance(secondaryApp(context.applicationContext))
        } catch (e: Exception) {
            callback.onFailure("Couldn't start account creation: ${e.message}")
            return
        }

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val newUser = result.user
                if (newUser == null) {
                    secondaryAuth.signOut()
                    callback.onFailure("Account was created but no user was returned.")
                    return@addOnSuccessListener
                }

                val profile = User(newUser.uid, name, email, role, roomNumber, adminUid).apply {
                    isVerified = true
                }

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(newUser.uid)
                    .set(profile)
                    .addOnSuccessListener {
                        if (sendResetEmail) {
                            secondaryAuth.sendPasswordResetEmail(email)
                        }
                        secondaryAuth.signOut()
                        callback.onSuccess()
                    }
                    .addOnFailureListener { e ->
                        newUser.delete().addOnCompleteListener { secondaryAuth.signOut() }
                        callback.onFailure("Couldn't save the profile: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                secondaryAuth.signOut()
                callback.onFailure(e.message ?: "Sign-up failed.")
            }
    }

    private fun secondaryApp(appContext: Context): FirebaseApp {
        return try {
            FirebaseApp.getInstance(SECONDARY_APP_NAME)
        } catch (notCreatedYet: IllegalStateException) {
            FirebaseApp.initializeApp(
                appContext,
                FirebaseApp.getInstance().options,
                SECONDARY_APP_NAME
            )
        }
    }
}
