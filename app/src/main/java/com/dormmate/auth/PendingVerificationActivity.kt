package com.dormmate.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.dormmate.MainActivity
import com.dormmate.databinding.ActivityPendingVerificationBinding
import com.dormmate.model.Role
import com.dormmate.model.User

class PendingVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingVerificationBinding
    private lateinit var viewModel: AuthViewModel

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = Runnable { pollVerificationStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingVerificationBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
        schedulePoll()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun setupClickListeners() {
        binding.btnCheckNow.setOnClickListener { pollVerificationStatus() }

        binding.btnSignOut.setOnClickListener {
            viewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun schedulePoll() {
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun pollVerificationStatus() {
        binding.btnCheckNow.isEnabled = false
        viewModel.fetchCurrentUser()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.currentUser.observe(this, this::onUserRefreshed)
    }

    private fun onUserRefreshed(user: User?) {
        binding.btnCheckNow.isEnabled = true

        if (user == null) return

        if (user.isVerified || user.roleEnum != Role.RESIDENT) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            pollHandler.removeCallbacks(pollRunnable)
            schedulePoll()
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
    }
}
