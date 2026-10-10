package com.dormmate

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dormmate.admin.ManageUsersFragment
import com.dormmate.announcements.AnnouncementsFragment
import com.dormmate.auth.AuthViewModel
import com.dormmate.auth.LoginActivity
import com.dormmate.bills.BillsFragment
import com.dormmate.databinding.ActivityMainBinding
import com.dormmate.home.HomeFragment
import com.dormmate.maintenance.MaintenanceFragment
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authViewModel: AuthViewModel
    var currentUser: User? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.fragmentContainer.setPadding(0, 0, 0, binding.bottomNav.height)
        }
        observeUser()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun observeUser() {
        authViewModel.currentUser.observe(this) { user ->
            if (user == null) return@observe
            currentUser = user
            applyRoleVisibility(user.roleEnum)
        }
        if (authViewModel.isLoggedIn()) {
            authViewModel.fetchCurrentUser()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_maintenance -> {
                    loadFragment(MaintenanceFragment())
                    true
                }
                R.id.nav_bills -> {
                    loadFragment(BillsFragment())
                    true
                }
                R.id.nav_announcements -> {
                    loadFragment(AnnouncementsFragment())
                    true
                }
                R.id.nav_manage_users -> {
                    loadFragment(ManageUsersFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun applyRoleVisibility(role: Role) {
        val menu = binding.bottomNav.menu
        val isSuperAdmin = role == Role.SUPER_ADMIN

        var maintenanceItem = menu.findItem(R.id.nav_maintenance)
        val manageUsersItem = menu.findItem(R.id.nav_manage_users)

        if (isSuperAdmin && manageUsersItem == null) {
            val order = maintenanceItem?.order ?: 1
            if (maintenanceItem != null) menu.removeItem(R.id.nav_maintenance)
            menu.add(Menu.NONE, R.id.nav_manage_users, order, R.string.tab_manage_users)
                .setIcon(R.drawable.ic_group)
        } else if (!isSuperAdmin && manageUsersItem != null) {
            val order = manageUsersItem.order
            menu.removeItem(R.id.nav_manage_users)
            menu.add(Menu.NONE, R.id.nav_maintenance, order, R.string.tab_maintenance)
                .setIcon(R.drawable.ic_build)
        }

        maintenanceItem = menu.findItem(R.id.nav_maintenance)
        val billsItem = menu.findItem(R.id.nav_bills)
        val announcementsItem = menu.findItem(R.id.nav_announcements)

        maintenanceItem?.setTitle(
            if (role == Role.MAINTENANCE_STAFF) R.string.tab_my_queue else R.string.tab_maintenance
        )

        billsItem?.isVisible = !isSuperAdmin && role != Role.MAINTENANCE_STAFF
        announcementsItem?.isVisible = !isSuperAdmin
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun signOut() {
        authViewModel.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
