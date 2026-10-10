package com.dormmate.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dormmate.model.Bill
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date

class BillsRepository private constructor() {

    interface Callback {
        fun onSuccess(id: String)
        fun onFailure(error: String)
    }

    val loadError = MutableLiveData<String?>()
    private val db = FirebaseFirestore.getInstance()

    private var residentListener: ListenerRegistration? = null
    private var allBillsListener: ListenerRegistration? = null
    private var monthListener: ListenerRegistration? = null

    private fun attachPaymentsToBills(bills: List<Bill>, onComplete: (List<Bill>) -> Unit) {
        if (bills.isEmpty()) {
            onComplete(bills)
            return
        }
        db.collection("users").get()
            .addOnSuccessListener { userSnap ->
                val userPayMap = mutableMapOf<String, Map<String, Any>>()
                for (doc in userSnap.documents) {
                    val data = doc.data ?: continue
                    for ((key, value) in data) {
                        if (key.startsWith("pendingPayment_") && value is Map<*, *>) {
                            val targetBillId = key.removePrefix("pendingPayment_")
                            @Suppress("UNCHECKED_CAST")
                            userPayMap[targetBillId] = value as Map<String, Any>
                        }
                    }
                }
                for (b in bills) {
                    val id = b.billId
                    if (!id.isNullOrBlank() && userPayMap.containsKey(id)) {
                        val pData = userPayMap[id]!!
                        if (!b.isPaid) {
                            val method = pData["paymentMethod"] as? String
                            val ref = pData["gcashRefNumber"] as? String
                            val url = pData["gcashReceiptUrl"] as? String
                            val status = pData["paymentStatus"] as? String
                            if (!method.isNullOrBlank()) b.paymentMethod = method
                            if (!ref.isNullOrBlank()) b.gcashRefNumber = ref
                            if (!url.isNullOrBlank()) b.gcashReceiptUrl = url
                            if (!status.isNullOrBlank()) b.paymentStatus = status
                        }
                    }
                }
                onComplete(bills)
            }
            .addOnFailureListener {
                onComplete(bills)
            }
    }

    private fun filterAndPurgeOrphanedBills(
        bills: List<Bill>,
        onResult: (List<Bill>) -> Unit
    ) {
        db.collection("users").get()
            .addOnSuccessListener { userSnap ->
                val activeUids = userSnap.documents.map { it.id }.toSet()
                val validBills = mutableListOf<Bill>()
                for (b in bills) {
                    val resUid = b.residentUid
                    if (!resUid.isNullOrBlank() && !activeUids.contains(resUid)) {
                        val id = b.billId
                        if (!id.isNullOrBlank()) {
                            db.collection(COL).document(id).delete()
                        }
                    } else {
                        validBills.add(b)
                    }
                }
                attachPaymentsToBills(validBills, onResult)
            }
            .addOnFailureListener {
                attachPaymentsToBills(bills, onResult)
            }
    }

    fun getBillsForResident(residentUid: String): LiveData<List<Bill>> {
        loadError.value = null
        val liveData = MutableLiveData<List<Bill>>()
        stopResidentListener()
        residentListener = db.collection(COL)
            .whereEqualTo("residentUid", residentUid)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getBillsForResident error", e)
                    loadError.value = "Unable to load bills. Pull down to retry."
                    return@addSnapshotListener
                }
                loadError.value = null
                val bills = snap.toObjects(Bill::class.java)
                bills.sortWith { a, b ->
                    val y = b.year.compareTo(a.year)
                    if (y != 0) y else b.month.compareTo(a.month)
                }
                flagSurchargeIfOverdue(bills)
                filterAndPurgeOrphanedBills(bills) { liveData.value = it }
            }
        return liveData
    }

    fun getAllBills(): LiveData<List<Bill>> {
        loadError.value = null
        val liveData = MutableLiveData<List<Bill>>()
        stopAllBillsListener()
        allBillsListener = db.collection(COL)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getAllBills error", e)
                    loadError.value = "Unable to load bills. Pull down to retry."
                    return@addSnapshotListener
                }
                loadError.value = null
                val bills = snap.toObjects(Bill::class.java)
                bills.sortWith { a, b ->
                    val y = b.year.compareTo(a.year)
                    if (y != 0) y else b.month.compareTo(a.month)
                }
                flagSurchargeIfOverdue(bills)
                filterAndPurgeOrphanedBills(bills) { liveData.value = it }
            }
        return liveData
    }

    fun getBillsForAdmin(adminUid: String): LiveData<List<Bill>> {
        loadError.value = null
        val liveData = MutableLiveData<List<Bill>>()
        stopAllBillsListener()
        allBillsListener = db.collection(COL)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getBillsForAdmin error", e)
                    loadError.value = "Unable to load bills. Pull down to retry."
                    return@addSnapshotListener
                }
                loadError.value = null
                val all = snap.toObjects(Bill::class.java)
                val filtered = mutableListOf<Bill>()
                for (b in all) {
                    if (b.adminUid.isNullOrBlank() || b.adminUid == adminUid) {
                        filtered.add(b)
                    }
                }
                filtered.sortWith { a, b ->
                    val y = b.year.compareTo(a.year)
                    if (y != 0) y else b.month.compareTo(a.month)
                }
                flagSurchargeIfOverdue(filtered)
                filterAndPurgeOrphanedBills(filtered) { liveData.value = it }
            }
        return liveData
    }

    fun getBillsByMonth(month: Int, year: Int): LiveData<List<Bill>> {
        loadError.value = null
        val liveData = MutableLiveData<List<Bill>>()
        stopMonthListener()
        monthListener = db.collection(COL)
            .whereEqualTo("month", month)
            .whereEqualTo("year", year)
            .orderBy("residentUid", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getBillsByMonth error", e)
                    loadError.value = "Unable to load bills. Pull down to retry."
                    return@addSnapshotListener
                }
                loadError.value = null
                val bills = snap.toObjects(Bill::class.java)
                flagSurchargeIfOverdue(bills)
                filterAndPurgeOrphanedBills(bills) { liveData.value = it }
            }
        return liveData
    }

    fun getBillsByMonthForAdmin(month: Int, year: Int, adminUid: String): LiveData<List<Bill>> {
        loadError.value = null
        val liveData = MutableLiveData<List<Bill>>()
        stopMonthListener()
        monthListener = db.collection(COL)
            .whereEqualTo("month", month)
            .whereEqualTo("year", year)
            .orderBy("residentUid", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getBillsByMonthForAdmin error", e)
                    loadError.value = "Unable to load bills. Pull down to retry."
                    return@addSnapshotListener
                }
                val all = snap.toObjects(Bill::class.java)
                val filtered = mutableListOf<Bill>()
                for (b in all) {
                    if (b.adminUid.isNullOrBlank() || b.adminUid == adminUid) {
                        filtered.add(b)
                    }
                }
                flagSurchargeIfOverdue(filtered)
                filterAndPurgeOrphanedBills(filtered) { liveData.value = it }
            }
        return liveData
    }

    fun stopListening() {
        stopResidentListener()
        stopAllBillsListener()
        stopMonthListener()
    }

    private fun stopResidentListener() {
        residentListener?.remove()
        residentListener = null
    }

    private fun stopAllBillsListener() {
        allBillsListener?.remove()
        allBillsListener = null
    }

    private fun stopMonthListener() {
        monthListener?.remove()
        monthListener = null
    }

    fun generateBill(bill: Bill, callback: Callback?) {
        val docRef = db.collection(COL).document()
        bill.billId = docRef.id

        docRef.set(bill)
            .addOnSuccessListener {
                callback?.onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "generateBill failed", e)
                callback?.onFailure(e.message ?: "Failed to generate bill")
            }
    }

    fun confirmPayment(billId: String, callback: Callback?) {
        val updates = mutableMapOf<String, Any?>()
        updates["isPaid"] = true
        updates["paidAt"] = Date()
        updates["hasSurcharge"] = false
        updates["paymentStatus"] = "PAID"

        db.collection(COL).document(billId).update(updates)
            .addOnSuccessListener {
                callback?.onSuccess(billId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "confirmPayment failed", e)
                callback?.onFailure(e.message ?: "Failed to confirm payment")
            }
    }

    fun submitGcashPayment(
        billId: String,
        refNumber: String,
        receiptUrl: String?,
        callback: Callback?
    ) {
        val updates = mutableMapOf<String, Any>()
        updates["paymentMethod"] = "GCASH"
        updates["gcashRefNumber"] = if (refNumber.isNotBlank()) refNumber else "Receipt Photo Attached"
        if (!receiptUrl.isNullOrBlank()) {
            updates["gcashReceiptUrl"] = receiptUrl
        }
        updates["paymentStatus"] = "PENDING_VERIFICATION"

        val authUid = FirebaseAuth.getInstance().uid
        if (!authUid.isNullOrBlank()) {
            updates["submittedBy"] = authUid
            val userUpdates = mutableMapOf<String, Any>()
            userUpdates["pendingPayment_$billId"] = updates

            db.collection("users").document(authUid).set(userUpdates, SetOptions.merge())
                .addOnSuccessListener {
                    db.collection("resident_directory").document(authUid).set(userUpdates, SetOptions.merge())
                    db.collection(COL).document(billId).update(updates)
                    callback?.onSuccess(billId)
                }
                .addOnFailureListener {
                    db.collection(COL).document(billId).update(updates)
                        .addOnSuccessListener { callback?.onSuccess(billId) }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "submitGcashPayment failed", e)
                            callback?.onFailure(e.message ?: "Failed to submit GCash payment")
                        }
                }
        } else {
            db.collection(COL).document(billId).update(updates)
                .addOnSuccessListener { callback?.onSuccess(billId) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "submitGcashPayment failed", e)
                    callback?.onFailure(e.message ?: "Failed to submit GCash payment")
                }
        }
    }

    fun verifyGcashPayment(billId: String, approve: Boolean, callback: Callback?) {
        val updates = mutableMapOf<String, Any?>()
        if (approve) {
            updates["isPaid"] = true
            updates["paidAt"] = Date()
            updates["hasSurcharge"] = false
            updates["paymentStatus"] = "PAID"
        } else {
            updates["paymentStatus"] = "REJECTED"
        }

        db.collection(COL).document(billId).update(updates)
            .addOnSuccessListener {
                callback?.onSuccess(billId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "verifyGcashPayment failed", e)
                callback?.onFailure(e.message ?: "Failed to verify GCash payment")
            }
    }

    fun markBillAsUnpaid(billId: String, callback: Callback?) {
        val updates = mutableMapOf<String, Any?>()
        updates["isPaid"] = false
        updates["paidAt"] = null
        updates["hasSurcharge"] = false

        db.collection(COL).document(billId).update(updates)
            .addOnSuccessListener {
                callback?.onSuccess(billId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "markBillAsUnpaid failed", e)
                callback?.onFailure(e.message ?: "Failed to mark as unpaid")
            }
    }

    fun flagSurchargeIfOverdue(bills: List<Bill>) {
        // The bill model computes the late amount locally; loading a bill never changes payment data.
        for (bill in bills) if (bill.isOverdue()) bill.isHasSurcharge = true
    }

    companion object {
        private const val TAG = "BillsRepository"
        const val COL = "bills"

        @Volatile
        private var instance: BillsRepository? = null

        @JvmStatic
        fun getInstance(): BillsRepository {
            return instance ?: synchronized(this) {
                instance ?: BillsRepository().also { instance = it }
            }
        }
    }
}
