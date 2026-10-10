package com.dormmate.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dormmate.R
import com.dormmate.admin.ManageResidentsBottomSheet
import com.dormmate.admin.ManageUsersBottomSheet
import com.dormmate.databinding.FragmentHomeBinding
import com.dormmate.maintenance.SubmitReportBottomSheet
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.util.AvatarUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        val cached = viewModel.currentUser.value
        if (cached != null) viewModel.refresh(cached)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupClickListeners() {
        binding.cardTopAvatar.setOnClickListener { navigateToTab(R.id.nav_profile) }
        binding.tvTopAvatar.setOnClickListener { navigateToTab(R.id.nav_profile) }

        binding.cardTotalResidents.setOnClickListener {
            val user = viewModel.currentUser.value
            if (user != null) {
                if (user.roleEnum == Role.DORM_ADMIN) {
                    ManageResidentsBottomSheet()
                        .show(parentFragmentManager, "manage_residents")
                } else if (user.roleEnum == Role.SUPER_ADMIN) {
                    navigateToTab(R.id.nav_manage_users)
                }
            }
        }

        binding.cardPendingApproval.setOnClickListener {
            val user = viewModel.currentUser.value
            if (user != null) {
                if (user.roleEnum.canManageResidents()) {
                    PendingApprovalsBottomSheet()
                        .show(parentFragmentManager, "pending_approvals")
                } else if (user.roleEnum == Role.MAINTENANCE_STAFF) {
                    navigateToTab(R.id.nav_maintenance)
                }
            }
        }

        binding.cardUnpaidBills.setOnClickListener { navigateToTab(R.id.nav_bills) }
        binding.cardMoneyCollected.setOnClickListener { navigateToTab(R.id.nav_bills) }
        binding.cardOpenTickets.setOnClickListener {
            val user = viewModel.currentUser.value
            if (user != null && user.roleEnum == Role.MAINTENANCE_STAFF) {
                navigateToTab(R.id.nav_announcements)
            } else {
                navigateToTab(R.id.nav_maintenance)
            }
        }
        binding.cardStaffEditProfile.setOnClickListener { navigateToTab(R.id.nav_profile) }

        parentFragmentManager.setFragmentResultListener(
            PendingApprovalsBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            val user = viewModel.currentUser.value
            if (user != null) viewModel.refresh(user)
        }

        binding.cardAnnouncement.setOnClickListener { navigateToTab(R.id.nav_announcements) }
        binding.cardTrackBills.setOnClickListener { navigateToTab(R.id.nav_bills) }
        binding.cardBrowseRoomsHome.setOnClickListener {
            RoomDirectoryBottomSheet().show(parentFragmentManager, "room_directory")
        }

        binding.fabQuickAction.setOnClickListener {
            val user = viewModel.currentUser.value
            if (user != null) {
                when (user.roleEnum) {
                    Role.SUPER_ADMIN -> ManageUsersBottomSheet().show(parentFragmentManager, "manage_users")
                    Role.MAINTENANCE_STAFF -> navigateToTab(R.id.nav_maintenance)
                    else -> SubmitReportBottomSheet().show(childFragmentManager, "submit_report")
                }
            }
        }
    }

    private fun navigateToTab(itemId: Int) {
        if (!isAdded) return
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.selectedItemId = itemId
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(viewLifecycleOwner, this::bindUser)

        viewModel.totalResidentsCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                binding.tvTotalResidentsCount.text = count.toString()
            }
        }

        viewModel.pendingApprovalCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                val user = viewModel.currentUser.value
                if (user != null && user.roleEnum != Role.MAINTENANCE_STAFF) {
                    binding.tvApprovalCount.text = count.toString()
                }
            }
        }

        viewModel.assignedTicketCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                val user = viewModel.currentUser.value
                if (user != null && user.roleEnum == Role.MAINTENANCE_STAFF) {
                    binding.tvApprovalCount.text = count.toString()
                }
            }
        }

        viewModel.unpaidBillCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                binding.tvBillCount.text = count.toString()
                binding.tvBillBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }

        viewModel.openTicketCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                val user = viewModel.currentUser.value
                if (user == null || user.roleEnum != Role.MAINTENANCE_STAFF) {
                    binding.tvOpenTicketCount.text = count.toString()
                }
            }
        }

        viewModel.totalAnnouncementsCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null && count != null) {
                val user = viewModel.currentUser.value
                if (user != null && user.roleEnum == Role.MAINTENANCE_STAFF) {
                    binding.tvOpenTicketCount.visibility = View.VISIBLE
                    binding.tvOpenTicketCount.text = count.toString()
                }
            }
        }

        viewModel.moneyCollectedThisMonth.observe(viewLifecycleOwner) { amount ->
            if (_binding != null) {
                val valAmount = amount ?: 0.0
                binding.tvMoneyCollectedAmount.text = String.format(Locale.US, "₱%,.2f", valAmount)
            }
        }

        viewModel.urgentBannerState.observe(viewLifecycleOwner) { banner ->
            if (_binding == null) return@observe
            if (banner != null && banner.type != HomeViewModel.UrgentBannerState.Type.NONE) {
                binding.tvUrgentMessage.text = banner.message
                binding.tvUrgentAction.text = banner.actionText
                binding.cardUrgentBanner.visibility = View.VISIBLE

                binding.cardUrgentBanner.setOnClickListener {
                    if (banner.type == HomeViewModel.UrgentBannerState.Type.OVERDUE_BILL) {
                        navigateToTab(R.id.nav_bills)
                    } else if (banner.type == HomeViewModel.UrgentBannerState.Type.OPEN_TICKETS) {
                        navigateToTab(R.id.nav_maintenance)
                    }
                }
            } else {
                binding.cardUrgentBanner.visibility = View.GONE
            }
        }

        viewModel.latestAnnouncement.observe(viewLifecycleOwner) { title ->
            if (_binding == null) return@observe
            if (!title.isNullOrEmpty()) {
                binding.tvAnnouncementTitle.text = title
            } else {
                binding.tvAnnouncementTitle.setText(R.string.placeholder_no_announcements)
            }
            binding.cardAnnouncement.visibility = View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (_binding != null && isLoading != null) {
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh(viewModel.currentUser.value) }
    }

    private fun bindUser(user: User?) {
        if (_binding == null) return
        viewModel.refresh(user)
        if (user == null) return

        val rawName = if (!user.name.isNullOrBlank()) user.name!! else (user.email ?: "")
        val name = AvatarUtils.formatDisplayName(rawName)
        val firstName = extractFirstName(name)

        binding.tvGreeting.text = "Hi, $firstName"

        AvatarUtils.bindAvatar(user, binding.tvTopAvatar, binding.ivTopAvatar)
        AvatarUtils.bindRoleBadge(user, binding.tvTopRoleBadge)

        val room = user.roomNumber
        if (!room.isNullOrBlank()) {
            binding.tvRoomNumber.text = getString(R.string.label_room, room)
            binding.tvRoomNumber.visibility = View.VISIBLE
        } else {
            binding.tvRoomNumber.visibility = View.GONE
        }

        applyRoleLayout(user.roleEnum)
    }

    private fun applyRoleLayout(role: Role) {
        if (_binding == null || context == null) return

        styleStatCard(
            binding.cardOpenTickets, binding.ivOpenTicketsIcon,
            binding.tvOpenTicketCount, binding.tvOpenTicketLabel,
            R.color.md_theme_primary_container, R.color.md_theme_on_primary_container
        )

        styleStatCard(
            binding.cardUnpaidBills, binding.ivUnpaidBillsIcon,
            binding.tvBillCount, binding.tvBillCardLabel,
            R.color.md_theme_tertiary_container, R.color.md_theme_on_tertiary_container
        )

        styleStatCard(
            binding.cardPendingApproval, binding.ivPendingApprovalIcon,
            binding.tvApprovalCount, binding.tvApprovalCardLabel,
            R.color.md_theme_secondary_container, R.color.md_theme_on_secondary_container
        )

        styleStatCard(
            binding.cardTotalResidents, binding.ivTotalResidentsIcon,
            binding.tvTotalResidentsCount, binding.tvTotalResidentsLabel,
            R.color.md_theme_primary_container, R.color.md_theme_on_primary_container
        )

        if (role == Role.MAINTENANCE_STAFF) {
            if (binding.cardOpenTickets.parent == binding.rowSummary2) {
                binding.rowSummary2.removeView(binding.cardOpenTickets)
                binding.rowSummary1.addView(binding.cardOpenTickets)
            }
        } else {
            if (binding.cardOpenTickets.parent == binding.rowSummary1) {
                binding.rowSummary1.removeView(binding.cardOpenTickets)
                binding.rowSummary2.addView(binding.cardOpenTickets, 0)
            }
        }

        val paramsTotal = binding.cardTotalResidents.layoutParams as LinearLayout.LayoutParams

        when (role) {
            Role.SUPER_ADMIN -> {
                binding.rowSummary1.visibility = View.VISIBLE
                binding.rowSummary2.visibility = View.GONE
                binding.cardPendingApproval.visibility = View.GONE
                binding.cardUnpaidBills.visibility = View.GONE
                binding.cardOpenTickets.visibility = View.GONE
                binding.cardMoneyCollected.visibility = View.GONE
                binding.cardTotalResidents.visibility = View.VISIBLE
                binding.tvTotalResidentsLabel.setText(R.string.label_total_admins)
                binding.ivTotalResidentsIcon.setImageResource(R.drawable.ic_group)
                paramsTotal.weight = 2f
                paramsTotal.rightMargin = 0
                binding.fabQuickAction.text = "Manage Admins & Staffs"
                binding.fabQuickAction.setIconResource(R.drawable.ic_group)
            }

            Role.MAINTENANCE_STAFF -> {
                binding.rowSummary1.visibility = View.VISIBLE
                binding.rowSummary2.visibility = View.GONE
                binding.cardTotalResidents.visibility = View.GONE
                binding.cardUnpaidBills.visibility = View.GONE
                binding.cardMoneyCollected.visibility = View.GONE

                binding.cardPendingApproval.visibility = View.VISIBLE
                binding.cardOpenTickets.visibility = View.VISIBLE

                val margin = (8 * resources.displayMetrics.density).toInt()

                val lp1 = binding.cardPendingApproval.layoutParams as LinearLayout.LayoutParams
                lp1.weight = 1f
                lp1.leftMargin = 0
                lp1.rightMargin = margin
                binding.cardPendingApproval.layoutParams = lp1

                val lp2 = binding.cardOpenTickets.layoutParams as LinearLayout.LayoutParams
                lp2.weight = 1f
                lp2.leftMargin = margin
                lp2.rightMargin = 0
                binding.cardOpenTickets.layoutParams = lp2

                binding.tvApprovalCardLabel.setText(R.string.label_assigned_tickets)
                binding.ivPendingApprovalIcon.setImageResource(R.drawable.ic_build)

                binding.cardOpenTickets.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_primary_container)
                )
                binding.ivOpenTicketsIcon.setImageResource(R.drawable.ic_announcement)
                binding.ivOpenTicketsIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )
                binding.tvOpenTicketCount.visibility = View.VISIBLE
                binding.tvOpenTicketCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                binding.tvOpenTicketCount.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )
                binding.tvOpenTicketLabel.text = "Announcements"
                binding.tvOpenTicketLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                binding.tvOpenTicketLabel.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )

                binding.fabQuickAction.text = "View Queue"
                binding.fabQuickAction.setIconResource(R.drawable.ic_build)
            }

            Role.DORM_ADMIN -> {
                binding.rowSummary1.visibility = View.VISIBLE
                binding.rowSummary2.visibility = View.VISIBLE
                binding.cardTotalResidents.visibility = View.VISIBLE
                binding.cardPendingApproval.visibility = View.VISIBLE
                binding.cardUnpaidBills.visibility = View.VISIBLE
                binding.cardMoneyCollected.visibility = View.VISIBLE
                binding.cardOpenTickets.visibility = View.VISIBLE

                binding.tvBillCardLabel.setText(R.string.label_pending_bills)
                binding.tvApprovalCardLabel.setText(R.string.label_pending_approval)
                binding.ivPendingApprovalIcon.setImageResource(R.drawable.ic_pending_approval)
                binding.tvOpenTicketLabel.setText(R.string.label_open_tickets)
                binding.tvOpenTicketCount.visibility = View.VISIBLE
                binding.tvOpenTicketCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                binding.cardOpenTickets.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_primary_container)
                )
                binding.ivOpenTicketsIcon.setImageResource(R.drawable.ic_build)
                binding.ivOpenTicketsIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )
                binding.tvOpenTicketCount.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )
                binding.tvOpenTicketLabel.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary_container)
                )

                paramsTotal.weight = 1f
                paramsTotal.rightMargin = (8 * resources.displayMetrics.density).toInt()
                binding.fabQuickAction.setIconResource(R.drawable.ic_add)
            }

            Role.RESIDENT -> {
                binding.rowSummary1.visibility = View.GONE
                binding.rowSummary2.visibility = View.VISIBLE
                binding.cardTotalResidents.visibility = View.GONE
                binding.cardPendingApproval.visibility = View.GONE
                binding.cardUnpaidBills.visibility = View.VISIBLE
                binding.cardMoneyCollected.visibility = View.GONE
                binding.cardOpenTickets.visibility = View.VISIBLE

                binding.tvBillCardLabel.setText(R.string.label_unpaid_bills)
                binding.tvOpenTicketLabel.setText(R.string.label_open_tickets)
                binding.tvOpenTicketCount.visibility = View.VISIBLE

                binding.fabQuickAction.setIconResource(R.drawable.ic_add)
            }
        }
        binding.layoutResidentShortcuts.visibility = if (role == Role.RESIDENT) View.VISIBLE else View.GONE
        binding.cardStaffEditProfile.visibility = if (role == Role.MAINTENANCE_STAFF) View.VISIBLE else View.GONE
        binding.cardTotalResidents.layoutParams = paramsTotal
    }

    private fun styleStatCard(
        card: MaterialCardView,
        icon: ImageView?,
        countText: TextView?,
        labelText: TextView?,
        bgColorRes: Int,
        fgColorRes: Int
    ) {
        if (context == null) return
        val bgColor = ContextCompat.getColor(requireContext(), bgColorRes)
        val fgColor = ContextCompat.getColor(requireContext(), fgColorRes)

        card.setCardBackgroundColor(bgColor)
        icon?.imageTintList = ColorStateList.valueOf(fgColor)
        countText?.setTextColor(fgColor)
        labelText?.setTextColor(fgColor)
    }

    private fun extractFirstName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        var trimmed = name.trim()
        if (trimmed.contains(" ")) {
            trimmed = trimmed.split(" ")[0]
        }
        return if (trimmed.isNotEmpty()) {
            trimmed.substring(0, 1).uppercase(Locale.ROOT) + trimmed.substring(1)
        } else {
            trimmed
        }
    }

    private fun getInitial(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().substring(0, 1).uppercase(Locale.ROOT)
    }
}
