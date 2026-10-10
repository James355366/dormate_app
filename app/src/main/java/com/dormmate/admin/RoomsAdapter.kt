package com.dormmate.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Room
import com.google.android.material.button.MaterialButton
import java.util.Locale

class RoomsAdapter(
    private val listener: OnRoomActionListener?
) : ListAdapter<Room, RoomsAdapter.ViewHolder>(DIFF_CALLBACK) {

    interface OnRoomActionListener {
        fun onEdit(room: Room)
        fun onDelete(room: Room)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = getItem(position) ?: return
        val context = holder.itemView.context

        holder.tvRoomNumber.text = context.getString(R.string.label_room, room.roomNumber)
        holder.tvRoomPrice.text = String.format(Locale.US, "₱%,.2f / month", room.monthlyRent)

        if (room.capacity > 0) {
            holder.tvRoomCapacity.text = String.format(Locale.US, "Max capacity: %d occupants", room.capacity)
            holder.tvRoomCapacity.visibility = View.VISIBLE
        } else {
            holder.tvRoomCapacity.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { listener?.onEdit(room) }
        holder.btnDelete.setOnClickListener { listener?.onDelete(room) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRoomNumber: TextView = itemView.findViewById(R.id.tv_room_number)
        val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)
        val tvRoomCapacity: TextView = itemView.findViewById(R.id.tv_room_capacity)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.btn_edit_room)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btn_delete_room)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Room>() {
            override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
                return oldItem.roomId != null && oldItem.roomId == newItem.roomId
            }

            override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
                return oldItem.roomNumber == newItem.roomNumber &&
                        oldItem.monthlyRent == newItem.monthlyRent &&
                        oldItem.capacity == newItem.capacity
            }
        }
    }
}
