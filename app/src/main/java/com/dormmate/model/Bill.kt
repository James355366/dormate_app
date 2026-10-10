package com.dormmate.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
class Bill {

    @DocumentId
    var billId: String? = null

    @get:PropertyName("residentUid")
    @set:PropertyName("residentUid")
    var residentUid: String? = null

    @get:PropertyName("residentName")
    @set:PropertyName("residentName")
    var residentName: String? = null

    @get:PropertyName("roomNumber")
    @set:PropertyName("roomNumber")
    var roomNumber: String? = null

    @get:PropertyName("adminUid")
    @set:PropertyName("adminUid")
    var adminUid: String? = null

    @get:PropertyName("month")
    @set:PropertyName("month")
    var month: Int = 0

    @get:PropertyName("year")
    @set:PropertyName("year")
    var year: Int = 0

    @get:PropertyName("roomRent")
    @set:PropertyName("roomRent")
    var roomRent: Double = 0.0

    @get:PropertyName("electricity")
    @set:PropertyName("electricity")
    var electricity: Double = 0.0

    @get:PropertyName("water")
    @set:PropertyName("water")
    var water: Double = 0.0

    @get:PropertyName("wifi")
    @set:PropertyName("wifi")
    var wifi: Double = 0.0

    @get:PropertyName("totalAmount")
    @set:PropertyName("totalAmount")
    var totalAmount: Double = 0.0

    @get:PropertyName("isPaid")
    @set:PropertyName("isPaid")
    var isPaid: Boolean = false

    @get:PropertyName("dueDate")
    @set:PropertyName("dueDate")
    var dueDate: Date? = null

    @get:PropertyName("paidAt")
    @set:PropertyName("paidAt")
    var paidAt: Date? = null

    @get:PropertyName("hasSurcharge")
    @set:PropertyName("hasSurcharge")
    var isHasSurcharge: Boolean = false

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null

    @get:PropertyName("paymentMethod")
    @set:PropertyName("paymentMethod")
    var paymentMethod: String? = null

    @get:PropertyName("gcashRefNumber")
    @set:PropertyName("gcashRefNumber")
    var gcashRefNumber: String? = null

    @get:PropertyName("gcashReceiptUrl")
    @set:PropertyName("gcashReceiptUrl")
    var gcashReceiptUrl: String? = null

    @get:PropertyName("paymentStatus")
    @set:PropertyName("paymentStatus")
    var paymentStatus: String? = null

    constructor()

    constructor(
        residentUid: String?,
        residentName: String?,
        roomNumber: String?,
        month: Int,
        year: Int,
        roomRent: Double,
        electricity: Double,
        water: Double,
        wifi: Double,
        dueDate: Date?
    ) {
        this.residentUid = residentUid
        this.residentName = residentName
        this.roomNumber = roomNumber
        this.month = month
        this.year = year
        this.roomRent = roomRent
        this.electricity = electricity
        this.water = water
        this.wifi = wifi
        this.totalAmount = roomRent + electricity + water + wifi
        this.dueDate = dueDate
        this.isPaid = false
        this.isHasSurcharge = false
    }

    fun isOverdue(): Boolean {
        return !isPaid && dueDate != null && Date().after(dueDate)
    }

    fun getEffectiveAmount(): Double {
        return if (isOverdue()) {
            totalAmount * (1 + SURCHARGE_RATE)
        } else {
            totalAmount
        }
    }

    companion object {
        const val SURCHARGE_RATE: Double = 0.05
    }
}
