package com.dormmate.maintenance

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.dormmate.model.MaintenanceReport
import com.dormmate.model.User
import com.dormmate.repository.MaintenanceRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class AssignStaffDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val reportId = arguments?.getString(ARG_REPORT_ID)
        val currentUid = arguments?.getString(ARG_CURRENT_UID)

        val loading = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Assign Maintenance Staff")
            .setMessage("Loading staff…")
            .create()

        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "MAINTENANCE_STAFF")
            .get()
            .addOnSuccessListener { snap ->
                if (!isAdded) return@addOnSuccessListener

                val staffList = mutableListOf<User>()
                for (u in snap.toObjects(User::class.java)) {
                    if (u.isVerified) staffList.add(u)
                }
                staffList.sortWith { a, b ->
                    (a.name ?: "").compareTo(b.name ?: "", ignoreCase = true)
                }

                loading.dismiss()
                showPicker(reportId, currentUid, staffList)
            }
            .addOnFailureListener { e ->
                if (isAdded) loading.setMessage("Failed to load staff: " + e.message)
            }

        return loading
    }

    private fun showPicker(reportId: String?, currentUid: String?, staffList: List<User>) {
        if (staffList.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No maintenance staff yet")
                .setMessage("Ask the Super Admin to add one from Profile → Manage users → Add staff / admin account.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val labels = Array(staffList.size) { i ->
            val u = staffList[i]
            val isCurrent = u.uid != null && u.uid == currentUid
            (u.name ?: "") + if (isCurrent) "   ✓ assigned" else ""
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (currentUid == null) "Assign Maintenance Staff" else "Reassign Maintenance Staff")
            .setItems(labels) { _, which ->
                val selected = staffList[which]
                if (selected.uid != null && selected.uid == currentUid) {
                    toast("${selected.name} is already assigned.")
                    return@setItems
                }
                if (reportId != null) {
                    assign(reportId, selected)
                }
            }
            .setNegativeButton("Cancel", null)

        if (currentUid != null && reportId != null) {
            builder.setNeutralButton("Unassign") { _, _ -> unassign(reportId) }
        }
        builder.show()
    }

    private fun assign(reportId: String, selected: User) {
        val sUid = selected.uid ?: return
        val sName = selected.name ?: ""
        MaintenanceRepository.getInstance().assignStaff(
            reportId, sUid, sName,
            object : MaintenanceRepository.Callback {
                override fun onSuccess(id: String) {
                    toast("${selected.name} assigned.")
                }

                override fun onFailure(error: String) {
                    toast("Error: $error")
                }
            }
        )
    }

    private fun unassign(reportId: String) {
        val updates = mutableMapOf<String, Any?>()
        updates["assignedStaffUid"] = null
        updates["assignedStaffName"] = null
        updates["status"] = MaintenanceReport.Status.PENDING.name
        updates["updatedAt"] = Date()

        FirebaseFirestore.getInstance()
            .collection(MaintenanceRepository.COL)
            .document(reportId)
            .update(updates)
            .addOnSuccessListener { toast("Staff unassigned.") }
            .addOnFailureListener { e -> toast("Error: ${e.message}") }
    }

    private fun toast(msg: String) {
        if (context != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_REPORT_ID = "report_id"
        private const val ARG_CURRENT_UID = "current_staff_uid"

        @JvmStatic
        fun newInstance(reportId: String?, currentStaffUid: String? = null): AssignStaffDialog {
            val d = AssignStaffDialog()
            val args = Bundle().apply {
                putString(ARG_REPORT_ID, reportId)
                putString(ARG_CURRENT_UID, currentStaffUid)
            }
            d.arguments = args
            return d
        }
    }
}
