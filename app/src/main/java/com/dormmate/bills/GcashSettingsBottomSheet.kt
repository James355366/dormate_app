package com.dormmate.bills

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.dormmate.R
import com.dormmate.model.User
import com.dormmate.repository.AuthRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class GcashSettingsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var etGcashName: EditText
    private lateinit var etGcashNumber: EditText
    private lateinit var ivGcashQr: ImageView
    private lateinit var btnUploadQr: Button
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private var selectedQrUri: Uri? = null
    private var currentUser: User? = null

    private val qrPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedQrUri = result.data?.data
                ivGcashQr.setImageURI(selectedQrUri)
                ivGcashQr.imageTintList = null
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_gcash_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etGcashName = view.findViewById(R.id.et_gcash_name)
        etGcashNumber = view.findViewById(R.id.et_gcash_number)
        ivGcashQr = view.findViewById(R.id.iv_gcash_qr)
        btnUploadQr = view.findViewById(R.id.btn_upload_qr)
        btnSave = view.findViewById(R.id.btn_save_gcash_settings)
        progressBar = view.findViewById(R.id.progress_gcash_settings)

        loadCurrentGcashSettings()

        btnUploadQr.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            qrPickerLauncher.launch(intent)
        }

        btnSave.setOnClickListener { saveGcashSettings() }
    }

    private fun loadCurrentGcashSettings() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || snapshot == null) return@addOnSuccessListener
                val user = snapshot.toObject(User::class.java)
                currentUser = user
                if (user != null) {
                    etGcashName.setText(user.gcashName ?: "")
                    etGcashNumber.setText(user.gcashNumber ?: "")
                    if (!user.gcashQrUrl.isNullOrBlank()) {
                        ivGcashQr.imageTintList = null
                        Glide.with(this)
                            .load(user.gcashQrUrl)
                            .into(ivGcashQr)
                    }
                }
            }
    }

    private fun saveGcashSettings() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val name = etGcashName.text.toString().trim()
        val number = etGcashNumber.text.toString().trim()

        if (name.isEmpty()) {
            etGcashName.error = "GCash name is required"
            return
        }
        if (number.isEmpty()) {
            etGcashNumber.error = "GCash number is required"
            return
        }

        setLoading(true)

        val qrUri = selectedQrUri
        if (qrUri != null) {
            uploadQrAndSave(uid, name, number, qrUri)
        } else {
            saveToFirestore(uid, name, number, currentUser?.gcashQrUrl)
        }
    }

    private fun uploadQrAndSave(uid: String, name: String, number: String, qrUri: Uri) {
        val ref = FirebaseStorage.getInstance().getReference("gcash_qrs/$uid")
        ref.putFile(qrUri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        saveToFirestore(uid, name, number, downloadUri.toString())
                    }
                    .addOnFailureListener {
                        saveBase64QrAndSave(uid, name, number, qrUri)
                    }
            }
            .addOnFailureListener {
                saveBase64QrAndSave(uid, name, number, qrUri)
            }
    }

    private fun saveBase64QrAndSave(uid: String, name: String, number: String, qrUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(qrUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val maxDim = 600
                val scale = Math.min(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height)
                val width = (originalBitmap.width * Math.min(1f, scale)).toInt()
                val height = (originalBitmap.height * Math.min(1f, scale)).toInt()

                val resized = Bitmap.createScaledBitmap(originalBitmap, Math.max(1, width), Math.max(1, height), true)
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val bytes = baos.toByteArray()
                val base64Str = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

                saveToFirestore(uid, name, number, base64Str)
            } else {
                saveToFirestore(uid, name, number, currentUser?.gcashQrUrl)
            }
        } catch (e: Exception) {
            saveToFirestore(uid, name, number, currentUser?.gcashQrUrl)
        }
    }

    private fun saveToFirestore(uid: String, name: String, number: String, qrUrl: String?) {
        val updates = mutableMapOf<String, Any?>()
        updates["gcashName"] = name
        updates["gcashNumber"] = number
        if (qrUrl != null) {
            updates["gcashQrUrl"] = qrUrl
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                val infoUpdates = mutableMapOf<String, Any>()
                infoUpdates["gcashName"] = name
                infoUpdates["gcashNumber"] = number
                if (qrUrl != null) {
                    infoUpdates["gcashQrUrl"] = qrUrl
                }
                db.collection("dorm_info").document(uid).set(infoUpdates, com.google.firebase.firestore.SetOptions.merge())

                if (isAdded) {
                    setLoading(false)
                    AuthRepository.getInstance().fetchCurrentUser()
                    Toast.makeText(requireContext(), "GCash settings saved!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    setLoading(false)
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !loading
    }
}
