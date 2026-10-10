package com.dormmate.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.dormmate.MainActivity
import com.dormmate.R
import com.dormmate.databinding.ActivityRegisterBinding
import com.dormmate.model.Role
import com.dormmate.model.RoomWithOccupants
import com.dormmate.model.User
import com.dormmate.util.AvatarUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private var selectedRole: Role = Role.RESIDENT
    private var selectedAdminUid: String? = null
    private var selectedAdminName: String? = null
    private val availableAdminsList = mutableListOf<User>()

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleGoogleSignInResult(task)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setContentView(binding.root)

        setupSignupMethodDropdown()
        setupRoleDropdown()
        loadAdmins()
        setupGoogleSignIn()
        setupClickListeners()
        observeViewModel()
    }

    private fun loadAdmins() {
        val db = FirebaseFirestore.getInstance()
        val adminOptions = mutableListOf<String>()
        availableAdminsList.clear()

        val defaultAdmins = listOf(
            User("kharyn_admin_uid", "Kharyn", "kharyn@gmail.com", Role.DORM_ADMIN, ""),
            User("aizen_admin_uid", "Aizen", "aizen@gmail.com", Role.DORM_ADMIN, ""),
            User("oda_admin_uid", "Oda", "oda@gmail.com", Role.DORM_ADMIN, "")
        )

        for (u in defaultAdmins) {
            availableAdminsList.add(u)
            adminOptions.add(AvatarUtils.formatDisplayName(u.name ?: "Dorm Admin"))
        }

        val bindAdapter = {
            if (!isFinishing && adminOptions.isNotEmpty()) {
                val adapter = object : ArrayAdapter<String>(this@RegisterActivity, android.R.layout.simple_dropdown_item_1line, adminOptions) {
                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(constraint: CharSequence?): FilterResults {
                                val results = FilterResults()
                                results.values = adminOptions.toTypedArray()
                                results.count = adminOptions.size
                                return results
                            }
                            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                notifyDataSetChanged()
                            }
                        }
                    }
                }
                binding.acvAdminSelection.setAdapter(adapter)
                binding.acvAdminSelection.setText(adminOptions[0], false)
                selectedAdminUid = availableAdminsList[0].uid
                selectedAdminName = availableAdminsList[0].name
            }
        }

        bindAdapter()

        fetchAdminsFromUsers(db, adminOptions, bindAdapter)

        binding.acvAdminSelection.setOnItemClickListener { _, _, position, _ ->
            if (position in availableAdminsList.indices) {
                val admin = availableAdminsList[position]
                selectedAdminUid = admin.uid
                selectedAdminName = admin.name
            }
        }
    }

    private fun fetchAdminsFromUsers(
        db: FirebaseFirestore,
        adminOptions: MutableList<String>,
        bindAdapter: () -> Unit
    ) {
        db.collection("users")
            .whereIn("role", listOf(Role.DORM_ADMIN.name, Role.SUPER_ADMIN.name))
            .get()
            .addOnSuccessListener { snap ->
                if (isFinishing || snap == null || snap.isEmpty) {
                    fetchAdminsFromDormInfo(db, adminOptions, bindAdapter)
                    return@addOnSuccessListener
                }
                availableAdminsList.clear()
                adminOptions.clear()
                for (doc in snap.documents) {
                    val u = doc.toObject(User::class.java)
                    if (u != null) {
                        u.uid = doc.id
                        availableAdminsList.add(u)
                        val name = if (!u.name.isNullOrBlank()) u.name!! else (u.email ?: "Dorm Admin")
                        adminOptions.add(AvatarUtils.formatDisplayName(name))
                    }
                }
                bindAdapter()
            }
            .addOnFailureListener {
                fetchAdminsFromDormInfo(db, adminOptions, bindAdapter)
            }
    }

    private fun fetchAdminsFromDormInfo(
        db: FirebaseFirestore,
        adminOptions: MutableList<String>,
        bindAdapter: () -> Unit
    ) {
        db.collection("dorm_info").get()
            .addOnSuccessListener { snap ->
                if (isFinishing || snap == null) return@addOnSuccessListener
                availableAdminsList.clear()
                adminOptions.clear()
                for (doc in snap.documents) {
                    val name = doc.getString("adminName") ?: "Dorm Admin"
                    val u = User(doc.id, name, "", Role.DORM_ADMIN, "")
                    availableAdminsList.add(u)
                    adminOptions.add(AvatarUtils.formatDisplayName(name))
                }
                bindAdapter()
            }
    }

    private fun setupSignupMethodDropdown() {
        val methods = arrayOf("Email Address & Password", "Google / Gmail Account")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, methods) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        results.values = methods
                        results.count = methods.size
                        return results
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        binding.acvSignupMethod.setAdapter(adapter)
        binding.acvSignupMethod.setText("Email Address & Password", false)

        binding.acvSignupMethod.setOnItemClickListener { _, _, position, _ ->
            val isEmailMethod = position == 0
            binding.tilEmail.visibility = if (isEmailMethod) View.VISIBLE else View.GONE
            binding.tilPassword.visibility = if (isEmailMethod) View.VISIBLE else View.GONE
            binding.tilConfirmPassword.visibility = if (isEmailMethod) View.VISIBLE else View.GONE
            binding.btnRegister.visibility = if (isEmailMethod) View.VISIBLE else View.GONE
            binding.llOrDivider.visibility = if (isEmailMethod) View.VISIBLE else View.GONE
            binding.btnGoogleSignIn.visibility = View.VISIBLE
        }
    }

    private fun setupRoleDropdown() {
        val roles = arrayOf(
            getString(R.string.role_user),
            getString(R.string.role_admin),
            getString(R.string.role_maintenance_staff)
        )

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, roles) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        results.values = roles
                        results.count = roles.size
                        return results
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        binding.acvRole.setAdapter(adapter)
        binding.acvRole.setText(getString(R.string.role_user), false)
        selectedRole = Role.RESIDENT

        binding.acvRole.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> {
                    selectedRole = Role.RESIDENT
                    binding.tilAdminSelection.visibility = View.VISIBLE
                }
                1 -> {
                    selectedRole = Role.DORM_ADMIN
                    binding.tilAdminSelection.visibility = View.GONE
                }
                2 -> {
                    selectedRole = Role.MAINTENANCE_STAFF
                    binding.tilAdminSelection.visibility = View.GONE
                }
                else -> {
                    selectedRole = Role.RESIDENT
                    binding.tilAdminSelection.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account == null || account.idToken == null) {
                binding.tvError.text = getString(R.string.error_google_sign_in_failed)
                binding.tvError.visibility = View.VISIBLE
                return
            }
            clearError()

            val name = binding.etName.text.toString().trim()
            viewModel.registerWithGoogle(account.idToken!!, selectedRole, "", name, selectedAdminUid, selectedAdminName)
        } catch (e: ApiException) {
            val cancelled = e.statusCode == 12501
            binding.tvError.text = if (cancelled) {
                getString(R.string.error_google_sign_in_cancelled)
            } else {
                getString(R.string.error_google_sign_in_failed)
            }
            binding.tvError.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRegister.setOnClickListener { attemptRegister() }

        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun attemptRegister() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        if (!validateInputs(name, email, password, confirm)) return

        clearError()
        viewModel.register(name, email, password, selectedRole, "", selectedAdminUid, selectedAdminName)
    }

    private fun validateInputs(
        name: String,
        email: String,
        password: String,
        confirm: String
    ): Boolean {
        var valid = true

        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_required)
            valid = false
        } else {
            binding.tilName.error = null
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            valid = false
        } else {
            binding.tilPassword.error = null
        }

        if (password != confirm) {
            binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            valid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return valid
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = !isLoading
            binding.btnGoogleSignIn.isEnabled = !isLoading
        }

        viewModel.currentUser.observe(this, this::onUserRegistered)

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                binding.tvError.text = error
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun onUserRegistered(user: User?) {
        if (user == null) return
        if (user.isVerified || user.roleEnum != Role.RESIDENT) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, PendingVerificationActivity::class.java))
        }
        finish()
    }

    private fun clearError() {
        binding.tvError.visibility = View.GONE
        binding.tvError.text = null
    }
}
