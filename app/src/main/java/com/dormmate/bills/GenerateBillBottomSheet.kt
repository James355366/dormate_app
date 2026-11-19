package com.dormmate.bills

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.dormmate.MainActivity
import com.dormmate.R
import com.dormmate.admin.UserDeleter
import com.dormmate.model.Bill
import com.dormmate.model.Role
import com.dormmate.model.Room
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.dormmate.repository.BillsRepository
import com.dormmate.repository.RoomsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GenerateBillBottomSheet : BottomSheetDialogFragment() {

    private lateinit var spinnerResident: Spinner
    private lateinit var etRoomRent: EditText
    private lateinit var etElectricity: EditText
    private lateinit var etWater: EditText
    private lateinit var etWifi: EditText
    private lateinit var tvDueDate: TextView
    private lateinit var tvAutoTotal: TextView
    private lateinit var btnPickDate: Button
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar

    private val residentList = mutableListOf<User>()
    private val rawAllResidents = mutableListOf<User>()
    private val validOccupantNames = mutableSetOf<String>()
    private val validOccupantUids = mutableSetOf<String>()
    private val cachedRooms = mutableListOf<Room>()

    private var selectedResident: User? = null
    private var selectedDueDate: Date? = null
    private var dbListener: ListenerRegistration? = null

    private var billMonth: Int = 0
    private var billYear: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_generate_bill, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerResident = view.findViewById(R.id.spinner_resident)
        etRoomRent = view.findViewById(R.id.et_room_rent)
        etElectricity = view.findViewById(R.id.et_electricity)
        etWater = view.findViewById(R.id.et_water)
        etWifi = view.findViewById(R.id.et_wifi)
        tvDueDate = view.findViewById(R.id.tv_due_date)
        tvAutoTotal = view.findViewById(R.id.tv_auto_total)
        btnPickDate = view.findViewById(R.id.btn_pick_due_date)
        btnGenerate = view.findViewById(R.id.btn_generate_bill)
        progressBar = view.findViewById(R.id.progress_generate)

        val now = Calendar.getInstance()
        billMonth = now.get(Calendar.MONTH) + 1
        billYear = now.get(Calendar.YEAR)

        RoomsRepository.getInstance().getRooms().observe(viewLifecycleOwner) { rooms ->
            if (rooms != null) {
                cachedRooms.clear()
                cachedRooms.addAll(rooms)
                autoFillRoomPrice()
            }
        }

        val currentAdminUid = FirebaseAuth.getInstance().uid
        RoomsRepository.getInstance().getRoomsWithOccupancy(currentAdminUid).observe(viewLifecycleOwner) { roomList ->
            if (roomList != null) {
                validOccupantNames.clear()
                validOccupantUids.clear()
                for (rwo in roomList) {
                    val names = rwo.occupantNames
                    if (!names.isNullOrEmpty()) {
                        for (n in names) {
                            if (n.isNotBlank()) validOccupantNames.add(n.trim().lowercase(Locale.ROOT))
                        }
                    }
                }
                updateResidentSpinner()
            }
        }

        loadResidents()
        setupDatePicker()
        setupAutoTotal()
        btnGenerate.setOnClickListener { validateAndGenerate() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dbListener?.remove()
        dbListener = null
    }

    private fun loadResidents() {
        val loadingPlaceholder = listOf("Loading residents...")
        val loadingAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, loadingPlaceholder
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerResident.adapter = loadingAdapter

        dbListener = FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "RESIDENT")
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null) return@addSnapshotListener
                rawAllResidents.clear()
                rawAllResidents.addAll(snap.toObjects(User::class.java))
                updateResidentSpinner()
            }
    }

    private fun updateResidentSpinner() {
        if (!isAdded) return
        residentList.clear()

        val myAdminUser = AuthRepository.getInstance().currentUser.value
        val isSuperAdmin = myAdminUser?.roleEnum == Role.SUPER_ADMIN

        for (u in rawAllResidents) {
            val uName = u.name?.trim()?.lowercase(Locale.ROOT) ?: ""
            val uUid = u.uid ?: ""

            if (u.isVerified && !u.roomNumber.isNullOrBlank()) {
                val matchesOccupant = validOccupantUids.contains(uUid) || (uName.isNotEmpty() && validOccupantNames.contains(uName))

                if (isSuperAdmin) {
                    residentList.add(u)
                } else if (matchesOccupant) {
                    residentList.add(u)
                }
            }
        }

        if (residentList.isEmpty()) {
            val emptyList = listOf("No verified residents found")
            val adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, emptyList
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerResident.adapter = adapter
            selectedResident = null
            return
        }

        val names = residentList.map { "${it.name} (Rm ${it.roomNumber})" }
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, names
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerResident.adapter = adapter
        spinnerResident.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos in residentList.indices) {
                    selectedResident = residentList[pos]
                    autoFillRoomPrice()
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {
                selectedResident = null
            }
        }
    }

    private fun autoFillRoomPrice() {
        val res = selectedResident ?: return
        val resRoom = res.roomNumber?.trim() ?: return
        for (r in cachedRooms) {
            val roomNum = r.roomNumber
            if (roomNum != null && resRoom.equals(roomNum.trim(), ignoreCase = true)) {
                etRoomRent.setText(String.format(Locale.US, "%.2f", r.monthlyRent))
                return
            }
        }
        if (etRoomRent.text.toString().isEmpty()) {
            etRoomRent.setText("2500")
        }
    }

    private fun setupDatePicker() {
        btnPickDate.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    c.set(y, m, d)
                    selectedDueDate = c.time
                    tvDueDate.text = "Due: " + DateFormat.format("MMM dd, yyyy", selectedDueDate)
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupAutoTotal() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                updateTotal()
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        etRoomRent.addTextChangedListener(watcher)
        etElectricity.addTextChangedListener(watcher)
        etWater.addTextChangedListener(watcher)
        etWifi.addTextChangedListener(watcher)
    }

    private fun updateTotal() {
        val total = parseDouble(etRoomRent) + parseDouble(etElectricity) +
                parseDouble(etWater) + parseDouble(etWifi)
        tvAutoTotal.text = String.format(Locale.US, "Total: ₱%.2f", total)
    }

    private fun parseDouble(et: EditText): Double {
        return try {
            et.text.toString().toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun validateAndGenerate() {
        val resident = selectedResident
        if (resident == null) {
            Toast.makeText(requireContext(), "Select a resident", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDueDate == null) {
            Toast.makeText(requireContext(), "Pick a due date", Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(etRoomRent.text)) {
            etRoomRent.error = "Required"
            return
        }

        setLoading(true)

        val bill = Bill(
            resident.uid,
            resident.name,
            resident.roomNumber,
            billMonth,
            billYear,
            parseDouble(etRoomRent),
            parseDouble(etElectricity),
            parseDouble(etWater),
            parseDouble(etWifi),
            selectedDueDate
        )

        val currentAdminUid = FirebaseAuth.getInstance().uid
        if (currentAdminUid != null) {
            bill.adminUid = currentAdminUid
        }

        BillsRepository.getInstance().generateBill(bill, object : BillsRepository.Callback {
            override fun onSuccess(id: String) {
                setLoading(false)
                Toast.makeText(requireContext(), "Bill generated!", Toast.LENGTH_SHORT).show()
                dismiss()
            }

            override fun onFailure(error: String) {
                setLoading(false)
                Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnGenerate.isEnabled = !loading
    }
}
