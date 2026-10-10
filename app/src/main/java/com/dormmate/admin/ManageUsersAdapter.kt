package com.dormmate.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Role
import com.dormmate.model.User

class ManageUsersAdapter @JvmOverloads constructor(
    private val clickListener: OnUserClickListener? = null,
    private val editListener: OnUserActionListener? = null,
    private val deleteListener: OnUserActionListener? = null
) : ListAdapter<User, ManageUsersAdapter.ViewHolder>(DIFF_CALLBACK) {

    fun interface OnUserClickListener {
        fun onUserClick(user: User)
    }

    fun interface OnUserActionListener {
        fun onAction(user: User)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_managed_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position) ?: return
        val context = holder.itemView.context

        val name = user.name
        holder.tvName.text = if (!name.isNullOrBlank()) name else context.getString(R.string.approvals_unnamed)

        val email = user.email
        val phone = user.phoneNumber
        if (!phone.isNullOrBlank()) {
            holder.tvEmail.text = if (!email.isNullOrBlank()) "$email • $phone" else phone
        } else {
            holder.tvEmail.text = email ?: ""
        }

        val meta = StringBuilder()
        meta.append(roleLabel(user.roleEnum))

        val room = user.roomNumber
        if (!room.isNullOrBlank()) {
            meta.append(" • ").append(context.getString(R.string.label_room, room))
        }
        holder.tvMeta.text = meta.toString()

        val pending = user.roleEnum == Role.RESIDENT && !user.isVerified
        holder.tvPending.visibility = if (pending) View.VISIBLE else View.GONE

        holder.btnEdit.setOnClickListener {
            if (editListener != null) {
                editListener.onAction(user)
            } else {
                clickListener?.onUserClick(user)
            }
        }
        holder.btnDelete.setOnClickListener {
            if (deleteListener != null) {
                deleteListener.onAction(user)
            } else {
                clickListener?.onUserClick(user)
            }
        }
        holder.itemView.setOnClickListener {
            clickListener?.onUserClick(user)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_mu_name)
        val tvEmail: TextView = itemView.findViewById(R.id.tv_mu_email)
        val tvMeta: TextView = itemView.findViewById(R.id.tv_mu_meta)
        val tvPending: TextView = itemView.findViewById(R.id.tv_mu_pending)
        val btnEdit: View = itemView.findViewById(R.id.btn_edit_user)
        val btnDelete: View = itemView.findViewById(R.id.btn_delete_user)
    }

    companion object {
        @JvmStatic
        fun roleLabel(role: Role?): String {
            return when (role) {
                Role.DORM_ADMIN -> "Dorm Admin"
                Role.MAINTENANCE_STAFF -> "Maintenance Staff"
                Role.SUPER_ADMIN -> "Super Admin"
                Role.RESIDENT, null -> "Resident"
            }
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem.uid != null && oldItem.uid == newItem.uid
            }

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
                return safe(oldItem.name) == safe(newItem.name) &&
                        safe(oldItem.email) == safe(newItem.email) &&
                        safe(oldItem.roomNumber) == safe(newItem.roomNumber) &&
                        oldItem.roleEnum == newItem.roleEnum &&
                        oldItem.isVerified == newItem.isVerified
            }

            private fun safe(s: String?): String = s ?: ""
        }
    }
}
