package com.dormmate.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.dormmate.model.User
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

object DormInfoActions {
    fun contact(fragment: Fragment, number: String?, sms: Boolean = false) {
        if (number.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Contact number not set", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            fragment.startActivity(Intent(if (sms) Intent.ACTION_SENDTO else Intent.ACTION_DIAL,
                Uri.fromParts(if (sms) "smsto" else "tel", number, null)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(fragment.requireContext(), "No phone or messaging app available", Toast.LENGTH_SHORT).show()
        }
    }
    private fun load(fragment: Fragment, user: User?, action: (com.google.firebase.firestore.DocumentSnapshot) -> Unit) {
        val adminUid = user?.adminUid
        if (adminUid.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Managing admin not assigned", Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseFirestore.getInstance().collection("dorm_info").document(adminUid).get()
            .addOnSuccessListener { if (fragment.view != null) action(it) }
            .addOnFailureListener { if (fragment.view != null) Toast.makeText(fragment.requireContext(), "Unable to load dorm details", Toast.LENGTH_SHORT).show() }
    }
    fun callAdmin(fragment: Fragment, user: User?) {
        val adminUid = user?.adminUid
        if (adminUid.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), "Managing admin not assigned", Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(adminUid).get()
            .addOnSuccessListener { snapshot ->
                if (fragment.view != null) {
                    val phone = snapshot.getString("phoneNumber")
                        ?: snapshot.getString("adminPhone")
                    contact(fragment, phone)
                }
            }
            .addOnFailureListener {
                if (fragment.view != null) {
                    Toast.makeText(fragment.requireContext(), "Unable to load admin contact", Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun showGcash(fragment: Fragment, user: User?) = load(fragment, user) {
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("GCash payment details")
            .setMessage("Account: ${it.getString("gcashName").orEmpty().ifBlank { "Not set" }}\nNumber: ${it.getString("gcashNumber").orEmpty().ifBlank { "Not set" }}\nSubmit your reference and receipt from the Bills tab.")
            .setPositiveButton("Close", null).show()
    }
}
