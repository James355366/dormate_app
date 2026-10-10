package com.dormmate.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dormmate.model.MaintenanceReport
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

class MaintenanceRepository private constructor() {

    interface Callback {
        fun onSuccess(id: String)
        fun onFailure(error: String)
    }

    private val db = FirebaseFirestore.getInstance()

    private var residentListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null
    private var allListener: ListenerRegistration? = null

    private fun filterAndPurgeOrphanedReports(
        reports: List<MaintenanceReport>,
        onResult: (List<MaintenanceReport>) -> Unit
    ) {
        db.collection("users").get()
            .addOnSuccessListener { userSnap ->
                val activeUids = userSnap.documents.map { it.id }.toSet()
                val validReports = mutableListOf<MaintenanceReport>()
                for (r in reports) {
                    val resUid = r.residentUid
                    if (!resUid.isNullOrBlank() && !activeUids.contains(resUid)) {
                        val id = r.reportId
                        if (!id.isNullOrBlank()) {
                            db.collection(COL).document(id).delete()
                        }
                    } else {
                        validReports.add(r)
                    }
                }
                onResult(validReports)
            }
            .addOnFailureListener {
                onResult(reports)
            }
    }

    fun getAllReports(): LiveData<List<MaintenanceReport>> {
        val ld = MutableLiveData<List<MaintenanceReport>>()
        stopAllListener()
        allListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getAllReports error", e)
                    return@addSnapshotListener
                }
                val raw = snap.toObjects(MaintenanceReport::class.java)
                filterAndPurgeOrphanedReports(raw) { ld.value = it }
            }
        return ld
    }

    fun getReportsForAdmin(adminUid: String): LiveData<List<MaintenanceReport>> {
        val ld = MutableLiveData<List<MaintenanceReport>>()
        stopAllListener()
        allListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getReportsForAdmin error", e)
                    return@addSnapshotListener
                }
                val all = snap.toObjects(MaintenanceReport::class.java)
                val filtered = mutableListOf<MaintenanceReport>()
                for (r in all) {
                    if (r.adminUid.isNullOrBlank() || r.adminUid == adminUid) {
                        filtered.add(r)
                    }
                }
                filterAndPurgeOrphanedReports(filtered) { ld.value = it }
            }
        return ld
    }

    fun getReportsForResident(residentUid: String): LiveData<List<MaintenanceReport>> {
        val ld = MutableLiveData<List<MaintenanceReport>>()
        stopResidentListener()
        residentListener = db.collection(COL)
            .whereEqualTo("residentUid", residentUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getReportsForResident error", e)
                    return@addSnapshotListener
                }
                val raw = snap.toObjects(MaintenanceReport::class.java)
                filterAndPurgeOrphanedReports(raw) { ld.value = it }
            }
        return ld
    }

    fun getReportsForStaff(staffUid: String): LiveData<List<MaintenanceReport>> {
        val ld = MutableLiveData<List<MaintenanceReport>>()
        stopStaffListener()
        staffListener = db.collection(COL)
            .whereEqualTo("assignedStaffUid", staffUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getReportsForStaff error", e)
                    return@addSnapshotListener
                }
                val raw = snap.toObjects(MaintenanceReport::class.java)
                filterAndPurgeOrphanedReports(raw) { ld.value = it }
            }
        return ld
    }

    fun stopListening() {
        stopResidentListener()
        stopStaffListener()
        stopAllListener()
    }

    private fun stopResidentListener() {
        residentListener?.remove()
        residentListener = null
    }

    private fun stopStaffListener() {
        staffListener?.remove()
        staffListener = null
    }

    private fun stopAllListener() {
        allListener?.remove()
        allListener = null
    }

    fun submitReport(report: MaintenanceReport, callback: Callback?) {
        val docRef = db.collection(COL).document()
        report.reportId = docRef.id

        docRef.set(report)
            .addOnSuccessListener {
                callback?.onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "submitReport failed", e)
                callback?.onFailure(e.message ?: "Failed to submit report")
            }
    }

    fun updatePhotoUrl(reportId: String, photoUrl: String, callback: Callback?) {
        val updates = mutableMapOf<String, Any>()
        updates["photoUrl"] = photoUrl
        updates["updatedAt"] = Date()

        db.collection(COL).document(reportId).update(updates)
            .addOnSuccessListener { callback?.onSuccess(reportId) }
            .addOnFailureListener { e ->
                Log.w(TAG, "updatePhotoUrl failed (non-fatal)", e)
                callback?.onFailure(e.message ?: "Failed to update photo")
            }
    }

    fun assignStaff(
        reportId: String,
        staffUid: String,
        staffName: String,
        callback: Callback?
    ) {
        val updates = mutableMapOf<String, Any>()
        updates["assignedStaffUid"] = staffUid
        updates["assignedStaffName"] = staffName
        updates["status"] = MaintenanceReport.Status.IN_PROGRESS.name
        updates["updatedAt"] = Date()

        db.collection(COL).document(reportId).update(updates)
            .addOnSuccessListener { callback?.onSuccess(reportId) }
            .addOnFailureListener { e ->
                Log.e(TAG, "assignStaff failed", e)
                callback?.onFailure(e.message ?: "Failed to assign staff")
            }
    }

    fun cycleStatus(report: MaintenanceReport, callback: Callback?) {
        val next = when (report.statusEnum) {
            MaintenanceReport.Status.PENDING -> MaintenanceReport.Status.IN_PROGRESS
            MaintenanceReport.Status.IN_PROGRESS -> MaintenanceReport.Status.RESOLVED
            else -> {
                callback?.onSuccess(report.reportId ?: "")
                return
            }
        }

        val updates = mutableMapOf<String, Any>()
        updates["status"] = next.name
        updates["updatedAt"] = Date()

        val id = report.reportId ?: ""
        db.collection(COL).document(id).update(updates)
            .addOnSuccessListener { callback?.onSuccess(id) }
            .addOnFailureListener { e ->
                Log.e(TAG, "cycleStatus failed", e)
                callback?.onFailure(e.message ?: "Failed to update status")
            }
    }

    companion object {
        private const val TAG = "MaintenanceRepository"
        const val COL = "maintenance_reports"

        @Volatile
        private var instance: MaintenanceRepository? = null

        @JvmStatic
        fun getInstance(): MaintenanceRepository {
            return instance ?: synchronized(this) {
                instance ?: MaintenanceRepository().also { instance = it }
            }
        }
    }
}
