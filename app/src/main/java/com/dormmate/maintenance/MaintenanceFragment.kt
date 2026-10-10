package com.dormmate.maintenance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.dormmate.R
import com.dormmate.auth.AuthViewModel
import com.dormmate.databinding.FragmentMaintenanceBinding
import com.dormmate.model.MaintenanceReport
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.MaintenanceRepository
import com.dormmate.util.AvatarUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Locale

class MaintenanceFragment : Fragment(), MaintenanceAdapter.OnItemClickListener {

    private var _binding: FragmentMaintenanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MaintenanceAdapter
    private lateinit var repo: MaintenanceRepository
    private var currentUser: User? = null

    private var fullList: List<MaintenanceReport> = emptyList()
    private var activeFilter: MaintenanceReport.Status? = null
    private var setupDone = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaintenanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = MaintenanceRepository.getInstance()

        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            bindTopHeader(user)
            if (setupDone) return@observe
            setupDone = true
            currentUser = user
            setupRecyclerView()
            applyRoleLayout(user.roleEnum)
            setupFilterChips()
            setupSwipeRefresh()
            observeReports(user)
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

    private fun getInitial(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().substring(0, 1).uppercase(Locale.ROOT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.stopListening()
        setupDone = false
        _binding = null
    }

    private fun setupRecyclerView() {
        val user = currentUser ?: return
        adapter = MaintenanceAdapter(user.roleEnum, this)
        binding.rvMaintenance.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaintenance.adapter = adapter
    }

    private fun applyRoleLayout(role: Role) {
        val isAdmin = role == Role.DORM_ADMIN || role == Role.SUPER_ADMIN
        val isStaff = role == Role.MAINTENANCE_STAFF
        val showsFilters = isAdmin || isStaff

        if (isStaff) {
            binding.tvMaintenanceTitle.setText(R.string.tab_my_queue)
            binding.tvMaintenancePillTitle.text = "Assigned Work Queue"
            binding.tvMaintenancePillSub.text = "Manage & update repair tasks"
        } else if (isAdmin) {
            binding.tvMaintenanceTitle.text = "Maintenance Admin"
            binding.tvMaintenancePillTitle.text = "All Maintenance Reports"
            binding.tvMaintenancePillSub.text = "Assign staff & manage reports"
        } else {
            binding.tvMaintenanceTitle.setText(R.string.tab_maintenance)
            binding.tvMaintenancePillTitle.text = "My Requests"
            binding.tvMaintenancePillSub.text = "Track & submit repair requests"
        }

        binding.tvReportCount.visibility = if (showsFilters) View.VISIBLE else View.GONE
        binding.scrollStatusFilter.visibility = if (showsFilters) View.VISIBLE else View.GONE

        if (role == Role.RESIDENT) {
            binding.fabSubmitReport.visibility = View.VISIBLE
            binding.fabSubmitReport.setOnClickListener {
                SubmitReportBottomSheet().show(childFragmentManager, "submit_report")
            }
        } else {
            binding.fabSubmitReport.visibility = View.GONE
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            activeFilter = null
            applyFilter()
        }
        binding.chipPending.setOnClickListener {
            activeFilter = MaintenanceReport.Status.PENDING
            applyFilter()
        }
        binding.chipInProgress.setOnClickListener {
            activeFilter = MaintenanceReport.Status.IN_PROGRESS
            applyFilter()
        }
        binding.chipResolved.setOnClickListener {
            activeFilter = MaintenanceReport.Status.RESOLVED
            applyFilter()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshMaintenance.setOnRefreshListener {
            binding.swipeRefreshMaintenance.isRefreshing = false
        }
    }

    private fun observeReports(user: User) {
        val role = user.roleEnum

        when (role) {
            Role.RESIDENT -> {
                repo.getReportsForResident(user.uid ?: "")
                    .observe(viewLifecycleOwner, this::onReportsLoaded)
            }
            Role.MAINTENANCE_STAFF -> {
                repo.getReportsForStaff(user.uid ?: "")
                    .observe(viewLifecycleOwner, this::onReportsLoaded)
            }
            Role.DORM_ADMIN -> {
                repo.getReportsForAdmin(user.uid ?: "")
                    .observe(viewLifecycleOwner, this::onReportsLoaded)
            }
            else -> {
                repo.getAllReports()
                    .observe(viewLifecycleOwner, this::onReportsLoaded)
            }
        }
    }

    private fun onReportsLoaded(reports: List<MaintenanceReport>?) {
        val list = reports ?: emptyList()
        fullList = list

        if (binding.tvReportCount.visibility == View.VISIBLE) {
            binding.tvReportCount.text = list.size.toString()
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (activeFilter == null) {
            fullList
        } else {
            val res = mutableListOf<MaintenanceReport>()
            for (r in fullList) {
                if (r.statusEnum == activeFilter) res.add(r)
            }
            res
        }

        adapter.submitList(ArrayList(filtered))

        val empty = filtered.isEmpty()
        binding.layoutEmptyMaintenance.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvMaintenance.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty && currentUser != null) {
            when (currentUser!!.roleEnum) {
                Role.RESIDENT -> binding.tvEmptyMaintenance.setText(R.string.empty_maintenance_resident)
                Role.MAINTENANCE_STAFF -> binding.tvEmptyMaintenance.setText(R.string.empty_maintenance_staff)
                else -> binding.tvEmptyMaintenance.setText(
                    if (activeFilter == null) R.string.empty_maintenance_admin else R.string.empty_maintenance_filtered
                )
            }
        }
    }

    override fun onAssignStaffClicked(report: MaintenanceReport) {
        AssignStaffDialog.newInstance(report.reportId, report.assignedStaffUid)
            .show(childFragmentManager, "assign_staff")
    }

    override fun onCycleStatusClicked(report: MaintenanceReport) {
        repo.cycleStatus(report, object : MaintenanceRepository.Callback {
            override fun onSuccess(id: String) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Status updated", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(error: String) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
