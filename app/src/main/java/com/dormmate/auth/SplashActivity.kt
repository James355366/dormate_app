package com.dormmate.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.dormmate.MainActivity
import com.dormmate.model.Role
import com.dormmate.model.User

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        if (!viewModel.isLoggedIn()) {
            goTo(LoginActivity::class.java)
            return
        }

        viewModel.fetchCurrentUser()

        viewModel.currentUser.observe(this) { user ->
            if (user == null) return@observe

            if (requiresVerification(user)) {
                goTo(PendingVerificationActivity::class.java)
            } else {
                goTo(MainActivity::class.java)
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                goTo(LoginActivity::class.java)
            }
        }
    }

    private fun requiresVerification(user: User): Boolean {
        return user.roleEnum == Role.RESIDENT && !user.isVerified
    }

    private fun goTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        finish()
    }
}
