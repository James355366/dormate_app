package com.dormmate.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Room
import com.dormmate.repository.RoomsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class RoomManagementBottomSheet : BottomSheetDialogFragment() {

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RoomsAdapter
    private val repository = RoomsRepository.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_room_management, container, false)
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

        progress = view.findViewById(R.id.progress_rooms)
        tvEmpty = view.findViewById(R.id.tv_rooms_empty)
        tvCount = view.findViewById(R.id.tv_room_count)
        recycler = view.findViewById(R.id.rv_rooms)

        adapter = RoomsAdapter(object : RoomsAdapter.OnRoomActionListener {
            override fun onEdit(room: Room) {
                showRoomEditorDialog(room)
            }

            override fun onDelete(room: Room) {
                confirmDeleteRoom(room)
            }
        })

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btn_add_room).setOnClickListener { showRoomEditorDialog(null) }

        observeRooms()
    }

    private fun observeRooms() {
        val myUid = FirebaseAuth.getInstance().uid
        repository.getRooms(myUid).observe(viewLifecycleOwner) { rooms ->
            if (!isAdded) return@observe
            progress.visibility = View.GONE

            if (rooms.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recycler.visibility = View.GONE
                tvCount.setText(R.string.room_management_subtitle)
            } else {
                tvEmpty.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                adapter.submitList(rooms)
                tvCount.text = String.format(Locale.US, "%d predefined rooms available", rooms.size)
            }
        }
    }

    private fun showRoomEditorDialog(room: Room?) {
        val isEditing = room != null

        val form = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_room_editor, null, false)

        val tilNumber = form.findViewById<TextInputLayout>(R.id.til_room_number_input)
        val tilPrice = form.findViewById<TextInputLayout>(R.id.til_room_price_input)
        val etNumber = form.findViewById<EditText>(R.id.et_room_number_input)
        val etPrice = form.findViewById<EditText>(R.id.et_room_price_input)
        val etCap = form.findViewById<EditText>(R.id.et_room_capacity_input)

        if (isEditing) {
            etNumber.setText(room.roomNumber)
            etPrice.setText(String.format(Locale.US, "%.2f", room.monthlyRent))
            if (room.capacity > 0) {
                etCap.setText(room.capacity.toString())
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEditing) R.string.action_edit_room else R.string.action_add_room)
            .setView(form)
            .setPositiveButton(R.string.manage_users_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val numberStr = etNumber.text.toString().trim()
                val priceStr = etPrice.text.toString().trim()
                val capStr = etCap.text.toString().trim()

                tilNumber.error = null
                tilPrice.error = null

                if (numberStr.isEmpty()) {
                    tilNumber.error = getString(R.string.error_room_number_required)
                    return@setOnClickListener
                }

                val price = try {
                    priceStr.toDouble()
                } catch (e: Exception) {
                    tilPrice.error = getString(R.string.error_room_price_required)
                    return@setOnClickListener
                }

                var capacity = 0
                try {
                    if (capStr.isNotEmpty()) capacity = capStr.toInt()
                } catch (ignored: Exception) {}

                save.isEnabled = false

                if (isEditing) {
                    val roomId = room.roomId ?: return@setOnClickListener
                    repository.updateRoom(roomId, numberStr, price, capacity, object : RoomsRepository.Callback {
                        override fun onSuccess(roomId: String) {
                            if (isAdded) {
                                dialog.dismiss()
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.msg_room_updated, numberStr),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onFailure(error: String) {
                            if (isAdded) {
                                save.isEnabled = true
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                } else {
                    val myUid = FirebaseAuth.getInstance().uid
                    val newRoom = Room(numberStr, price, capacity, myUid)
                    repository.addRoom(newRoom, object : RoomsRepository.Callback {
                        override fun onSuccess(roomId: String) {
                            if (isAdded) {
                                dialog.dismiss()
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.msg_room_added, numberStr),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onFailure(error: String) {
                            if (isAdded) {
                                save.isEnabled = true
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                }
            }
        }

        dialog.show()
    }

    private fun confirmDeleteRoom(room: Room) {
        val roomId = room.roomId ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete_room)
            .setMessage(getString(R.string.msg_confirm_delete_room, room.roomNumber))
            .setPositiveButton(R.string.action_delete_room) { _, _ ->
                repository.deleteRoom(roomId, object : RoomsRepository.Callback {
                    override fun onSuccess(roomId: String) {
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.msg_room_deleted, room.roomNumber),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(error: String) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
