package com.dormmate.announcements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.dormmate.R
import com.dormmate.auth.AuthViewModel
import com.dormmate.databinding.DialogPostAnnouncementBinding
import com.dormmate.databinding.FragmentAnnouncementsBinding
import com.dormmate.model.Announcement
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.AnnouncementsRepository
import com.dormmate.util.AvatarUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Locale

class AnnouncementsFragment : Fragment() {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AnnouncementsAdapter
    private lateinit var repo: AnnouncementsRepository
    private var currentUser: User? = null
    private var setupDone = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = AnnouncementsRepository.getInstance()

        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            bindTopHeader(user)
            if (setupDone) return@observe
            setupDone = true
            currentUser = user
            setupRecyclerView()
            applyRoleLayout()
            setupSwipeRefresh()
            observeAnnouncements()
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
        adapter = AnnouncementsAdapter(currentUser?.uid.orEmpty())
        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAnnouncements.adapter = adapter
    }

    private fun applyRoleLayout() {
        val user = currentUser ?: return
        if (user.roleEnum.canPostAnnouncements()) {
            binding.fabPostAnnouncement.visibility = View.VISIBLE
            binding.fabPostAnnouncement.setOnClickListener { showPostDialog() }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAnnouncements.setOnRefreshListener {
            observeAnnouncements()
            binding.swipeRefreshAnnouncements.isRefreshing = false
        }
    }

    private fun observeAnnouncements() {
        val user = currentUser ?: return
        val role = user.roleEnum

        when (role) {
            Role.DORM_ADMIN -> {
                repo.getAnnouncementsForPoster(user.uid)
                    .observe(viewLifecycleOwner, this::onAnnouncementsLoaded)
            }
            Role.RESIDENT -> {
                if (user.adminUid != null) {
                    repo.getAnnouncementsForPoster(user.adminUid)
                        .observe(viewLifecycleOwner, this::onAnnouncementsLoaded)
                } else {
                    repo.getAllAnnouncements()
                        .observe(viewLifecycleOwner, this::onAnnouncementsLoaded)
                }
            }
            else -> {
                repo.getAllAnnouncements()
                    .observe(viewLifecycleOwner, this::onAnnouncementsLoaded)
            }
        }
    }

    private fun onAnnouncementsLoaded(announcements: List<Announcement>?) {
        if (_binding == null) return
        val list = announcements ?: emptyList()

        adapter.submitList(ArrayList(list))

        val empty = list.isEmpty()
        binding.layoutEmptyAnnouncements.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvAnnouncements.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showPostDialog() {
        val user = currentUser ?: return
        val dialogBinding = DialogPostAnnouncementBinding.inflate(layoutInflater)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.post_announcement)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_post) { _, _ ->
                val titleField = dialogBinding.etTitle
                val bodyField = dialogBinding.etBody

                val title = titleField.text?.toString()?.trim() ?: ""
                val body = bodyField.text?.toString()?.trim() ?: ""

                if (title.isEmpty() || body.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        R.string.error_announcement_fields_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                repo.postAnnouncement(title, body, user.name, user.uid, object : AnnouncementsRepository.Callback {
                    override fun onSuccess() {
                        if (_binding != null) {
                            Toast.makeText(
                                requireContext(),
                                R.string.msg_announcement_posted,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(error: String) {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
