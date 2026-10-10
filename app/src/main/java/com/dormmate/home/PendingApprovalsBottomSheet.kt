package com.dormmate.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.admin.UserDeleter
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class PendingApprovalsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PendingUsersAdapter
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_pending_approvals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress = view.findViewById(R.id.progress_approvals)
        tvEmpty = view.findViewById(R.id.tv_approvals_empty)
        recycler = view.findViewById(R.id.rv_pending_users)

        adapter = PendingUsersAdapter(object : PendingUsersAdapter.OnPendingUserActionListener {
            override fun onApprove(user: User) {
                approve(user)
            }

            override fun onReject(user: User) {
                reject(user)
            }
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        listenForPendingUsers()
    }

    private fun isResidentForAdmin(resident: User, adminUid: String?, adminName: String?): Boolean {
        if (adminUid.isNullOrBlank() && adminName.isNullOrBlank()) return true

        val rUid = resident.adminUid?.trim()
        val rName = resident.adminName?.trim()?.lowercase(Locale.ROOT) ?: ""

        val myUid = adminUid?.trim() ?: ""
        val myName = adminName?.trim()?.lowercase(Locale.ROOT) ?: ""

        if (myUid.isNotEmpty() && !rUid.isNullOrBlank() && rUid == myUid) {
            return true
        }

        if (myName.isNotEmpty() && rName.isNotEmpty()) {
            if (rName == myName || myName.contains(rName) || rName.contains(myName)) {
                return true
            }
        }

        if (myName.isNotEmpty() && !rUid.isNullOrBlank() && rUid.lowercase(Locale.ROOT).contains(myName)) {
            return true
        }

        return false
    }

    private fun listenForPendingUsers() {
        val myAdminUid = FirebaseAuth.getInstance().uid
        val authRepo = AuthRepository.getInstance()
        val myAdminName = authRepo.currentUser.value?.name

        listener = FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "RESIDENT")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                progress.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.approvals_error_load, error.message),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val users = mutableListOf<User>()
                for (u in snapshot.toObjects(User::class.java)) {
                    if (u.roleEnum != Role.RESIDENT) continue
                    if (u.isVerified || !u.approvedBy.isNullOrBlank()) continue

                    if (isResidentForAdmin(u, myAdminUid, myAdminName)) {
                        users.add(u)
                    }
                }
                users.sortWith { a, b ->
                    val da = a.createdAt
                    val db = b.createdAt
                    if (da == null && db == null) 0
                    else if (da == null) 1
                    else if (db == null) -1
                    else da.compareTo(db)
                }

                adapter.submitList(users)
                val empty = users.isEmpty()
                tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                recycler.visibility = if (empty) View.GONE else View.VISIBLE
            }
    }

    private fun approve(user: User) {
        val uid = user.uid ?: return

        adapter.setBusy(uid, true)

        val myUid = FirebaseAuth.getInstance().uid
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update(
                "isVerified", true,
                "adminUid", myUid,
                "approvedAt", FieldValue.serverTimestamp(),
                "approvedBy", myUid
            )
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                val name = if (!user.name.isNullOrEmpty()) user.name!! else (user.email ?: "")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.approvals_approved_toast, name),
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                adapter.setBusy(uid, false)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.approvals_error_approve, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun reject(user: User) {
        val uid = user.uid ?: return
        val displayName = if (!user.name.isNullOrEmpty()) user.name!! else (user.email ?: "")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.approvals_reject_title)
            .setMessage(getString(R.string.approvals_reject_msg, displayName))
            .setPositiveButton(R.string.approvals_reject) { _, _ -> confirmReject(user) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmReject(user: User) {
        val uid = user.uid ?: return

        adapter.setBusy(uid, true)

        UserDeleter.deleteUser(uid) { success, error ->
            if (isAdded) {
                adapter.setBusy(uid, false)
                if (success) {
                    val name = if (!user.name.isNullOrEmpty()) user.name!! else (user.email ?: "")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.approvals_rejected_toast, name),
                        Toast.LENGTH_SHORT
                    ).show()
                    parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.approvals_error_reject, error ?: "Failed to reject user"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
    }

    companion object {
        const val RESULT_KEY = "pending_approvals_changed"
    }
}
