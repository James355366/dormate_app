package com.dormmate.maintenance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.dormmate.MainActivity
import com.dormmate.R
import com.dormmate.model.MaintenanceReport
import com.dormmate.repository.MaintenanceRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.storage.FirebaseStorage

class SubmitReportBottomSheet : BottomSheetDialogFragment() {

    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var etRoomNumber: EditText
    private lateinit var ivPhoto: ImageView
    private lateinit var btnPickPhoto: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    private var selectedPhotoUri: Uri? = null

    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedPhotoUri = result.data?.data
                ivPhoto.setImageURI(selectedPhotoUri)
                ivPhoto.visibility = View.VISIBLE
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_submit_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etTitle = view.findViewById(R.id.et_report_title)
        etDescription = view.findViewById(R.id.et_report_description)
        etRoomNumber = view.findViewById(R.id.et_report_room)
        ivPhoto = view.findViewById(R.id.iv_report_photo)
        btnPickPhoto = view.findViewById(R.id.btn_pick_photo)
        btnSubmit = view.findViewById(R.id.btn_submit_report)
        progressBar = view.findViewById(R.id.progress_submit)

        val activity = activity
        if (activity is MainActivity) {
            val u = activity.currentUser
            if (u != null && u.roomNumber != null) {
                etRoomNumber.setText(u.roomNumber)
            }
        }

        btnPickPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            photoPickerLauncher.launch(intent)
        }

        btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun validateAndSubmit() {
        val title = etTitle.text.toString().trim()
        val desc = etDescription.text.toString().trim()
        val room = etRoomNumber.text.toString().trim()

        if (TextUtils.isEmpty(title)) {
            etTitle.error = "Required"
            return
        }
        if (TextUtils.isEmpty(desc)) {
            etDescription.error = "Required"
            return
        }
        if (TextUtils.isEmpty(room)) {
            etRoomNumber.error = "Required"
            return
        }

        val activity = activity
        val currentUser = if (activity is MainActivity) activity.currentUser else null
        if (currentUser == null) return

        setLoading(true)

        val report = MaintenanceReport(
            currentUser.uid, currentUser.name, room, title, desc
        )
        if (!currentUser.adminUid.isNullOrBlank()) {
            report.adminUid = currentUser.adminUid
        }

        val repo = MaintenanceRepository.getInstance()
        repo.submitReport(report, object : MaintenanceRepository.Callback {
            override fun onSuccess(id: String) {
                val uri = selectedPhotoUri
                if (uri != null) {
                    uploadPhoto(id, uri, repo)
                } else {
                    onSubmitComplete()
                }
            }

            override fun onFailure(error: String) {
                setLoading(false)
                Toast.makeText(requireContext(), "Submit failed: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun uploadPhoto(reportId: String, photoUri: Uri, repo: MaintenanceRepository) {
        val ref = FirebaseStorage.getInstance().getReference("maintenance_photos/$reportId")
        ref.putFile(photoUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Photo upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                repo.updatePhotoUrl(reportId, downloadUri.toString(), object : MaintenanceRepository.Callback {
                    override fun onSuccess(id: String) {
                        onSubmitComplete()
                    }

                    override fun onFailure(error: String) {
                        onSubmitComplete()
                    }
                })
            }
            .addOnFailureListener {
                onSubmitComplete()
            }
    }

    private fun onSubmitComplete() {
        setLoading(false)
        Toast.makeText(requireContext(), "Report submitted!", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
    }
}
