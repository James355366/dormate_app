package com.dormmate.admin

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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.auth.AuthViewModel
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.util.AvatarUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class ManageUsersFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val allUsers = mutableListOf<User>()

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
        return inflater.inflate(R.layout.fragment_manage_users, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myUid = FirebaseAuth.getInstance().uid
        progress = view.findViewById(R.id.progress_manage)
        tvEmpty = view.findViewById(R.id.tv_manage_empty)
        tvCount = view.findViewById(R.id.tv_manage_count)
        recycler = view.findViewById(R.id.rv_manage_users)
        etSearch = view.findViewById(R.id.et_manage_search)

        adapter = ManageUsersAdapter(
            clickListener = { user -> showEditAdminDialog(user) },
            editListener = { user -> showEditAdminDialog(user) },
            deleteListener = { user -> confirmRemove(user) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<View>(R.id.btn_add_account).setOnClickListener { showAddAccountDialog() }

        val chipAll = view.findViewById<Chip>(R.id.chip_filter_all)
        val chipPending = view.findViewById<Chip>(R.id.chip_filter_pending)
        chipAll.setOnClickListener {
            activeFilter = FILTER_ALL
            applyFilter()
        }
        chipPending.setOnClickListener {
            activeFilter = FILTER_PENDING
            applyFilter()
        }

        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                bindTopHeader(user, view)
            }
        }

        listenForUsers()
    }

    private fun bindTopHeader(user: User, view: View) {
        val rawName = if (!user.name.isNullOrBlank()) user.name!! else (user.email ?: "")
        val name = AvatarUtils.formatDisplayName(rawName)
        view.findViewById<TextView>(R.id.tv_top_user_name)?.text = name
        AvatarUtils.bindAvatar(user, view.findViewById(R.id.tv_top_avatar), view.findViewById(R.id.iv_top_avatar))
        AvatarUtils.bindRoleBadge(user, view.findViewById(R.id.tv_top_role_badge))
        val navigate = {
            if (isAdded) {
                requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_profile
            }
        }
        view.findViewById<View>(R.id.card_top_avatar)?.setOnClickListener { navigate() }
        view.findViewById<View>(R.id.tv_top_avatar)?.setOnClickListener { navigate() }
        view.findViewById<View>(R.id.tv_top_user_name)?.setOnClickListener { navigate() }
    }

    private fun listenForUsers() {
        listener = db.collection("users")
            .whereIn("role", listOf(Role.DORM_ADMIN.name, Role.SUPER_ADMIN.name, Role.MAINTENANCE_STAFF.name))
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                progress.visibility = View.GONE

                if (error != null) {
                    db.collection("users").addSnapshotListener { fbSnap, _ ->
                        if (!isAdded || fbSnap == null) return@addSnapshotListener
                        allUsers.clear()
                        allUsers.addAll(fbSnap.toObjects(User::class.java))
                        applyFilter()
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                allUsers.clear()
                allUsers.addAll(snapshot.toObjects(User::class.java))
                applyFilter()
            }
    }

    private fun applyFilter() {
        if (!isAdded) return
        val q = etSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        val shown = mutableListOf<User>()
        var adminCount = 0
        for (u in allUsers) {
            if (u.roleEnum == Role.RESIDENT) continue
            if (u.roleEnum == Role.DORM_ADMIN) adminCount++
            if (activeFilter == FILTER_PENDING && !isPending(u)) continue
            if (q.isEmpty() || matches(u, q)) shown.add(u)
        }

        shown.sortWith { a, b -> safe(a.name).compareTo(safe(b.name), ignoreCase = true) }

        adapter.submitList(shown)
        tvCount.text = resources.getQuantityString(
            R.plurals.manage_admins_count, adminCount, adminCount
        )
        val empty = shown.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun matches(u: User, q: String): Boolean {
        return safe(u.name).lowercase(Locale.ROOT).contains(q) ||
                safe(u.email).lowercase(Locale.ROOT).contains(q) ||
                safe(u.roomNumber).lowercase(Locale.ROOT).contains(q) ||
                ManageUsersAdapter.roleLabel(u.roleEnum).lowercase(Locale.ROOT).contains(q)
    }

    private fun isPending(u: User): Boolean {
        return u.roleEnum == Role.DORM_ADMIN && !u.isVerified
    }

    private fun safe(s: String?): String = s ?: ""

    private fun getInitial(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().substring(0, 1).uppercase(Locale.ROOT)
    }

    private fun displayName(u: User): String {
        return if (safe(u.name).isNotEmpty()) u.name!! else safe(u.email)
    }

    private fun showAddAccountDialog() {
        val form = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_account, null, false)

        val tilName = form.findViewById<TextInputLayout>(R.id.til_add_name)
        val tilEmail = form.findViewById<TextInputLayout>(R.id.til_add_email)
        val tilPass = form.findViewById<TextInputLayout>(R.id.til_add_password)
        val etName = form.findViewById<EditText>(R.id.et_add_name)
        val etEmail = form.findViewById<EditText>(R.id.et_add_email)
        val etPass = form.findViewById<EditText>(R.id.et_add_password)
        val cbReset = form.findViewById<CheckBox>(R.id.cb_add_reset)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_account_title)
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

                tilName.error = null
                tilEmail.error = null
                tilPass.error = null

                var ok = true
                if (name.isEmpty()) {
                    tilName.error = getString(R.string.add_account_error_name)
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
                if (!ok) return@setOnClickListener

                create.setEnabled(false)
                create.setText(R.string.add_account_creating)

                AccountCreator.create(
                    requireContext(),
                    name,
                    email,
                    pass,
                    Role.DORM_ADMIN,
                    "",
                    null,
                    cbReset.isChecked,
                    object : AccountCreator.Callback {
                        override fun onSuccess() {
                            dialog.dismiss()
                            toast(
                                getString(
                                    R.string.add_account_created,
                                    ManageUsersAdapter.roleLabel(Role.DORM_ADMIN),
                                    name
                                )
                            )
                        }

                        override fun onFailure(message: String) {
                            create.setEnabled(true);
                            create.setText(R.string.add_account_create)
                            tilEmail.error = message
                        }
                    }
                )
            }
        }
        dialog.show()
    }

    private fun showEditAdminDialog(user: User) {
        val uid = user.uid ?: return
        if (uid == myUid) {
            Toast.makeText(requireContext(), R.string.manage_users_self, Toast.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, (12 * resources.displayMetrics.density).toInt(), p, (8 * resources.displayMetrics.density).toInt())
        }

        val tilName = TextInputLayout(context).apply {
            hint = "Full Name"
            startIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_person)
        }
        val etName = TextInputEditText(tilName.context).apply {
            setText(user.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        tilName.addView(etName)

        val tilPhone = TextInputLayout(context).apply {
            hint = "Contact Number"
            startIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_phone)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
            layoutParams = params
        }
        val etPhone = TextInputEditText(tilPhone.context).apply {
            setText(user.phoneNumber ?: "")
            inputType = InputType.TYPE_CLASS_PHONE
        }
        tilPhone.addView(etPhone)

        val tilRole = TextInputLayout(context).apply {
            hint = "Account Role"
            startIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_group)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
            layoutParams = params
        }
        val roles = arrayOf("Dorm Admin", "Maintenance Staff")
        val acvRole = AutoCompleteTextView(tilRole.context).apply {
            setText(ManageUsersAdapter.roleLabel(user.roleEnum), false)
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, roles))
            inputType = InputType.TYPE_NULL
        }
        tilRole.addView(acvRole)

        layout.addView(tilName)
        layout.addView(tilPhone)
        layout.addView(tilRole)

        MaterialAlertDialogBuilder(context)
            .setTitle("Edit Admin / Staff")
            .setView(layout)
            .setPositiveButton(R.string.manage_users_save) { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                val roleString = acvRole.text.toString().trim()
                val newRole = if (roleString == "Maintenance Staff") Role.MAINTENANCE_STAFF else Role.DORM_ADMIN

                if (newName.isEmpty()) {
                    toast(getString(R.string.error_name_required))
                    return@setPositiveButton
                }

                val updates = mutableMapOf<String, Any>()
                updates["name"] = newName
                updates["phoneNumber"] = newPhone
                updates["role"] = newRole.name

                db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener {
                        if (newRole == Role.DORM_ADMIN) {
                            val infoData = mutableMapOf<String, Any>()
                            infoData["adminName"] = newName
                            infoData["adminPhone"] = newPhone
                            db.collection("dorm_info").document(uid).set(infoData, SetOptions.merge())
                        }
                        toast("Admin profile updated")
                    }
                    .addOnFailureListener { e ->
                        toast("Error updating admin: ${e.message}")
                    }
            }
            .setNeutralButton("Remove Account") { _, _ ->
                confirmRemove(user)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun approve(user: User) {
        val uid = user.uid ?: return
        db.collection("users").document(uid)
            .update(
                "isVerified", true,
                "approvedAt", FieldValue.serverTimestamp(),
                "approvedBy", myUid
            )
            .addOnSuccessListener { toast(getString(R.string.approvals_approved_toast, displayName(user))) }
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
                            toast(getString(R.string.manage_users_error_action, error ?: "Failed to remove user"))
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
