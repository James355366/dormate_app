package com.dormmate.bills

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.dormmate.R
import com.dormmate.auth.AuthViewModel
import com.dormmate.databinding.FragmentBillsBinding
import com.dormmate.model.Bill
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.BillsRepository
import com.dormmate.util.AvatarUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class BillsFragment : Fragment(), BillsAdapter.OnBillActionListener {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BillsAdapter
    private lateinit var repo: BillsRepository
    private var currentUser: User? = null

    private var filterMonth = -1
    private var filterYear = -1
    private var setupDone = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = BillsRepository.getInstance()
        binding.layoutEmptyBills.visibility = View.VISIBLE
        binding.tvEmptyBills.text = "Loading bills…"
        binding.ivEmptyBills.setImageResource(R.drawable.ic_receipt)
        repo.loadError.observe(viewLifecycleOwner) { error ->
            if (_binding != null && error != null && adapter.itemCount == 0) {
                binding.layoutEmptyBills.visibility = View.VISIBLE
                binding.tvEmptyBills.text = error
                binding.ivEmptyBills.setImageResource(R.drawable.ic_warning)
            }
        }

        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            bindTopHeader(user)
            if (setupDone) return@observe
            setupDone = true
            currentUser = user
            setupRecyclerView()
            applyRoleLayout(user.roleEnum)
            setupSwipeRefresh()
            observeBills()
        }
    }

    private fun bindTopHeader(user: User) {
        val rawName = if (!user.name.isNullOrBlank()) user.name!! else (user.email ?: "")
        val name = AvatarUtils.formatDisplayName(rawName)
        binding.tvTopUserName.text = name
        AvatarUtils.bindAvatar(user, binding.tvTopAvatar, binding.ivTopAvatar)
        AvatarUtils.bindRoleBadge(user, binding.tvTopRoleBadge)
        binding.cardTopAvatar.setOnClickListener { navigateToTab(R.id.nav_profile) }
        binding.tvTopAvatar.setOnClickListener { navigateToTab(R.id.nav_profile) }
        binding.tvTopUserName.setOnClickListener { navigateToTab(R.id.nav_profile) }
    }

    private fun navigateToTab(itemId: Int) {
        if (!isAdded) return
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.selectedItemId = itemId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.stopListening()
        setupDone = false
        _binding = null
    }

    private fun setupRecyclerView() {
        val user = currentUser ?: return
        adapter = BillsAdapter(user.roleEnum, this)
        binding.rvBills.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBills.adapter = adapter
    }

    private fun applyRoleLayout(role: Role) {
        val isAdmin = role == Role.DORM_ADMIN || role == Role.SUPER_ADMIN

        if (isAdmin) {
            binding.btnFilterMonth.visibility = View.VISIBLE
            binding.btnFilterMonth.setOnClickListener { showMonthYearPicker() }
            binding.btnFilterMonth.setOnLongClickListener {
                clearMonthFilter()
                true
            }
            binding.fabGenerateBill.visibility = View.VISIBLE
            binding.fabGenerateBill.setOnClickListener {
                GenerateBillBottomSheet().show(childFragmentManager, "generate_bill")
            }
        }

        binding.bannerUnpaid.visibility = View.GONE
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshBills.setOnRefreshListener {
            observeBills()
            binding.swipeRefreshBills.isRefreshing = false
        }
    }

    private fun showMonthYearPicker() {
        val c = Calendar.getInstance()
        val y = if (filterYear > 0) filterYear else c.get(Calendar.YEAR)
        val m = if (filterMonth > 0) filterMonth - 1 else c.get(Calendar.MONTH)

        DatePickerDialog(requireContext(), { _, year, month, _ ->
            filterMonth = month + 1
            filterYear = year
            binding.btnFilterMonth.text = "${getMonthName(filterMonth)} $year"
            observeBills()
        }, y, m, 1).show()
    }

    private fun clearMonthFilter() {
        filterMonth = -1
        filterYear = -1
        binding.btnFilterMonth.setText(R.string.label_all_months)
        observeBills()
        Toast.makeText(requireContext(), R.string.msg_filter_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun observeBills() {
        val user = currentUser ?: return
        val role = user.roleEnum

        when (role) {
            Role.RESIDENT -> {
                repo.getBillsForResident(user.uid ?: "")
                    .observe(viewLifecycleOwner, this::onBillsLoaded)
            }
            Role.DORM_ADMIN -> {
                if (filterMonth > 0 && filterYear > 0) {
                    repo.getBillsByMonthForAdmin(filterMonth, filterYear, user.uid ?: "")
                        .observe(viewLifecycleOwner, this::onBillsLoaded)
                } else {
                    repo.getBillsForAdmin(user.uid ?: "")
                        .observe(viewLifecycleOwner, this::onBillsLoaded)
                }
            }
            else -> {
                if (filterMonth > 0 && filterYear > 0) {
                    repo.getBillsByMonth(filterMonth, filterYear)
                        .observe(viewLifecycleOwner, this::onBillsLoaded)
                } else {
                    repo.getAllBills()
                        .observe(viewLifecycleOwner, this::onBillsLoaded)
                }
            }
        }
    }

    private fun onBillsLoaded(bills: List<Bill>?) {
        if (_binding == null) return
        val list = bills ?: emptyList()

        adapter.submitList(ArrayList(list))

        val empty = list.isEmpty()
        val caughtUp = currentUser?.roleEnum == Role.RESIDENT && list.none { !it.isPaid }

        if (!empty) {
            repo.loadError.value = null
            binding.layoutEmptyBills.visibility = View.GONE
            binding.rvBills.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyBills.visibility = View.VISIBLE
            binding.rvBills.visibility = View.GONE
            binding.ivEmptyBills.setImageResource(if (caughtUp) R.drawable.ic_check_circle else R.drawable.ic_receipt)
            if (currentUser != null) {
                binding.tvEmptyBills.setText(
                    if (currentUser!!.roleEnum == Role.RESIDENT) R.string.empty_bills_resident else R.string.empty_bills_admin
                )
            }

            if (caughtUp) {
                binding.layoutEmptyBills.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.availability_green_container)
                )
                binding.cardEmptyBillsIcon.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.availability_green)
                )
                binding.ivEmptyBills.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_tertiary)
                )
                binding.tvEmptyBills.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_tertiary_container)
                )
            } else {
                binding.layoutEmptyBills.backgroundTintList = null
                binding.cardEmptyBillsIcon.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_secondary_container)
                )
                binding.ivEmptyBills.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_secondary_container)
                )
                binding.tvEmptyBills.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_surface_variant)
                )
            }
        }

        if (currentUser != null && currentUser!!.roleEnum == Role.RESIDENT) {
            var unpaidCount = 0
            var unpaidTotal = 0.0
            for (b in list) {
                if (!b.isPaid) {
                    unpaidCount++
                    unpaidTotal += b.getEffectiveAmount()
                }
            }
            if (unpaidCount > 0) {
                binding.bannerUnpaid.visibility = View.VISIBLE
                val pluralSuffix = if (unpaidCount > 1) "s" else ""
                binding.tvUnpaidSummary.text =
                    "$unpaidCount unpaid bill$pluralSuffix — ${PHP.format(unpaidTotal)} due"
            } else {
                binding.bannerUnpaid.visibility = View.GONE
            }
        }
    }

    override fun onConfirmPaymentClicked(bill: Bill) {
        val user = currentUser
        if (user != null && (user.roleEnum == Role.DORM_ADMIN || user.roleEnum == Role.SUPER_ADMIN)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Mark Bill as Paid?")
                .setMessage("Set bill status to Paid for ${bill.residentName} (${bill.roomNumber})?")
                .setPositiveButton("Mark as Paid") { _, _ -> markBillAsPaid(bill) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            markBillAsPaid(bill)
        }
    }

    override fun onPayGcashClicked(bill: Bill) {
        PayGcashBottomSheet.newInstance(bill).show(childFragmentManager, "pay_gcash")
    }

    override fun onVerifyGcashClicked(bill: Bill) {
        val refNum = if (!bill.gcashRefNumber.isNullOrBlank()) bill.gcashRefNumber!! else "Receipt Photo Uploaded"
        val message = "Resident: ${bill.residentName} (${bill.roomNumber})\nGCash Ref #: $refNum\nAmount: ${PHP.format(bill.getEffectiveAmount())}"
        val url = bill.gcashReceiptUrl

        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verify GCash Payment")
            .setMessage(message)

        if (!url.isNullOrBlank()) {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * resources.displayMetrics.density).toInt()
                setPadding(p, 0, p, 0)
            }
            val preview = ImageView(requireContext()).apply {
                adjustViewBounds = true
                maxHeight = (350 * resources.displayMetrics.density).toInt()
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            Glide.with(this).load(url).into(preview)
            layout.addView(preview)
            dialogBuilder.setView(layout)
        }

        val isStaffOrAdmin = currentUser?.roleEnum == Role.DORM_ADMIN || currentUser?.roleEnum == Role.SUPER_ADMIN
        if (isStaffOrAdmin) {
            dialogBuilder.setPositiveButton("Approve & Mark Paid") { _, _ ->
                repo.verifyGcashPayment(bill.billId ?: "", true, object : BillsRepository.Callback {
                    override fun onSuccess(id: String) {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "GCash payment verified!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(error: String) {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            .setNegativeButton("Reject") { _, _ ->
                repo.verifyGcashPayment(bill.billId ?: "", false, object : BillsRepository.Callback {
                    override fun onSuccess(id: String) {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Payment rejected", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(error: String) {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        } else {
            dialogBuilder.setPositiveButton("Close", null)
        }

        dialogBuilder.show()
    }

    private fun markBillAsPaid(bill: Bill) {
        val id = bill.billId ?: return
        repo.confirmPayment(id, object : BillsRepository.Callback {
            override fun onSuccess(id: String) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Bill marked as Paid!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(error: String) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun getMonthName(month: Int): String {
        val names = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        return if (month in 1..12) names[month - 1] else "?"
    }

    private fun getInitial(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().substring(0, 1).uppercase(Locale.ROOT)
    }

    companion object {
        private val PHP = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    }
}
