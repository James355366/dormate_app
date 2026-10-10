package com.dormmate.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
class Announcement {

    @DocumentId
    var announcementId: String? = null

    @get:PropertyName("title")
    @set:PropertyName("title")
    var title: String? = null

    @get:PropertyName("body")
    @set:PropertyName("body")
    var body: String? = null

    @get:PropertyName("postedByName")
    @set:PropertyName("postedByName")
    var postedByName: String? = null

    @get:PropertyName("postedByUid")
    @set:PropertyName("postedByUid")
    var postedByUid: String? = null

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null

    constructor()

    constructor(title: String?, body: String?, postedByName: String?, postedByUid: String?) {
        this.title = title
        this.body = body
        this.postedByName = postedByName
        this.postedByUid = postedByUid
    }
}
