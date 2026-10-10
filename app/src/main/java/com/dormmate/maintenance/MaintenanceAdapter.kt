package com.dormmate.maintenance

import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dormmate.R
import com.dormmate.model.MaintenanceReport
import com.dormmate.model.Role

class MaintenanceAdapter(
    private val currentRole: Role,
    private val listener: OnItemClickListener?
) : ListAdapter<MaintenanceReport, MaintenanceAdapter.ViewHolder>(DIFF_CALLBACK) {

    interface OnItemClickListener {
        fun onAssignStaffClicked(report: MaintenanceReport)
        fun onCycleStatusClicked(report: MaintenanceReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_report, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        val report = getItem(position)
        h.bind(report, currentRole, listener)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_report_title)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_report_desc)
        val tvRoom: TextView = itemView.findViewById(R.id.tv_report_room)
        val tvDate: TextView = itemView.findViewById(R.id.tv_report_date)
        val tvStaff: TextView = itemView.findViewById(R.id.tv_assigned_staff)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_report_status)
        val btnAction: Button = itemView.findViewById(R.id.btn_report_action)

        fun bind(report: MaintenanceReport, role: Role, listener: OnItemClickListener?) {
            tvTitle.text = report.title
            tvDesc.text = report.description
            tvRoom.text = "Room: ${report.roomNumber}"

            if (report.createdAt != null) {
                tvDate.text = DateFormat.format("MMM dd, yyyy", report.createdAt)
            }

            val staffName = report.assignedStaffName
            tvStaff.text = if (staffName != null) "Assigned: $staffName" else "Unassigned"
            tvStaff.visibility = View.VISIBLE

            applyStatusBadge(report.statusEnum)

            when (role) {
                Role.DORM_ADMIN, Role.SUPER_ADMIN -> {
                    if (report.assignedStaffUid == null) {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Assign Staff"
                        btnAction.setOnClickListener { listener?.onAssignStaffClicked(report) }
                    } else if (report.statusEnum != MaintenanceReport.Status.RESOLVED) {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Reassign"
                        btnAction.setOnClickListener { listener?.onAssignStaffClicked(report) }
                    } else {
                        btnAction.visibility = View.GONE
                    }
                }
                Role.MAINTENANCE_STAFF -> {
                    if (report.statusEnum != MaintenanceReport.Status.RESOLVED) {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = if (report.statusEnum == MaintenanceReport.Status.PENDING) "Start Work" else "Mark Resolved"
                        btnAction.setOnClickListener { listener?.onCycleStatusClicked(report) }
                    } else {
                        btnAction.visibility = View.GONE
                    }
                }
                else -> {
                    btnAction.visibility = View.GONE
                }
            }
        }

        private fun applyStatusBadge(status: MaintenanceReport.Status) {
            val colorRes: Int
            val label: String
            when (status) {
                MaintenanceReport.Status.IN_PROGRESS -> {
                    colorRes = R.color.status_in_progress
                    label = "In Progress"
                }
                MaintenanceReport.Status.RESOLVED -> {
                    colorRes = R.color.status_resolved
                    label = "Resolved"
                }
                else -> {
                    colorRes = R.color.status_pending
                    label = "Pending"
                }
            }
            tvStatus.text = label
            tvStatus.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, colorRes)
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MaintenanceReport>() {
            override fun areItemsTheSame(a: MaintenanceReport, b: MaintenanceReport): Boolean {
                return a.reportId != null && a.reportId == b.reportId
            }

            override fun areContentsTheSame(a: MaintenanceReport, b: MaintenanceReport): Boolean {
                return a.status == b.status &&
                        a.assignedStaffUid == b.assignedStaffUid &&
                        a.assignedStaffName == b.assignedStaffName &&
                        a.photoUrl == b.photoUrl
            }
        }
    }
}
