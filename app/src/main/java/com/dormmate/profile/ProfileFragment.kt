package com.dormmate.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.dormmate.util.DormInfoActions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dormmate.MainActivity
import com.dormmate.R
import com.dormmate.admin.ManageUsersBottomSheet
import com.dormmate.admin.RoomManagementBottomSheet
import com.dormmate.auth.AuthViewModel
import com.dormmate.bills.GcashSettingsBottomSheet
import com.dormmate.databinding.FragmentProfileBinding
import com.dormmate.home.RoomDirectoryBottomSheet
import com.dormmate.model.Role
import com.dormmate.model.RoomWithOccupants
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.dormmate.repository.RoomsRepository
import com.dormmate.util.AvatarUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: User? = null
    private var roommatesListener: ListenerRegistration? = null
    private var loadedDormKey: String? = null
    private val currentRoomOccupancyList = mutableListOf<RoomWithOccupants>()

    private val profileImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                uploadProfilePicture(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            bindUser(user)
            if (user != null) {
                observeRoomsForUser(user)
            }
        }

        binding.btnViewRooms.setOnClickListener {
            RoomDirectoryBottomSheet().show(parentFragmentManager, "room_directory")
        }

        binding.cardMainProfileAvatar.setOnClickListener {
            profileImagePickerLauncher.launch("image/*")
        }
        binding.tvChangePhotoLabel.setOnClickListener {
            profileImagePickerLauncher.launch("image/*")
        }

        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnSendResetEmail.setOnClickListener { sendPasswordResetEmail() }
        binding.btnSignOut.setOnClickListener { confirmSignOut() }

        binding.btnManageRooms.setOnClickListener {
            RoomManagementBottomSheet().show(parentFragmentManager, "manage_rooms")
        }

        binding.btnGcashSettings.setOnClickListener {
            GcashSettingsBottomSheet().show(parentFragmentManager, "gcash_settings")
        }

        binding.btnManageUsers.setOnClickListener {
            ManageUsersBottomSheet().show(parentFragmentManager, "manage_users")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        roommatesListener?.remove()
        _binding = null
    }

    private fun bindUser(user: User?) {
        if (_binding == null || user == null) return

        currentUser = user

        val rawName = if (!user.name.isNullOrBlank()) user.name!! else (user.email ?: "")
        val name = AvatarUtils.formatDisplayName(rawName)
        binding.tvTopUserName.text = name
        AvatarUtils.bindAvatar(user, binding.tvTopAvatar, binding.ivTopAvatar)
        AvatarUtils.bindRoleBadge(user, binding.tvTopRoleBadge)
        AvatarUtils.bindAvatar(user, binding.tvMainProfileAvatar, binding.ivMainProfileAvatar)

        binding.tvProfileName.text = name
        binding.tvProfileEmail.text = user.email
        binding.tvProfileRole.text = displayRole(user.roleEnum)

        val isSuperAdmin = user.roleEnum == Role.SUPER_ADMIN
        val isDormAdmin = user.roleEnum == Role.DORM_ADMIN
        val isStaff = user.roleEnum == Role.MAINTENANCE_STAFF

        val showsViewRooms = !isSuperAdmin && !isStaff
        binding.btnViewRooms.visibility = if (showsViewRooms) View.VISIBLE else View.GONE
        binding.btnManageRooms.visibility = if (isDormAdmin) View.VISIBLE else View.GONE
        binding.btnGcashSettings.visibility = if (isDormAdmin) View.VISIBLE else View.GONE

        binding.btnManageUsers.visibility = if (isSuperAdmin) View.VISIBLE else View.GONE

        val showContactNumber = !isSuperAdmin
        binding.dividerPhone.visibility = if (showContactNumber) View.VISIBLE else View.GONE
        binding.tvPhoneLabel.visibility = if (showContactNumber) View.VISIBLE else View.GONE
        binding.tvProfilePhone.visibility = if (showContactNumber) View.VISIBLE else View.GONE
        if (showContactNumber) {
            val phone = user.phoneNumber
            binding.tvProfilePhone.text = if (!phone.isNullOrBlank()) phone else "Not set"
        }

        updateProfileRoomDisplay()
    }

    private fun normalizeRoom(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.trim().uppercase(Locale.ROOT).replace("^ROOM\\s*".toRegex(), "")
    }

    private fun uploadProfilePicture(uri: Uri) {
        val user = currentUser ?: return
        val uid = user.uid ?: return

        binding.progressProfilePhoto.visibility = View.VISIBLE
        binding.tvChangePhotoLabel.text = "Uploading photo..."

        val ref = FirebaseStorage.getInstance().getReference("profile_images/$uid")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        saveProfileImageUrl(uid, downloadUri.toString())
                    }
                    .addOnFailureListener {
                        saveCompressedBase64Image(uid, uri)
                    }
            }
            .addOnFailureListener {
                saveCompressedBase64Image(uid, uri)
            }
    }

    private fun saveProfileImageUrl(uid: String, url: String) {
        AuthRepository.getInstance().updateProfileImage(
            uid,
            url,
            object : AuthRepository.Callback {
                override fun onSuccess() {
                    if (isAdded) {
                        binding.progressProfilePhoto.visibility = View.GONE
                        binding.tvChangePhotoLabel.text = "Tap photo to change profile picture"
                        Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(error: String) {
                    if (isAdded) {
                        binding.progressProfilePhoto.visibility = View.GONE
                        binding.tvChangePhotoLabel.text = "Tap photo to change profile picture"
                        Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun saveCompressedBase64Image(uid: String, uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val maxDim = 300
                val scale = Math.min(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height)
                val width = (originalBitmap.width * Math.min(1f, scale)).toInt()
                val height = (originalBitmap.height * Math.min(1f, scale)).toInt()

                val resized = Bitmap.createScaledBitmap(originalBitmap, Math.max(1, width), Math.max(1, height), true)
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                val bytes = baos.toByteArray()
                val base64Str = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

                saveProfileImageUrl(uid, base64Str)
            } else {
                showUploadError("Could not process selected image.")
            }
        } catch (e: Exception) {
            showUploadError(e.message ?: "Failed to process image")
        }
    }

    private fun showUploadError(msg: String) {
        if (isAdded) {
            binding.progressProfilePhoto.visibility = View.GONE
            binding.tvChangePhotoLabel.text = "Tap photo to change profile picture"
            Toast.makeText(requireContext(), "Upload failed: $msg", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateProfileRoomDisplay() {
        val user = currentUser ?: return
        if (_binding == null) return
        val showsRoom = user.roleEnum == Role.RESIDENT
        binding.tvRoomLabel.visibility = if (showsRoom) View.VISIBLE else View.GONE
        binding.tvProfileRoom.visibility = if (showsRoom) View.VISIBLE else View.GONE

        if (showsRoom) {
            val rm = user.roomNumber?.trim() ?: ""
            binding.tvProfileRoom.text = rm
            if (rm.isNotEmpty()) {
                val norm = normalizeRoom(rm)
                for (rwo in currentRoomOccupancyList) {
                    val r = rwo.room
                    if (r != null && norm == normalizeRoom(r.roomNumber)) {
                        val displayNum = if (r.roomNumber.isNullOrBlank()) rm else r.roomNumber!!
                        binding.tvProfileRoom.text = getString(R.string.label_room, displayNum) + " • " + rwo.getStatusText()
                        break
                    }
                }
            }
        }
    }

    private fun observeRoomsForUser(user: User) {
        val adminUid = when (user.roleEnum) {
            Role.DORM_ADMIN -> user.uid
            Role.RESIDENT, Role.MAINTENANCE_STAFF -> user.adminUid
            else -> null
        }
        RoomsRepository.getInstance().getRoomsWithOccupancy(adminUid).observe(viewLifecycleOwner) { list ->
            if (_binding == null || list == null) return@observe
            currentRoomOccupancyList.clear()
            currentRoomOccupancyList.addAll(list)
            updateProfileRoomDisplay()
        }
    }

    private fun displayRole(role: Role): String {
        return when (role) {
            Role.DORM_ADMIN -> "Dorm Admin"
            Role.SUPER_ADMIN -> "Super Admin"
            Role.MAINTENANCE_STAFF -> "Maintenance Staff"
            else -> "Resident"
        }
    }

    private fun getInitial(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().substring(0, 1).uppercase(Locale.ROOT)
    }

    private fun showEditProfileDialog() {
        val user = currentUser ?: return

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val tilName = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_full_name)
        }
        val etName = EditText(requireContext()).apply {
            setText(user.name)
        }
        tilName.addView(etName)
        layout.addView(tilName)

        val isSuperAdmin = user.roleEnum == Role.SUPER_ADMIN
        val etPhone = EditText(requireContext()).apply {
            setText(user.phoneNumber)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        if (!isSuperAdmin) {
            val tilPhone = TextInputLayout(requireContext()).apply {
                hint = getString(R.string.hint_contact_number)
                val topMargin = (12 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, 0) }
            }
            tilPhone.addView(etPhone)
            layout.addView(tilPhone)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_edit_profile)
            .setView(layout)
            .setPositiveButton(R.string.manage_users_save) { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = if (!isSuperAdmin) etPhone.text.toString().trim() else user.phoneNumber

                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.error_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                AuthRepository.getInstance().updateUserProfile(
                    user.uid ?: "", newName, null, newPhone,
                    object : AuthRepository.Callback {
                        override fun onSuccess() {
                            if (isAdded) {
                                Toast.makeText(requireContext(), R.string.msg_profile_updated, Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(error: String) {
                            if (isAdded) {
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val tilPass1 = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val etPass1 = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        tilPass1.addView(etPass1)
        layout.addView(tilPass1)

        val tilPass2 = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.hint_confirm_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val etPass2 = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        tilPass2.addView(etPass2)
        layout.addView(tilPass2)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_change_password)
            .setView(layout)
            .setPositiveButton(R.string.manage_users_save) { _, _ ->
                val p1 = etPass1.text.toString()
                val p2 = etPass2.text.toString()

                if (p1.length < 6) {
                    Toast.makeText(requireContext(), R.string.error_password_short, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (p1 != p2) {
                    Toast.makeText(requireContext(), R.string.error_password_match, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                AuthRepository.getInstance().changePassword(p1, object : AuthRepository.Callback {
                    override fun onSuccess() {
                        if (isAdded) {
                            Toast.makeText(requireContext(), R.string.msg_password_changed, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(error: String) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
            .setNeutralButton(R.string.action_send_reset_email) { _, _ -> sendPasswordResetEmail() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun sendPasswordResetEmail() {
        val email = currentUser?.email
        if (email.isNullOrBlank()) return

        AuthRepository.getInstance().sendPasswordResetEmail(email, object : AuthRepository.Callback {
            override fun onSuccess() {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.msg_reset_email_sent_to, email),
                        Toast.LENGTH_LONG
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

    private fun confirmSignOut() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_sign_out)
            .setMessage(R.string.msg_confirm_sign_out)
            .setPositiveButton(R.string.action_sign_out) { _, _ ->
                (requireActivity() as MainActivity).signOut()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
