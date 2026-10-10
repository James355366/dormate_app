package com.dormmate.home

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.User
import com.google.android.material.button.MaterialButton

class PendingUsersAdapter(
    private val listener: OnPendingUserActionListener?
) : ListAdapter<User, PendingUsersAdapter.VH>(DIFF_CALLBACK) {

    interface OnPendingUserActionListener {
        fun onApprove(user: User)
        fun onReject(user: User)
    }

    private val busy = mutableSetOf<String>()

    fun setBusy(uid: String, isBusy: Boolean) {
        if (isBusy) busy.add(uid) else busy.remove(uid)
        val list = currentList
        for (i in list.indices) {
            if (uid == list[i].uid) {
                notifyItemChanged(i)
                break
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val user = getItem(position) ?: return

        val name = user.name
        h.tvName.text = if (name.isNullOrEmpty()) h.itemView.context.getString(R.string.approvals_unnamed) else name
        val email = user.email
        val phone = user.phoneNumber
        if (!phone.isNullOrBlank()) {
            h.tvEmail.text = if (!email.isNullOrBlank()) "$email • $phone" else phone
        } else {
            h.tvEmail.text = email ?: ""
        }

        val meta = StringBuilder()
        val room = user.roomNumber
        if (!room.isNullOrEmpty()) {
            meta.append(h.itemView.context.getString(R.string.label_room, room))
        }
        if (user.createdAt != null) {
            if (meta.isNotEmpty()) meta.append(" • ")
            val date = DateFormat.format("MMM d, yyyy", user.createdAt).toString()
            meta.append(h.itemView.context.getString(R.string.approvals_registered, date))
        }
        h.tvMeta.text = meta
        h.tvMeta.visibility = if (meta.isNotEmpty()) View.VISIBLE else View.GONE

        val isUserBusy = busy.contains(user.uid)
        h.btnApprove.isEnabled = !isUserBusy
        h.btnReject.isEnabled = !isUserBusy

        h.btnApprove.setOnClickListener { listener?.onApprove(user) }
        h.btnReject.setOnClickListener { listener?.onReject(user) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_user_name)
        val tvEmail: TextView = itemView.findViewById(R.id.tv_user_email)
        val tvMeta: TextView = itemView.findViewById(R.id.tv_user_meta)
        val btnApprove: MaterialButton = itemView.findViewById(R.id.btn_approve)
        val btnReject: MaterialButton = itemView.findViewById(R.id.btn_reject)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(a: User, b: User): Boolean {
                return a.uid != null && a.uid == b.uid
            }

            override fun areContentsTheSame(a: User, b: User): Boolean {
                return safe(a.name) == safe(b.name) &&
                        safe(a.email) == safe(b.email) &&
                        safe(a.roomNumber) == safe(b.roomNumber)
            }

            private fun safe(s: String?): String = s ?: ""
        }
    }
}
