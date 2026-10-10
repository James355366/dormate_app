package com.dormmate.admin

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object UserDeleter {
    private const val TAG = "UserDeleter"

    fun deleteUser(uid: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()

        // 1. Delete user document from users collection
        db.collection("users").document(uid).delete()
            .addOnSuccessListener {
                // 2. Delete resident_directory entry if present
                db.collection("resident_directory").document(uid).delete()

                // 3. Delete maintenance reports submitted by this user
                db.collection("maintenance_reports")
                    .whereEqualTo("residentUid", uid)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap != null && !snap.isEmpty) {
                            val batch = db.batch()
                            for (doc in snap.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit()
                        }
                    }

                // 4. Delete maintenance reports assigned to this staff member
                db.collection("maintenance_reports")
                    .whereEqualTo("assignedStaffUid", uid)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap != null && !snap.isEmpty) {
                            val batch = db.batch()
                            for (doc in snap.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit()
                        }
                    }

                // 5. Delete bills associated with this user
                db.collection("bills")
                    .whereEqualTo("residentUid", uid)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap != null && !snap.isEmpty) {
                            val batch = db.batch()
                            for (doc in snap.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit()
                        }
                    }

                callback?.invoke(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete user $uid", e)
                callback?.invoke(false, e.message ?: "Failed to delete user")
            }
    }
}
