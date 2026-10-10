package com.dormmate.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.RoomWithOccupants
import java.util.Locale

class RoomDirectoryAdapter(
    private val listener: OnItemClickListener? = null
) : ListAdapter<RoomWithOccupants, RoomDirectoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    fun interface OnItemClickListener {
        fun onItemClick(item: RoomWithOccupants)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_directory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val room = item.room ?: return
        val context = holder.itemView.context

        holder.tvRoomNumber.text = context.getString(R.string.label_room, room.roomNumber)
        holder.tvRoomPrice.text = String.format(Locale.US, "₱%,.2f / month", room.monthlyRent)

        if (item.isFull()) {
            holder.tvBadge.text = item.getStatusText()
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_error_container))
            holder.tvBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.md_theme_error_container)
            )
        } else {
            holder.tvBadge.text = item.getStatusText()
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.availability_green))
            holder.tvBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.availability_green_container)
            )
        }

        val occupants = item.occupantNames
        if (!occupants.isNullOrEmpty()) {
            val sb = StringBuilder("Occupants: ")
            for (i in occupants.indices) {
                sb.append(occupants[i])
                if (i < occupants.size - 1) sb.append(", ")
            }
            holder.tvOccupants.text = sb.toString()
            holder.tvOccupants.visibility = View.VISIBLE
        } else {
            holder.tvOccupants.text = "No occupants yet"
            holder.tvOccupants.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            listener?.onItemClick(item)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRoomNumber: TextView = itemView.findViewById(R.id.tv_room_number)
        val tvRoomPrice: TextView = itemView.findViewById(R.id.tv_room_price)
        val tvBadge: TextView = itemView.findViewById(R.id.tv_room_status_badge)
        val tvOccupants: TextView = itemView.findViewById(R.id.tv_room_occupants_list)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RoomWithOccupants>() {
            override fun areItemsTheSame(a: RoomWithOccupants, b: RoomWithOccupants): Boolean {
                val ra = a.room
                val rb = b.room
                return ra != null && rb != null && ra.roomId != null && ra.roomId == rb.roomId
            }

            override fun areContentsTheSame(a: RoomWithOccupants, b: RoomWithOccupants): Boolean {
                val ra = a.room
                val rb = b.room
                return ra != null && rb != null &&
                        ra.roomNumber == rb.roomNumber &&
                        a.occupantCount == b.occupantCount &&
                        a.isFull() == b.isFull() &&
                        a.occupantNames?.size == b.occupantNames?.size &&
                        ra.monthlyRent == rb.monthlyRent && ra.capacity == rb.capacity
            }
        }
    }
}
