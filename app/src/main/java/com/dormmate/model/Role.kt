package com.dormmate.model

/**
 * Represents the four roles a DormMate user can hold.
 * Stored as a String in Firestore ("RESIDENT", "DORM_ADMIN", etc.)
 */
enum class Role {
    RESIDENT,
    DORM_ADMIN,
    MAINTENANCE_STAFF,
    SUPER_ADMIN;

    fun canManageResidents(): Boolean = this == DORM_ADMIN || this == SUPER_ADMIN
    fun canManageBills(): Boolean = this == DORM_ADMIN || this == SUPER_ADMIN
    fun canUpdateTickets(): Boolean = this == MAINTENANCE_STAFF || this == DORM_ADMIN || this == SUPER_ADMIN
    fun canPostAnnouncements(): Boolean = this == DORM_ADMIN || this == SUPER_ADMIN
    fun isResidentLike(): Boolean = this == RESIDENT

    fun getDisplayName(): String {
        return when (this) {
            DORM_ADMIN -> "Dorm Admin"
            SUPER_ADMIN -> "Super Admin"
            MAINTENANCE_STAFF -> "Maintenance Staff"
            RESIDENT -> "Resident"
        }
    }

    companion object {
        @JvmStatic
        fun fromString(value: String?): Role {
            if (value == null) return RESIDENT
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                RESIDENT
            }
        }
    }
}
