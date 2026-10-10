package com.dormmate.bills

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.dormmate.R
import com.dormmate.model.Bill
import com.dormmate.model.User
import com.dormmate.repository.BillsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale

class PayGcashBottomSheet : BottomSheetDialogFragment() {

    private lateinit var tvAmountDue: TextView
    private lateinit var tvAdminName: TextView
    private lateinit var tvAdminNumber: TextView
    private lateinit var btnCopyNumber: Button
    private lateinit var ivAdminQr: ImageView
    private lateinit var btnOpenGcash: Button
    private lateinit var etRefNum: EditText
    private lateinit var btnPickReceipt: Button
    private lateinit var btnSubmitReceiptDirect: Button
    private lateinit var ivReceiptPreview: ImageView
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    private var targetBill: Bill? = null
    private var adminUser: User? = null
    private var selectedReceiptUri: Uri? = null

    private val receiptPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedReceiptUri = result.data?.data
                ivReceiptPreview.setImageURI(selectedReceiptUri)
                ivReceiptPreview.visibility = View.VISIBLE
                btnSubmitReceiptDirect.visibility = View.VISIBLE
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_pay_gcash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvAmountDue = view.findViewById(R.id.tv_pay_amount_due)
        tvAdminName = view.findViewById(R.id.tv_admin_gcash_name)
        tvAdminNumber = view.findViewById(R.id.tv_admin_gcash_number)
        btnCopyNumber = view.findViewById(R.id.btn_copy_gcash_number)
        ivAdminQr = view.findViewById(R.id.iv_admin_gcash_qr)
        btnOpenGcash = view.findViewById(R.id.btn_open_gcash_app)
        etRefNum = view.findViewById(R.id.et_gcash_ref_num)
        btnPickReceipt = view.findViewById(R.id.btn_pick_receipt)
        btnSubmitReceiptDirect = view.findViewById(R.id.btn_submit_receipt_direct)
        ivReceiptPreview = view.findViewById(R.id.iv_receipt_preview)
        btnSubmit = view.findViewById(R.id.btn_submit_gcash_payment)
        progressBar = view.findViewById(R.id.progress_pay_gcash)

        val bill = targetBill
        if (bill == null) {
            val billId = arguments?.getString("billId")
            if (billId != null) FirebaseFirestore.getInstance().collection("bills").document(billId).get()
                .addOnSuccessListener { snap ->
                    if (viewLifecycleOwnerLiveData.value == null) return@addOnSuccessListener
                    targetBill = snap.toObject(Bill::class.java)
                    targetBill?.let { loaded ->
                        tvAmountDue.text = "Amount Due: ${NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(loaded.getEffectiveAmount())}"
                        etRefNum.setText(loaded.gcashRefNumber.orEmpty())
                        loadAdminGcashDetails(loaded.adminUid)
                    }
                }
        }
        if (bill != null) {
            val fmt = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
            tvAmountDue.text = "Amount Due: ${fmt.format(bill.getEffectiveAmount())}"
            etRefNum.setText(bill.gcashRefNumber.orEmpty())
            loadAdminGcashDetails(bill.adminUid)
        }

        btnCopyNumber.setOnClickListener {
            val num = adminUser?.gcashNumber
            if (!num.isNullOrBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("GCash Number", num)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "GCash number copied!", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenGcash.setOnClickListener {
            openGcashApp()
        }

        btnPickReceipt.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            receiptPickerLauncher.launch(intent)
        }

        btnSubmitReceiptDirect.setOnClickListener { validateAndSubmit() }
        btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun loadAdminGcashDetails(adminUid: String?) {
        if (adminUid.isNullOrBlank()) {
            loadFallbackAdminDetails()
            return
        }

        FirebaseFirestore.getInstance().collection("dorm_info").document(adminUid).get()
            .addOnSuccessListener { snapshot ->
                if (viewLifecycleOwnerLiveData.value == null) return@addOnSuccessListener
                if (snapshot == null || !snapshot.exists()) {
                    loadFallbackAdminDetails()
                    return@addOnSuccessListener
                }
                val u = snapshot.toObject(User::class.java)
                adminUser = u
                btnSubmit.isEnabled = true
                if (u != null) {
                    tvAdminName.text = "Account Name: ${u.gcashName.orEmpty().ifBlank { "Not set" }}"
                    tvAdminNumber.text = "Mobile: ${u.gcashNumber.orEmpty().ifBlank { "Not set" }}"
                    if (!u.gcashQrUrl.isNullOrBlank()) {
                        ivAdminQr.visibility = View.VISIBLE
                        Glide.with(this).load(u.gcashQrUrl).into(ivAdminQr)
                    } else {
                        ivAdminQr.visibility = View.GONE
                    }
                } else {
                    loadFallbackAdminDetails()
                }
            }
            .addOnFailureListener {
                loadFallbackAdminDetails()
            }
    }

    private fun loadFallbackAdminDetails() {
        if (viewLifecycleOwnerLiveData.value == null) return
        tvAdminName.text = "Dorm admin payment details are not set. Contact your admin."
        tvAdminNumber.text = ""
        btnSubmit.isEnabled = true
    }

    private fun openGcashApp() {
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage("com.globe.gcash.android")
            if (intent != null) {
                startActivity(intent)
            } else {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.globe.gcash.android"))
                startActivity(marketIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open GCash app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndSubmit() {
        val bill = targetBill ?: return
        val refNum = etRefNum.text.toString().trim()
        val receiptUri = selectedReceiptUri

        if (refNum.isEmpty() && receiptUri == null && bill.gcashReceiptUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please enter Option 1 (Reference No.) OR attach Option 2 (Receipt Photo).", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)

        val billId = bill.billId ?: return
        val effectiveRefNum = if (refNum.isNotEmpty()) refNum else (bill.gcashRefNumber.orEmpty().ifBlank { "Receipt Photo Uploaded" })

        if (receiptUri != null) {
            uploadReceiptAndSubmit(billId, effectiveRefNum, receiptUri)
        } else {
            submitPayment(billId, effectiveRefNum, bill.gcashReceiptUrl)
        }
    }

    private fun uploadReceiptAndSubmit(billId: String, refNum: String, receiptUri: Uri) {
        val ref = FirebaseStorage.getInstance().getReference("gcash_receipts/$billId")
        ref.putFile(receiptUri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        submitPayment(billId, refNum, downloadUri.toString())
                    }
                    .addOnFailureListener {
                        saveBase64ReceiptAndSubmit(billId, refNum, receiptUri)
                    }
            }
            .addOnFailureListener {
                saveBase64ReceiptAndSubmit(billId, refNum, receiptUri)
            }
    }

    private fun saveBase64ReceiptAndSubmit(billId: String, refNum: String, receiptUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(receiptUri)
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

                submitPayment(billId, refNum, base64Str)
            } else {
                submitPayment(billId, refNum, targetBill?.gcashReceiptUrl)
            }
        } catch (e: Exception) {
            submitPayment(billId, refNum, targetBill?.gcashReceiptUrl)
        }
    }

    private fun submitPayment(billId: String, refNum: String, receiptUrl: String?) {
        BillsRepository.getInstance().submitGcashPayment(billId, refNum, receiptUrl, object : BillsRepository.Callback {
            override fun onSuccess(id: String) {
                if (isAdded) {
                    setLoading(false)
                    Toast.makeText(requireContext(), "GCash payment submitted for admin verification!", Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }

            override fun onFailure(error: String) {
                if (isAdded) {
                    setLoading(false)
                    Toast.makeText(requireContext(), "Submission failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
    }

    companion object {
        @JvmStatic
        fun newInstance(bill: Bill): PayGcashBottomSheet {
            val fragment = PayGcashBottomSheet()
            fragment.targetBill = bill
            fragment.arguments = Bundle().apply { putString("billId", bill.billId) }
            return fragment
        }
    }
}
