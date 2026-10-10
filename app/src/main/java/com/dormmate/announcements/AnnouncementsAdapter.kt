package com.dormmate.announcements

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Announcement
import java.text.SimpleDateFormat
import java.util.Locale

class AnnouncementsAdapter(private val userUid: String) : ListAdapter<Announcement, AnnouncementsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), userUid)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_announcement_title)
        private val body: TextView = itemView.findViewById(R.id.tv_announcement_body)
        private val meta: TextView = itemView.findViewById(R.id.tv_announcement_meta)

        fun bind(a: Announcement, uid: String) {
            val prefs = itemView.context.getSharedPreferences("announcement_reads_$uid", android.content.Context.MODE_PRIVATE)
            val id = a.announcementId ?: return
            val version = "${a.title}\n${a.body}"
            val unread: TextView = itemView.findViewById(R.id.tv_announcement_unread)
            unread.visibility = if (prefs.getString(id, null) == version) View.GONE else View.VISIBLE
            itemView.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(a.title).setMessage(a.body).setPositiveButton("Close", null).show()
                prefs.edit().putString(id, version).apply()
                unread.visibility = View.GONE
            }
            title.text = a.title
            body.text = a.body

            val posterName = if (!a.postedByName.isNullOrBlank()) a.postedByName else "Admin"
            val dateStr = if (a.createdAt != null) DATE_FORMAT.format(a.createdAt!!) else "Just now"
            meta.text = "Posted by $posterName • $dateStr"
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        private val DIFF = object : DiffUtil.ItemCallback<Announcement>() {
            override fun areItemsTheSame(a: Announcement, b: Announcement): Boolean {
                return a.announcementId == b.announcementId
            }

            override fun areContentsTheSame(a: Announcement, b: Announcement): Boolean {
                return a.title == b.title && a.body == b.body
            }
        }
    }
}
