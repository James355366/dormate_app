package com.dormmate.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Role
import com.dormmate.model.RoomWithOccupants
import com.dormmate.repository.AuthRepository
import com.dormmate.repository.RoomsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class RoomDirectoryBottomSheet : BottomSheetDialogFragment() {

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RoomDirectoryAdapter
    private val repository = RoomsRepository.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_room_directory, container, false)
    }

    override fun onStart() {
        super.onStart()
        val d = dialog
        if (d is BottomSheetDialog) {
            val b = d.behavior
            b.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress = view.findViewById(R.id.progress_directory)
        tvEmpty = view.findViewById(R.id.tv_directory_empty)
        tvSubtitle = view.findViewById(R.id.tv_directory_subtitle)
        recycler = view.findViewById(R.id.rv_room_directory)

        adapter = RoomDirectoryAdapter { item ->
            showRoomDetailsDialog(item)
        }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        observeRooms()
    }

    private fun observeRooms() {
        val currentUser = AuthRepository.getInstance().currentUser.value
        val adminUid = when (currentUser?.roleEnum) {
            Role.DORM_ADMIN -> currentUser.uid
            Role.RESIDENT, Role.MAINTENANCE_STAFF -> currentUser.adminUid
            else -> null
        }
        repository.getRoomsWithOccupancy(adminUid).observe(viewLifecycleOwner) { list ->
            if (!isAdded) return@observe
            progress.visibility = View.GONE

            if (list.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recycler.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                adapter.submitList(list)

                var avail = 0
                for (r in list) {
                    if (!r.isFull()) avail++
                }
                tvSubtitle.text = String.format(Locale.US, "%d of %d rooms available", avail, list.size)
            }
        }
    }

    private fun showRoomDetailsDialog(item: RoomWithOccupants) {
        val room = item.room ?: return
        val roomNum = if (!room.roomNumber.isNullOrBlank()) room.roomNumber else "Unassigned"
        val statusText = item.getStatusText()
        val title = "Room $roomNum — $statusText"

        val msg = StringBuilder()
        msg.append("Monthly Rent: ").append(String.format(Locale.US, "₱%,.2f / month", room.monthlyRent)).append("\n")
        msg.append("Capacity: ").append(item.occupantCount).append(" of ").append(room.capacity).append(" occupants\n\n")

        val occupants = item.occupantNames
        msg.append("Occupants:\n")
        if (!occupants.isNullOrEmpty()) {
            for (name in occupants) {
                msg.append("• ").append(name).append("\n")
            }
        } else {
            msg.append("No occupants currently assigned to this room.")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(msg.toString().trim())
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
