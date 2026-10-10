package com.dormmate.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dormmate.model.Role
import com.dormmate.model.Room
import com.dormmate.model.RoomWithOccupants
import com.dormmate.model.User
import com.dormmate.util.AvatarUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class RoomsRepository private constructor() {

    interface Callback {
        fun onSuccess(roomId: String)
        fun onFailure(error: String)
    }

    private val db = FirebaseFirestore.getInstance()
    private var roomsListener: ListenerRegistration? = null
    private var occupancyRoomsListener: ListenerRegistration? = null
    private var occupancyUsersListener: ListenerRegistration? = null

    private var occupancyLiveData: MutableLiveData<List<RoomWithOccupants>>? = null

    fun getRooms(adminUid: String? = null): LiveData<List<Room>> {
        val liveData = MutableLiveData<List<Room>>()
        stopListening()
        roomsListener = db.collection(COL)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    Log.e(TAG, "getRooms error", e)
                    liveData.value = emptyList()
                    return@addSnapshotListener
                }
                val allRooms = snap.toObjects(Room::class.java)
                val filtered = mutableListOf<Room>()
                for (r in allRooms) {
                    if (adminUid.isNullOrBlank() || r.adminUid.isNullOrBlank() || r.adminUid == adminUid) {
                        filtered.add(r)
                    }
                }
                filtered.sortWith { a, b ->
                    safe(a.roomNumber).compareTo(safe(b.roomNumber), ignoreCase = true)
                }
                liveData.value = filtered
            }
        return liveData
    }

    private fun safe(s: String?): String = s ?: ""

    private fun normalizeRoom(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.trim().uppercase(Locale.ROOT).replace("^ROOM\\s*".toRegex(), "")
    }

    @Synchronized
    fun getRoomsWithOccupancy(targetAdminUid: String? = null): LiveData<List<RoomWithOccupants>> {
        val liveData = MutableLiveData<List<RoomWithOccupants>>()
        stopOccupancyListening()

        occupancyRoomsListener = db.collection(COL)
            .addSnapshotListener { roomSnap, roomError ->
                if (roomError != null || roomSnap == null) {
                    Log.e(TAG, "getRoomsWithOccupancy rooms error", roomError)
                    liveData.value = emptyList()
                    return@addSnapshotListener
                }
                val allRooms = roomSnap.toObjects(Room::class.java)
                val rooms = mutableListOf<Room>()
                for (r in allRooms) {
                    if (targetAdminUid.isNullOrBlank() || r.adminUid.isNullOrBlank() || r.adminUid == targetAdminUid) {
                        rooms.add(r)
                    }
                }
                rooms.sortWith { a, b ->
                    safe(a.roomNumber).compareTo(safe(b.roomNumber), ignoreCase = true)
                }

                var lastValidOccupantsDocs: List<DocumentSnapshot>? = null

                fun processOccupantDocs(docs: List<DocumentSnapshot>) {
                    if (docs.isEmpty() && !lastValidOccupantsDocs.isNullOrEmpty()) {
                        return
                    }

                    val roomOccupantsMap = mutableMapOf<String, MutableList<String>>()
                    for (doc in docs) {
                        val isVer = doc.getBoolean("isVerified") == true ||
                                doc.getBoolean("verified") == true ||
                                doc.get("approvedBy") != null ||
                                doc.get("approvedAt") != null
                        val rm = doc.getString("roomNumber")
                        val docAdminId = doc.getString("adminUid").orEmpty().ifBlank { doc.getString("approvedBy").orEmpty() }
                        val matchesAdmin = targetAdminUid.isNullOrBlank() ||
                                docAdminId.isBlank() ||
                                docAdminId == targetAdminUid

                        if (isVer && !rm.isNullOrBlank() && matchesAdmin) {
                            val rmKey = normalizeRoom(rm)
                            val list = roomOccupantsMap.getOrPut(rmKey) { mutableListOf() }
                            val rawName = doc.getString("name").orEmpty().ifBlank { doc.getString("email").orEmpty() }
                            if (rawName.isNotBlank()) {
                                list.add(AvatarUtils.formatDisplayName(rawName))
                            }
                        }
                    }

                    val result = mutableListOf<RoomWithOccupants>()
                    for (r in rooms) {
                        val key = normalizeRoom(r.roomNumber)
                        val occupants = roomOccupantsMap[key] ?: emptyList()
                        result.add(RoomWithOccupants(r, occupants.size, occupants))
                    }

                    if (roomOccupantsMap.isNotEmpty()) {
                        lastValidOccupantsDocs = docs
                    }
                    liveData.value = result
                }

                occupancyUsersListener?.remove()

                occupancyUsersListener = db.collection("users")
                    .whereEqualTo("role", Role.RESIDENT.name)
                    .addSnapshotListener { userSnap, userError ->
                        if (userError == null && userSnap != null && !userSnap.isEmpty) {
                            processOccupantDocs(userSnap.documents)
                        } else {
                            db.collection("resident_directory")
                                .addSnapshotListener { dirSnap, dirError ->
                                    if (dirError == null && dirSnap != null) {
                                        processOccupantDocs(dirSnap.documents)
                                    } else {
                                        processOccupantDocs(userSnap?.documents ?: emptyList())
                                    }
                                }
                        }
                    }
            }
        return liveData
    }

    fun stopListening() {
        roomsListener?.remove()
        roomsListener = null
        stopOccupancyListening()
    }

    private fun stopOccupancyListening() {
        occupancyRoomsListener?.remove()
        occupancyRoomsListener = null
        occupancyUsersListener?.remove()
        occupancyUsersListener = null
    }

    fun addRoom(room: Room, callback: Callback?) {
        val docRef = db.collection(COL).document()
        room.roomId = docRef.id

        docRef.set(room)
            .addOnSuccessListener {
                callback?.onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "addRoom failed", e)
                callback?.onFailure(e.message ?: "Failed to add room")
            }
    }

    fun updateRoom(
        roomId: String,
        roomNumber: String,
        monthlyRent: Double,
        capacity: Int,
        callback: Callback?
    ) {
        val updates = mutableMapOf<String, Any>()
        updates["roomNumber"] = roomNumber
        updates["monthlyRent"] = monthlyRent
        updates["capacity"] = capacity

        db.collection(COL).document(roomId).update(updates)
            .addOnSuccessListener {
                callback?.onSuccess(roomId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "updateRoom failed", e)
                callback?.onFailure(e.message ?: "Failed to update room")
            }
    }

    fun deleteRoom(roomId: String, callback: Callback?) {
        db.collection(COL).document(roomId).delete()
            .addOnSuccessListener {
                callback?.onSuccess(roomId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteRoom failed", e)
                callback?.onFailure(e.message ?: "Failed to delete room")
            }
    }

    companion object {
        private const val TAG = "RoomsRepository"
        const val COL = "rooms"

        @Volatile
        private var instance: RoomsRepository? = null

        @JvmStatic
        fun getInstance(): RoomsRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomsRepository().also { instance = it }
            }
        }
    }
}
