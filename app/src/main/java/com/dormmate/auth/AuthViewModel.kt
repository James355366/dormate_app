package com.dormmate.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository.getInstance()

    val currentUser: LiveData<User?>
        get() = repository.currentUser

    val errorMessage: LiveData<String?>
        get() = repository.errorMessage

    val loading: LiveData<Boolean>
        get() = repository.loading

    fun login(email: String, password: String) {
        repository.login(email.trim(), password)
    }

    fun loginWithGoogle(idToken: String) {
        repository.signInWithGoogle(idToken)
    }

    fun registerWithGoogle(
        idToken: String,
        role: Role,
        roomNumber: String,
        name: String? = null,
        adminUid: String? = null,
        adminName: String? = null
    ) {
        repository.registerWithGoogle(idToken, role, roomNumber, name, adminUid, adminName)
    }

    fun register(
        name: String,
        email: String,
        password: String,
        role: Role,
        roomNumber: String,
        adminUid: String? = null,
        adminName: String? = null
    ) {
        repository.register(name.trim(), email.trim(), password, role, roomNumber.trim(), adminUid, adminName)
    }

    fun fetchCurrentUser() {
        repository.fetchCurrentUser()
    }

    fun logout() {
        repository.logout()
    }

    fun isLoggedIn(): Boolean {
        return repository.isLoggedIn()
    }
}
