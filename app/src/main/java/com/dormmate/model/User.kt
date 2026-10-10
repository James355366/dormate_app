package com.dormmate.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
class User {
    @DocumentId
    var uid: String? = null

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String? = null

    @get:PropertyName("email")
    @set:PropertyName("email")
    var email: String? = null

    @get:PropertyName("role")
    @set:PropertyName("role")
    var role: String? = null

    @get:PropertyName("roomNumber")
    @set:PropertyName("roomNumber")
    var roomNumber: String? = null

    @get:PropertyName("adminUid")
    @set:PropertyName("adminUid")
    var rawAdminUid: String? = null

    @get:PropertyName("adminName")
    @set:PropertyName("adminName")
    var adminName: String? = null

    @get:PropertyName("approvedBy")
    @set:PropertyName("approvedBy")
    var approvedBy: String? = null

    @get:PropertyName("isVerified")
    @set:PropertyName("isVerified")
    var isVerified: Boolean = false

    @get:PropertyName("fcmToken")
    @set:PropertyName("fcmToken")
    var fcmToken: String? = null

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null

    @get:PropertyName("profileImageUrl")
    @set:PropertyName("profileImageUrl")
    var profileImageUrl: String? = null

    @get:PropertyName("gcashName")
    @set:PropertyName("gcashName")
    var gcashName: String? = null

    @get:PropertyName("gcashNumber")
    @set:PropertyName("gcashNumber")
    var gcashNumber: String? = null

    @get:PropertyName("gcashQrUrl")
    @set:PropertyName("gcashQrUrl")
    var gcashQrUrl: String? = null

    @get:PropertyName("phoneNumber")
    @set:PropertyName("phoneNumber")
    var phoneNumber: String? = null

    constructor()

    @JvmOverloads
    constructor(
        uid: String?,
        name: String?,
        email: String?,
        role: Role,
        roomNumber: String?,
        adminUid: String? = null,
        phoneNumber: String? = null
    ) {
        this.uid = uid
        this.name = name
        this.email = email
        this.role = role.name
        this.roomNumber = roomNumber
        this.rawAdminUid = adminUid
        this.phoneNumber = phoneNumber
        this.isVerified = role != Role.RESIDENT
        this.fcmToken = null
    }

    var roleEnum: Role
        @Exclude
        get() = Role.fromString(role)
        @Exclude
        set(value) {
            role = value.name
        }

    val adminUid: String?
        @Exclude
        get() {
            if (!rawAdminUid.isNullOrBlank()) return rawAdminUid
            if (!approvedBy.isNullOrBlank()) return approvedBy
            return null
        }

    val isUserVerified: Boolean
        @Exclude
        get() = isVerified || !approvedBy.isNullOrBlank() || roleEnum != Role.RESIDENT

    @PropertyName("verified")
    fun setLegacyVerified(verified: Boolean) {
        isVerified = verified
    }

    override fun toString(): String {
        return "User(uid='$uid', name='$name', role='$role', room='$roomNumber', adminUid='$adminUid', approvedBy='$approvedBy', verified=$isVerified)"
    }
}
