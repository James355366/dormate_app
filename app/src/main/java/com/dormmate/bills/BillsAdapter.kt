package com.dormmate.bills

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.Bill
import com.dormmate.model.Role
import java.text.NumberFormat
import java.util.Locale

class BillsAdapter(
    private val currentRole: Role,
    private val listener: OnBillActionListener?
) : ListAdapter<Bill, BillsAdapter.ViewHolder>(DIFF) {

    interface OnBillActionListener {
        fun onConfirmPaymentClicked(bill: Bill)
        fun onPayGcashClicked(bill: Bill)
        fun onVerifyGcashClicked(bill: Bill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bill, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        h.bind(getItem(position), currentRole, listener)
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvMonth: TextView = v.findViewById(R.id.tv_bill_month)
        val tvResident: TextView = v.findViewById(R.id.tv_bill_resident)
        val tvRoom: TextView = v.findViewById(R.id.tv_bill_room)
        val tvBreakdown: TextView = v.findViewById(R.id.tv_bill_breakdown)
        val tvTotal: TextView = v.findViewById(R.id.tv_bill_total)
        val tvStatus: TextView = v.findViewById(R.id.tv_bill_status)
        val tvSurcharge: TextView = v.findViewById(R.id.tv_bill_surcharge)
        val tvDue: TextView = v.findViewById(R.id.tv_bill_due)
        val tvTimeline: TextView = v.findViewById(R.id.tv_payment_timeline)
        val tvGcashRef: TextView = v.findViewById(R.id.tv_bill_gcash_ref)
        val btnPay: Button = v.findViewById(R.id.btn_confirm_payment)
        val btnPayGcash: Button = v.findViewById(R.id.btn_pay_gcash)

        fun bind(bill: Bill, role: Role, listener: OnBillActionListener?) {
            tvMonth.text = "${getMonthName(bill.month)} ${bill.year}"

            if (role == Role.DORM_ADMIN || role == Role.SUPER_ADMIN) {
                tvResident.visibility = View.VISIBLE
                tvResident.text = "${bill.residentName} · ${bill.roomNumber}"
            } else {
                tvResident.visibility = View.GONE
            }
            tvRoom.text = "Room: ${bill.roomNumber}"

            tvBreakdown.text =
                "Rent: ${PHP.format(bill.roomRent)}  |  Elec: ${PHP.format(bill.electricity)}  |  Water: ${PHP.format(bill.water)}  |  WiFi: ${PHP.format(bill.wifi)}"

            if (bill.dueDate != null) {
                tvDue.text = "Due: " + DateFormat.format("MMM dd, yyyy", bill.dueDate)
            }

            val effective = bill.getEffectiveAmount()
            tvTotal.text = "Total: ${PHP.format(effective)}"

            if (bill.isOverdue()) {
                tvSurcharge.visibility = View.VISIBLE
                tvSurcharge.text = "⚠ 5% late surcharge applied"
            } else {
                tvSurcharge.visibility = View.GONE
            }

            val isPendingVerification = bill.paymentStatus == "PENDING_VERIFICATION"
            val rejected = bill.paymentStatus == "REJECTED"
            tvTimeline.visibility = if (bill.paymentMethod == "GCASH" || isPendingVerification || rejected) View.VISIBLE else View.GONE
            tvTimeline.text = when {
                bill.isPaid -> "✓ Submitted → ✓ Admin reviewed → ✓ Verified / Paid"
                rejected -> "✓ Submitted → Admin reviewed → Rejected\nUpdate your reference number or receipt and resubmit."
                isPendingVerification -> "✓ Submitted → Admin reviewing → Verified / Paid (pending)"
                else -> "Submitted → Admin reviewing → Verified / Paid"
            }
            tvTimeline.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context,
                if (bill.isPaid) R.color.availability_green else if (rejected) R.color.md_theme_error else R.color.md_theme_on_surface))

            if (bill.isPaid) {
                tvStatus.text = "Paid"
                tvStatus.backgroundTintList = ResourcesCompat.getColorStateList(
                    itemView.resources, R.color.status_resolved_tint, null
                )
                btnPay.visibility = View.GONE
                btnPayGcash.visibility = View.GONE

                if (!bill.gcashRefNumber.isNullOrBlank() || !bill.gcashReceiptUrl.isNullOrBlank()) {
                    tvGcashRef.visibility = View.VISIBLE
                    val refText = if (!bill.gcashRefNumber.isNullOrBlank()) "GCash Ref: ${bill.gcashRefNumber}" else "Receipt Photo Uploaded"
                    val hasPhoto = !bill.gcashReceiptUrl.isNullOrBlank()
                    tvGcashRef.text = if (hasPhoto) "$refText  📷 [View Receipt Photo]" else refText
                    if (hasPhoto) {
                        tvGcashRef.setOnClickListener { listener?.onVerifyGcashClicked(bill) }
                    } else {
                        tvGcashRef.setOnClickListener(null)
                    }
                } else {
                    tvGcashRef.visibility = View.GONE
                }
            } else if (isPendingVerification) {
                tvStatus.text = "Pending Verification"
                tvStatus.backgroundTintList = ResourcesCompat.getColorStateList(
                    itemView.resources, R.color.status_in_progress_tint, null
                )
                if (!bill.gcashRefNumber.isNullOrBlank() || !bill.gcashReceiptUrl.isNullOrBlank()) {
                    tvGcashRef.visibility = View.VISIBLE
                    val refText = if (!bill.gcashRefNumber.isNullOrBlank()) "GCash Ref: ${bill.gcashRefNumber}" else "Receipt Photo Uploaded"
                    val hasPhoto = !bill.gcashReceiptUrl.isNullOrBlank()
                    tvGcashRef.text = if (hasPhoto) "$refText  📷 [View Receipt Photo]" else refText
                    if (hasPhoto) {
                        tvGcashRef.setOnClickListener { listener?.onVerifyGcashClicked(bill) }
                    } else {
                        tvGcashRef.setOnClickListener(null)
                    }
                } else {
                    tvGcashRef.visibility = View.VISIBLE
                    tvGcashRef.text = "GCash Ref: Submitted"
                    tvGcashRef.setOnClickListener(null)
                }

                if (role == Role.DORM_ADMIN || role == Role.SUPER_ADMIN) {
                    btnPay.visibility = View.VISIBLE
                    btnPay.isEnabled = true
                    btnPay.text = "Verify GCash Payment"
                    btnPay.setOnClickListener { listener?.onVerifyGcashClicked(bill) }
                    btnPayGcash.visibility = View.GONE
                } else if (role == Role.RESIDENT) {
                    btnPay.visibility = View.VISIBLE
                    btnPay.isEnabled = false
                    btnPay.text = "Submitted (Pending Verification)"
                    btnPayGcash.visibility = View.GONE
                } else {
                    btnPay.visibility = View.GONE
                    btnPayGcash.visibility = View.GONE
                }
            } else {
                tvStatus.text = if (rejected) "Payment Rejected" else "Unpaid"
                tvStatus.backgroundTintList = ResourcesCompat.getColorStateList(
                    itemView.resources, R.color.status_pending_tint, null
                )
                tvGcashRef.visibility = View.GONE

                if (role == Role.RESIDENT) {
                    btnPayGcash.visibility = View.VISIBLE
                    btnPayGcash.text = if (rejected) "Update & Resubmit GCash" else "Pay via GCash"
                    btnPayGcash.setOnClickListener { listener?.onPayGcashClicked(bill) }

                    btnPay.visibility = View.GONE
                } else if (role == Role.DORM_ADMIN || role == Role.SUPER_ADMIN) {
                    btnPayGcash.visibility = View.GONE
                    btnPay.visibility = View.VISIBLE
                    btnPay.isEnabled = true
                    btnPay.text = "Mark as Paid"
                    btnPay.setOnClickListener { listener?.onConfirmPaymentClicked(bill) }
                } else {
                    btnPay.visibility = View.GONE
                    btnPayGcash.visibility = View.GONE
                }
            }
        }

        private fun getMonthName(month: Int): String {
            val names = arrayOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            return if (month in 1..12) names[month - 1] else "?"
        }
    }

    companion object {
        private val PHP: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

        private val DIFF = object : DiffUtil.ItemCallback<Bill>() {
            override fun areItemsTheSame(a: Bill, b: Bill): Boolean {
                return a.billId != null && a.billId == b.billId
            }

            override fun areContentsTheSame(a: Bill, b: Bill): Boolean {
                return a.isPaid == b.isPaid &&
                        a.totalAmount == b.totalAmount &&
                        a.paymentStatus == b.paymentStatus &&
                        a.gcashRefNumber == b.gcashRefNumber && a.gcashReceiptUrl == b.gcashReceiptUrl &&
                        a.paymentMethod == b.paymentMethod && a.dueDate == b.dueDate
            }
        }
    }
}
