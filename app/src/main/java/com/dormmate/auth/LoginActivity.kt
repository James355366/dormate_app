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
import com.dormmate.databinding.ActivityLoginBinding
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private var selectedRole: Role = Role.RESIDENT

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleGoogleSignInResult(task)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setContentView(binding.root)

        setupRoleDropdown()
        setupGoogleSignIn()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRoleDropdown() {
        val roles = arrayOf(
            getString(R.string.role_user),
            getString(R.string.role_admin),
            getString(R.string.role_maintenance_staff),
            getString(R.string.role_super_admin)
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
            selectedRole = when (position) {
                0 -> Role.RESIDENT
                1 -> Role.DORM_ADMIN
                2 -> Role.MAINTENANCE_STAFF
                3 -> Role.SUPER_ADMIN
                else -> Role.RESIDENT
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
            viewModel.loginWithGoogle(account.idToken!!)
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
        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener { sendPasswordReset() }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (!validateInputs(email, password)) return

        clearError()
        viewModel.login(email, password)
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            valid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            valid = false
        } else {
            binding.tilPassword.error = null
        }

        return valid
    }

    private fun sendPasswordReset() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_for_reset)
            return
        }
        AuthRepository.getInstance().sendPasswordResetEmail(
            email,
            object : AuthRepository.Callback {
                override fun onSuccess() {
                    showSnackbar(getString(R.string.msg_reset_email_sent))
                }

                override fun onFailure(error: String) {
                    showSnackbar("Failed to send reset email: $error")
                }
            }
        )
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
            binding.btnGoogleSignIn.isEnabled = !isLoading
        }

        viewModel.currentUser.observe(this, this::onUserLoaded)

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                binding.tvError.text = error
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun onUserLoaded(user: User?) {
        if (user == null) return

        val actualRole = user.roleEnum
        selectedRole = actualRole

        if (actualRole == Role.RESIDENT && !user.isVerified) {
            startActivity(Intent(this, PendingVerificationActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun clearError() {
        binding.tvError.visibility = View.GONE
        binding.tvError.text = null
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
