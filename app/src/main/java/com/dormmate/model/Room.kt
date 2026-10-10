package com.dormmate.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
class Room {

    @DocumentId
    var roomId: String? = null

    @get:PropertyName("roomNumber")
    @set:PropertyName("roomNumber")
    var roomNumber: String? = null

    @get:PropertyName("monthlyRent")
    @set:PropertyName("monthlyRent")
    var monthlyRent: Double = 0.0

    @get:PropertyName("capacity")
    @set:PropertyName("capacity")
    var capacity: Int = 0

    @get:PropertyName("adminUid")
    @set:PropertyName("adminUid")
    var adminUid: String? = null

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null

    constructor()

    @JvmOverloads
    constructor(
        roomNumber: String?,
        monthlyRent: Double,
        capacity: Int,
        adminUid: String? = null
    ) {
        this.roomNumber = roomNumber
        this.monthlyRent = monthlyRent
        this.capacity = capacity
        this.adminUid = adminUid
    }
}
