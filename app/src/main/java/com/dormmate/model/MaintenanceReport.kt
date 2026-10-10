package com.dormmate.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
class MaintenanceReport {

    enum class Status { PENDING, IN_PROGRESS, RESOLVED }

    @DocumentId
    var reportId: String? = null

    @get:PropertyName("residentUid")
    @set:PropertyName("residentUid")
    var residentUid: String? = null

    @get:PropertyName("residentName")
    @set:PropertyName("residentName")
    var residentName: String? = null

    @get:PropertyName("roomNumber")
    @set:PropertyName("roomNumber")
    var roomNumber: String? = null

    @get:PropertyName("title")
    @set:PropertyName("title")
    var title: String? = null

    @get:PropertyName("description")
    @set:PropertyName("description")
    var description: String? = null

    @get:PropertyName("photoUrl")
    @set:PropertyName("photoUrl")
    var photoUrl: String? = null

    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String? = null

    @get:PropertyName("assignedStaffUid")
    @set:PropertyName("assignedStaffUid")
    var assignedStaffUid: String? = null

    @get:PropertyName("assignedStaffName")
    @set:PropertyName("assignedStaffName")
    var assignedStaffName: String? = null

    @get:PropertyName("adminUid")
    @set:PropertyName("adminUid")
    var adminUid: String? = null

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Date? = null

    constructor()

    constructor(
        residentUid: String?,
        residentName: String?,
        roomNumber: String?,
        title: String?,
        description: String?
    ) {
        this.residentUid = residentUid
        this.residentName = residentName
        this.roomNumber = roomNumber
        this.title = title
        this.description = description
        this.status = Status.PENDING.name
        this.photoUrl = null
        this.assignedStaffUid = null
        this.assignedStaffName = null
    }

    var statusEnum: Status
        @Exclude
        get() = try {
            Status.valueOf(status ?: Status.PENDING.name)
        } catch (e: Exception) {
            Status.PENDING
        }
        @Exclude
        set(s) {
            this.status = s.name
        }
}
