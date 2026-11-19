package com.dormmate.admin

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Role
import com.dormmate.model.RoomWithOccupants
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.dormmate.repository.RoomsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class ManageResidentsBottomSheet : BottomSheetDialogFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val allResidents = mutableListOf<User>()
    private val roomOccupancyList = mutableListOf<RoomWithOccupants>()

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var adapter: ManageUsersAdapter
    private var listener: ListenerRegistration? = null
    private var myUid: String? = null
    private var activeFilter = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_manage_residents, container, false)
    }

    override fun onStart() {
        super.onStart()
        val d = dialog
        if (d is BottomSheetDialog) {
            val b = d.behavior
            b.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myUid = FirebaseAuth.getInstance().uid
        progress = view.findViewById(R.id.progress_residents)
        tvEmpty = view.findViewById(R.id.tv_residents_empty)
        tvCount = view.findViewById(R.id.tv_resident_count)
        recycler = view.findViewById(R.id.rv_manage_residents)
        etSearch = view.findViewById(R.id.et_resident_search)

        adapter = ManageUsersAdapter(
            clickListener = { user -> showActions(user) },
            editListener = { user -> showEditResidentDialog(user) },
            deleteListener = { user -> confirmRemove(user) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<View>(R.id.btn_add_resident).setOnClickListener { showAddResidentDialog() }

        val chipAll = view.findViewById<Chip>(R.id.chip_filter_all_residents)
        val chipPending = view.findViewById<Chip>(R.id.chip_filter_pending_residents)
        if (chipAll != null && chipPending != null) {
            chipAll.setOnClickListener {
                activeFilter = FILTER_ALL
                applyFilter()
            }
            chipPending.setOnClickListener {
                activeFilter = FILTER_PENDING
                applyFilter()
            }
        }

        RoomsRepository.getInstance().getRoomsWithOccupancy().observe(viewLifecycleOwner) { list ->
            if (list != null) {
                roomOccupancyList.clear()
                roomOccupancyList.addAll(list)
            }
        }

        listenForResidents()
    }

    private fun listenForResidents() {
        listener = db.collection("users")
            .whereEqualTo("role", Role.RESIDENT.name)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                progress.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.manage_users_error_load, error.message),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                allResidents.clear()
                allResidents.addAll(snapshot.toObjects(User::class.java))
                val myAdminUser = AuthRepository.getInstance().currentUser.value
                val myAdminName = myAdminUser?.name.orEmpty().ifBlank { myAdminUser?.email.orEmpty() }

                for (u in allResidents) {
                    val uId = u.uid
                    if (u.isUserVerified && !uId.isNullOrBlank()) {
                        val dirData = mutableMapOf<String, Any>()
                        dirData["name"] = u.name ?: ""
                        dirData["email"] = u.email ?: ""
                        dirData["roomNumber"] = u.roomNumber ?: ""
                        dirData["isVerified"] = true
                        val effectiveAdmin = u.adminUid ?: myUid
                        if (!effectiveAdmin.isNullOrBlank()) dirData["adminUid"] = effectiveAdmin
                        if (myAdminName.isNotBlank()) {
                            dirData["adminName"] = myAdminName
                            db.collection("users").document(uId).update("adminName", myAdminName)
                        }
                        db.collection("resident_directory").document(uId).set(dirData, SetOptions.merge())
                    }
                }
                applyFilter()
            }
    }

    private fun applyFilter() {
        if (!isAdded) return
        val q = etSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        val shown = mutableListOf<User>()
        var resCount = 0
        for (u in allResidents) {
            val uName = u.name?.trim() ?: ""
            if (u.roomNumber.isNullOrBlank() && (uName.startsWith("1james") || uName.startsWith("2james") || uName.startsWith("Stork"))) {
                u.uid?.let { testUid -> UserDeleter.deleteUser(testUid) }
                continue
            }
            val adminId = u.adminUid
            if (!adminId.isNullOrBlank() && adminId != myUid) {
                continue
            }
            resCount++
            if (activeFilter == FILTER_PENDING && isVerified(u)) continue
            if (q.isEmpty() || matches(u, q)) shown.add(u)
        }

        shown.sortWith { a, b ->
            val pa = !isVerified(a)
            val pb = !isVerified(b)
            if (pa != pb) {
                if (pa) -1 else 1
            } else {
                safe(a.name).compareTo(safe(b.name), ignoreCase = true)
            }
        }

        adapter.submitList(shown)
        tvCount.text = resources.getQuantityString(
            R.plurals.manage_residents_count, resCount, resCount
        )
        val empty = shown.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun matches(u: User, q: String): Boolean {
        return safe(u.name).lowercase(Locale.ROOT).contains(q) ||
                safe(u.email).lowercase(Locale.ROOT).contains(q) ||
                safe(u.roomNumber).lowercase(Locale.ROOT).contains(q)
    }

    private fun isVerified(u: User): Boolean {
        return u.isVerified
    }

    private fun safe(s: String?): String = s ?: ""

    private fun displayName(u: User): String {
        return if (safe(u.name).isNotEmpty()) u.name!! else safe(u.email)
    }

    private fun showAddResidentDialog() {
        val form = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_resident, null, false)

        val tilName = form.findViewById<TextInputLayout>(R.id.til_add_res_name)
        val tilEmail = form.findViewById<TextInputLayout>(R.id.til_add_res_email)
        val tilPass = form.findViewById<TextInputLayout>(R.id.til_add_res_password)
        val tilRoom = form.findViewById<TextInputLayout>(R.id.til_add_res_room)
        val etName = form.findViewById<EditText>(R.id.et_add_res_name)
        val etEmail = form.findViewById<EditText>(R.id.et_add_res_email)
        val etPass = form.findViewById<EditText>(R.id.et_add_res_password)
        val etRoom = form.findViewById<AutoCompleteTextView>(R.id.et_add_res_room)
        val cbReset = form.findViewById<CheckBox>(R.id.cb_add_res_reset)

        if (roomOccupancyList.isNotEmpty()) {
            val roomOptions = mutableListOf<String>()
            for (rwo in roomOccupancyList) {
                val r = rwo.room
                if (r != null) {
                    roomOptions.add("${r.roomNumber} [${rwo.getStatusText()}]")
                }
            }
            val roomAdapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, roomOptions
            )
            etRoom.setAdapter(roomAdapter)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_add_resident)
            .setView(form)
            .setPositiveButton(R.string.add_account_create, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val create = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            create.setOnClickListener {
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val pass = etPass.text.toString()
                val rawRoom = etRoom.text.toString().trim()
                val room = if (rawRoom.contains(" [")) rawRoom.split(" [")[0].trim() else rawRoom

                tilName.error = null
                tilEmail.error = null
                tilPass.error = null
                tilRoom.error = null

                var ok = true
                if (name.isEmpty()) {
                    tilName.error = getString(R.string.error_name_required)
                    ok = false
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.error = getString(R.string.add_account_error_email)
                    ok = false
                }
                if (pass.length < 6) {
                    tilPass.error = getString(R.string.add_account_error_password)
                    ok = false
                }
                if (room.isEmpty()) {
                    tilRoom.error = getString(R.string.error_room_required)
                    ok = false
                }
                if (!ok) return@setOnClickListener

                create.isEnabled = false
                create.setText(R.string.add_account_creating)

                AccountCreator.create(
                    requireContext(),
                    name,
                    email,
                    pass,
                    Role.RESIDENT,
                    room,
                    myUid,
                    cbReset.isChecked,
                    object : AccountCreator.Callback {
                        override fun onSuccess() {
                            dialog.dismiss()
                            toast(getString(R.string.msg_resident_created, name))
                        }

                        override fun onFailure(message: String) {
                            create.isEnabled = true
                            create.setText(R.string.add_account_create)
                            tilEmail.error = message
                        }
                    }
                )
            }
        }
        dialog.show()
    }

    private fun showEditResidentDialog(user: User) {
        val uid = user.uid ?: return

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val tilName = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_full_name)
        }
        val etName = EditText(requireContext()).apply {
            setText(user.name)
        }
        tilName.addView(etName)
        layout.addView(tilName)

        val tilRoom = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_room_number)
            val topMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, topMargin, 0, 0) }
        }
        val etRoom = AutoCompleteTextView(requireContext()).apply {
            setText(user.roomNumber)
        }
        if (roomOccupancyList.isNotEmpty()) {
            val roomOptions = mutableListOf<String>()
            for (rwo in roomOccupancyList) {
                val r = rwo.room
                if (r != null) {
                    roomOptions.add("${r.roomNumber} [${rwo.getStatusText()}]")
                }
            }
            val roomAdapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, roomOptions
            )
            etRoom.setAdapter(roomAdapter)
        }
        tilRoom.addView(etRoom)
        layout.addView(tilRoom)

        val tilPhone = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_contact_number)
            val topMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, topMargin, 0, 0) }
        }
        val etPhone = EditText(requireContext()).apply {
            setText(user.phoneNumber)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        tilPhone.addView(etPhone)
        layout.addView(tilPhone)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Resident")
            .setView(layout)
            .setPositiveButton(R.string.manage_users_save) { _, _ ->
                val newName = etName.text.toString().trim()
                val rawRoom = etRoom.text.toString().trim()
                val newRoom = if (rawRoom.contains(" [")) rawRoom.split(" [")[0].trim() else rawRoom
                val newPhone = etPhone.text.toString().trim()

                if (newName.isEmpty()) {
                    toast(getString(R.string.error_name_required))
                    return@setPositiveButton
                }

                val updates = mutableMapOf<String, Any>()
                updates["name"] = newName
                updates["roomNumber"] = newRoom
                updates["phoneNumber"] = newPhone

                db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener {
                        toast("Resident updated")
                    }
                    .addOnFailureListener { e ->
                        toast("Error updating resident: ${e.message}")
                    }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showActions(user: User) {
        val uid = user.uid ?: return

        val pending = !isVerified(user)
        val labels = mutableListOf<String>()
        if (pending) labels.add(getString(R.string.approvals_approve))
        labels.add("Edit Resident")
        labels.add(getString(R.string.manage_users_remove))

        val title = displayName(user) + if (user.roomNumber != null) " (${user.roomNumber})" else ""

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (pending) {
                    when (which) {
                        0 -> approve(user)
                        1 -> showEditResidentDialog(user)
                        2 -> confirmRemove(user)
                    }
                } else {
                    when (which) {
                        0 -> showEditResidentDialog(user)
                        1 -> confirmRemove(user)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun approve(user: User) {
        val uid = user.uid ?: return
        db.collection("users").document(uid)
            .update(
                "isVerified", true,
                "adminUid", myUid,
                "approvedAt", FieldValue.serverTimestamp(),
                "approvedBy", myUid
            )
            .addOnSuccessListener {
                val dirData = mutableMapOf<String, Any>()
                dirData["name"] = user.name ?: ""
                dirData["email"] = user.email ?: ""
                dirData["roomNumber"] = user.roomNumber ?: ""
                dirData["isVerified"] = true
                if (!myUid.isNullOrBlank()) dirData["adminUid"] = myUid!!
                db.collection("resident_directory").document(uid).set(dirData, SetOptions.merge())
                toast(getString(R.string.approvals_approved_toast, displayName(user)))
            }
            .addOnFailureListener { e -> toast(getString(R.string.approvals_error_approve, e.message)) }
    }

    private fun confirmRemove(user: User) {
        val uid = user.uid ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manage_users_remove)
            .setMessage(getString(R.string.manage_users_remove_msg, displayName(user)))
            .setPositiveButton(R.string.manage_users_remove) { _, _ ->
                UserDeleter.deleteUser(uid) { success, error ->
                    if (isAdded) {
                        if (success) {
                            toast(getString(R.string.manage_users_removed, displayName(user)))
                        } else {
                            toast(error ?: "Failed to remove user")
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
    }

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_PENDING = 1
    }
}
