package com.dormmate.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dormmate.model.Announcement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AnnouncementsRepository private constructor() {

    interface Callback {
        fun onSuccess()
        fun onFailure(error: String)
    }

    private val db = FirebaseFirestore.getInstance()

    private var allListener: ListenerRegistration? = null
    private var latestListener: ListenerRegistration? = null

    fun getAllAnnouncements(): LiveData<List<Announcement>> {
        val liveData = MutableLiveData<List<Announcement>>()

        allListener?.remove()
        allListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "getAllAnnouncements listen failed", error)
                    liveData.value = emptyList()
                    return@addSnapshotListener
                }
                val list = mutableListOf<Announcement>()
                if (snapshots != null) {
                    list.addAll(snapshots.toObjects(Announcement::class.java))
                }
                liveData.value = list
            }

        return liveData
    }

    fun getAnnouncementsForPoster(posterUid: String?): LiveData<List<Announcement>> {
        val liveData = MutableLiveData<List<Announcement>>()

        allListener?.remove()
        allListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "getAnnouncementsForPoster listen failed", error)
                    liveData.value = emptyList()
                    return@addSnapshotListener
                }
                val list = mutableListOf<Announcement>()
                if (snapshots != null) {
                    for (a in snapshots.toObjects(Announcement::class.java)) {
                        if (posterUid.isNullOrBlank() || a.postedByUid.isNullOrBlank() || a.postedByUid == posterUid) {
                            list.add(a)
                        }
                    }
                }
                liveData.value = list
            }

        return liveData
    }

    fun getLatest(): LiveData<Announcement?> {
        val liveData = MutableLiveData<Announcement?>()

        latestListener?.remove()
        latestListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "getLatest listen failed", error)
                    liveData.value = null
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    liveData.value = snapshots.documents[0].toObject(Announcement::class.java)
                } else {
                    liveData.value = null
                }
            }

        return liveData
    }

    fun getLatestForPoster(posterUid: String?): LiveData<Announcement?> {
        val liveData = MutableLiveData<Announcement?>()

        latestListener?.remove()
        latestListener = db.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "getLatestForPoster listen failed", error)
                    liveData.value = null
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    for (a in snapshots.toObjects(Announcement::class.java)) {
                        if (posterUid.isNullOrBlank() || a.postedByUid.isNullOrBlank() || a.postedByUid == posterUid) {
                            liveData.value = a
                            return@addSnapshotListener
                        }
                    }
                }
                liveData.value = null
            }

        return liveData
    }

    fun postAnnouncement(
        title: String,
        body: String,
        postedByName: String?,
        postedByUid: String?,
        callback: Callback?
    ) {
        val announcement = Announcement(title, body, postedByName, postedByUid)
        db.collection(COL)
            .add(announcement)
            .addOnSuccessListener { callback?.onSuccess() }
            .addOnFailureListener { e -> callback?.onFailure(e.message ?: "Failed to post") }
    }

    fun stopListening() {
        allListener?.remove()
        allListener = null
        latestListener?.remove()
        latestListener = null
    }

    companion object {
        private const val TAG = "AnnouncementsRepo"
        const val COL = "announcements"

        @Volatile
        private var instance: AnnouncementsRepository? = null

        @JvmStatic
        fun getInstance(): AnnouncementsRepository {
            return instance ?: synchronized(this) {
                instance ?: AnnouncementsRepository().also { instance = it }
            }
        }
    }
}
