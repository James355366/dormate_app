package com.dormmate.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dormmate.model.Role
import com.dormmate.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class AuthRepository private constructor() {

    interface Callback {
        fun onSuccess()
        fun onFailure(error: String)
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    val currentUser = MutableLiveData<User?>()
    val errorMessage = MutableLiveData<String?>()
    val loading = MutableLiveData(false)

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun startTimeoutGuard() {
        cancelTimeoutGuard()
        timeoutRunnable = Runnable {
            loading.value = false
            errorMessage.value =
                "This is taking longer than expected. Check your internet connection and try again."
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
    }

    private fun cancelTimeoutGuard() {
        timeoutRunnable?.let {
            timeoutHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
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
        loading.value = true
        startTimeoutGuard()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    cancelTimeoutGuard()
                    errorMessage.value = "Registration failed: no user returned."
                    loading.value = false
                    return@addOnSuccessListener
                }

                val user = User(firebaseUser.uid, name, email, role, roomNumber)
                if (!adminUid.isNullOrBlank()) user.rawAdminUid = adminUid
                if (!adminName.isNullOrBlank()) user.adminName = adminName

                db.collection(COLLECTION_USERS)
                    .document(firebaseUser.uid)
                    .set(user)
                    .addOnSuccessListener {
                        cancelTimeoutGuard()
                        loading.value = false
                        currentUser.value = user
                    }
                    .addOnFailureListener { e ->
                        cancelTimeoutGuard()
                        loading.value = false
                        errorMessage.value = "Account created but profile save failed: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                cancelTimeoutGuard()
                loading.value = false
                errorMessage.value = e.message
            }
    }

    fun login(email: String, password: String) {
        loading.value = true
        startTimeoutGuard()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    cancelTimeoutGuard()
                    errorMessage.value = "Sign-in failed."
                    loading.value = false
                    return@addOnSuccessListener
                }
                fetchUserDocument(firebaseUser.uid)
            }
            .addOnFailureListener { e ->
                cancelTimeoutGuard()
                loading.value = false
                errorMessage.value = e.message
            }
    }

    fun signInWithGoogle(idToken: String) {
        loading.value = true
        startTimeoutGuard()

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    cancelTimeoutGuard()
                    errorMessage.value = "Google sign-in failed."
                    loading.value = false
                    return@addOnSuccessListener
                }

                db.collection(COLLECTION_USERS)
                    .document(firebaseUser.uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            cancelTimeoutGuard()
                            loading.value = false
                            val user = snapshot.toObject(User::class.java)
                            if (user != null && isSuperAdminEmail(user.email)) {
                                user.roleEnum = Role.SUPER_ADMIN
                                user.isVerified = true
                                val superAdminMap = mapOf<String, Any>(
                                    "role" to Role.SUPER_ADMIN.name,
                                    "isVerified" to true
                                )
                                db.collection(COLLECTION_USERS).document(firebaseUser.uid)
                                    .set(superAdminMap, SetOptions.merge())
                            }
                            currentUser.value = user
                        } else {
                            val name = firebaseUser.displayName ?: ""
                            val email = firebaseUser.email ?: ""
                            val isSuper = isSuperAdminEmail(email)
                            val assignedRole = if (isSuper) Role.SUPER_ADMIN else Role.RESIDENT

                            val newUser = User(firebaseUser.uid, name, email, assignedRole, "")
                            if (isSuper) newUser.isVerified = true

                            db.collection(COLLECTION_USERS)
                                .document(firebaseUser.uid)
                                .set(newUser)
                                .addOnSuccessListener {
                                    cancelTimeoutGuard()
                                    loading.value = false
                                    currentUser.value = newUser
                                }
                                .addOnFailureListener { e ->
                                    cancelTimeoutGuard()
                                    loading.value = false
                                    errorMessage.value = "Signed in but profile save failed: ${e.message}"
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        cancelTimeoutGuard()
                        loading.value = false
                        errorMessage.value = "Failed to load profile: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                cancelTimeoutGuard()
                loading.value = false
                errorMessage.value = e.message
            }
    }

    fun registerWithGoogle(
        idToken: String,
        role: Role,
        roomNumber: String,
        customName: String? = null,
        adminUid: String? = null,
        adminName: String? = null
    ) {
        loading.value = true
        startTimeoutGuard()

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    cancelTimeoutGuard()
                    errorMessage.value = "Google registration failed."
                    loading.value = false
                    return@addOnSuccessListener
                }

                val name = customName.orEmpty().ifBlank { firebaseUser.displayName ?: "" }
                val email = firebaseUser.email ?: ""

                val user = User(firebaseUser.uid, name, email, role, roomNumber)
                if (!adminUid.isNullOrBlank()) user.rawAdminUid = adminUid
                if (!adminName.isNullOrBlank()) user.adminName = adminName

                db.collection(COLLECTION_USERS)
                    .document(firebaseUser.uid)
                    .set(user)
                    .addOnSuccessListener {
                        cancelTimeoutGuard()
                        loading.value = false
                        currentUser.value = user
                    }
                    .addOnFailureListener { e ->
                        cancelTimeoutGuard()
                        loading.value = false
                        errorMessage.value = "Account created but profile save failed: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                cancelTimeoutGuard()
                loading.value = false
                errorMessage.value = e.message
            }
    }

    fun fetchCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            currentUser.value = null
            return
        }
        fetchUserDocument(firebaseUser.uid)
    }

    fun logout() {
        auth.signOut()
        currentUser.value = null
    }

    fun updateFcmToken(uid: String, token: String) {
        db.collection(COLLECTION_USERS)
            .document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e ->
                Log.w("AuthRepository", "FCM token update failed", e)
            }
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

    @JvmOverloads
    fun getPendingApprovalCount(adminUid: String? = null, adminName: String? = null, callback: (Int) -> Unit) {
        val currentAdminName = adminName ?: currentUser.value?.name
        db.collection(COLLECTION_USERS)
            .whereEqualTo("role", Role.RESIDENT.name)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null) {
                    callback(0)
                    return@addOnSuccessListener
                }
                var pending = 0
                for (u in snapshot.toObjects(User::class.java)) {
                    if (u.roleEnum != Role.RESIDENT) continue
                    if (u.isVerified || !u.approvedBy.isNullOrBlank()) continue

                    if (isResidentForAdmin(u, adminUid, currentAdminName)) pending++
                }
                callback(pending)
            }
            .addOnFailureListener { e ->
                Log.w("AuthRepository", "Pending approval count failed", e)
                callback(0)
            }
    }

    @JvmOverloads
    fun getTotalResidentsCount(adminUid: String? = null, callback: (Int) -> Unit) {
        db.collection(COLLECTION_USERS)
            .whereEqualTo("role", Role.RESIDENT.name)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null) {
                    callback(0)
                    return@addOnSuccessListener
                }
                var count = 0
                for (u in snapshot.toObjects(User::class.java)) {
                    val aId = u.adminUid
                    if (adminUid.isNullOrEmpty() || aId.isNullOrEmpty() || aId == adminUid) {
                        count++
                    }
                }
                callback(count)
            }
            .addOnFailureListener { e ->
                Log.w("AuthRepository", "Total residents count failed", e)
                callback(0)
            }
    }

    fun getTotalAdminsCount(callback: (Int) -> Unit) {
        db.collection("dorm_info").get()
            .addOnSuccessListener { infoSnap ->
                val dormInfoCount = infoSnap?.size() ?: 0
                db.collection(COLLECTION_USERS)
                    .whereEqualTo("role", Role.DORM_ADMIN.name)
                    .get()
                    .addOnSuccessListener { userSnap ->
                        val uCount = userSnap?.size() ?: 0
                        val finalCount = Math.max(dormInfoCount, uCount)
                        callback(if (finalCount > 0) finalCount else 3)
                    }
                    .addOnFailureListener {
                        callback(if (dormInfoCount > 0) dormInfoCount else 3)
                    }
            }
            .addOnFailureListener {
                db.collection(COLLECTION_USERS)
                    .whereEqualTo("role", Role.DORM_ADMIN.name)
                    .get()
                    .addOnSuccessListener { userSnap ->
                        val uCount = userSnap?.size() ?: 0
                        callback(if (uCount > 0) uCount else 3)
                    }
                    .addOnFailureListener {
                        callback(3)
                    }
            }
    }

    fun updateUserProfile(
        uid: String,
        name: String,
        roomNumber: String?,
        phoneNumber: String? = null,
        callback: Callback? = null
    ) {
        val updates = mutableMapOf<String, Any>()
        updates["name"] = name
        if (roomNumber != null) {
            updates["roomNumber"] = roomNumber
        }
        if (phoneNumber != null) {
            updates["phoneNumber"] = phoneNumber
        }
        db.collection(COLLECTION_USERS)
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                fetchUserDocument(uid)
                callback?.onSuccess()
            }
            .addOnFailureListener { e ->
                callback?.onFailure(e.message ?: "Update failed")
            }
    }

    fun updateProfileImage(uid: String, photoUrl: String, callback: Callback?) {
        db.collection(COLLECTION_USERS)
            .document(uid)
            .update("profileImageUrl", photoUrl)
            .addOnSuccessListener {
                fetchUserDocument(uid)
                callback?.onSuccess()
            }
            .addOnFailureListener { e ->
                callback?.onFailure(e.message ?: "Failed to update profile photo")
            }
    }

    fun changePassword(newPassword: String, callback: Callback?) {
        val user = auth.currentUser
        if (user == null) {
            callback?.onFailure("Not signed in.")
            return
        }
        user.updatePassword(newPassword)
            .addOnSuccessListener {
                callback?.onSuccess()
            }
            .addOnFailureListener { e ->
                callback?.onFailure(e.message ?: "Change password failed")
            }
    }

    fun sendPasswordResetEmail(email: String?, callback: Callback?) {
        if (email.isNullOrBlank()) {
            callback?.onFailure("Email address is required.")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener {
                callback?.onSuccess()
            }
            .addOnFailureListener { e ->
                callback?.onFailure(e.message ?: "Reset email failed")
            }
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    private fun isSuperAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val e = email.trim().lowercase(Locale.ROOT)
        return e == "jamesklintonj428@gmail.com"
    }

    private fun fetchUserDocument(uid: String) {
        db.collection(COLLECTION_USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                cancelTimeoutGuard()
                loading.value = false
                if (snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null && isSuperAdminEmail(user.email)) {
                        user.roleEnum = Role.SUPER_ADMIN
                        user.isVerified = true
                        val superAdminMap = mapOf<String, Any>(
                            "role" to Role.SUPER_ADMIN.name,
                            "isVerified" to true
                        )
                        db.collection(COLLECTION_USERS).document(uid)
                            .set(superAdminMap, SetOptions.merge())
                    }
                    currentUser.value = user
                } else {
                    errorMessage.value = "User profile not found. Please contact support."
                }
            }
            .addOnFailureListener { e ->
                cancelTimeoutGuard()
                loading.value = false
                errorMessage.value = "Failed to load profile: ${e.message}"
            }
    }

    companion object {
        const val COLLECTION_USERS = "users"
        private const val TIMEOUT_MS = 15_000L

        @Volatile
        private var instance: AuthRepository? = null

        @JvmStatic
        fun getInstance(): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
        }
    }
}
