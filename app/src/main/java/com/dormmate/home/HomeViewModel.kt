package com.dormmate.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.dormmate.model.Announcement
import com.dormmate.model.Bill
import com.dormmate.model.MaintenanceReport
import com.dormmate.model.Role
import com.dormmate.model.User
import com.dormmate.repository.AnnouncementsRepository
import com.dormmate.repository.AuthRepository
import com.dormmate.repository.BillsRepository
import com.dormmate.repository.MaintenanceRepository
import java.util.Calendar

class HomeViewModel : ViewModel() {

    class UrgentBannerState(
        val type: Type,
        val message: String,
        val actionText: String,
        val billId: String?
    ) {
        enum class Type { NONE, OVERDUE_BILL, OPEN_TICKETS }
    }

    private val authRepo = AuthRepository.getInstance()
    private val billsRepo = BillsRepository.getInstance()
    private val maintenanceRepo = MaintenanceRepository.getInstance()
    private val announcementsRepo = AnnouncementsRepository.getInstance()

    val totalResidentsCount = MutableLiveData(0)
    val pendingApprovalCount = MutableLiveData(0)
    val unpaidBillCount = MutableLiveData(0)
    val moneyCollectedThisMonth = MutableLiveData(0.0)
    val openTicketCount = MutableLiveData(0)
    val assignedTicketCount = MutableLiveData(0)
    val latestAnnouncement = MutableLiveData<String?>(null)
    val isLoading = MutableLiveData(false)
    val urgentBannerState = MutableLiveData(
        UrgentBannerState(UrgentBannerState.Type.NONE, "", "", null)
    )

    private var currentBillsSource: LiveData<List<Bill>>? = null
    private var currentReportsSource: LiveData<List<MaintenanceReport>>? = null
    private var currentAssignedReportsSource: LiveData<List<MaintenanceReport>>? = null
    private var currentLatestAnnouncementSource: LiveData<Announcement?>? = null

    private val billsObserver = Observer<List<Bill>> { bills ->
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH) + 1
        val curYear = cal.get(Calendar.YEAR)

        var unpaid = 0
        var collected = 0.0
        if (bills != null) {
            for (b in bills) {
                if (!b.isPaid) {
                    unpaid++
                } else {
                    if (b.month == curMonth && b.year == curYear) {
                        collected += b.totalAmount
                    }
                }
            }
        }
        unpaidBillCount.postValue(unpaid)
        moneyCollectedThisMonth.postValue(collected)

        reevaluateUrgentBanner(currentUser.value, bills, currentReportsSource?.value)
    }

    private val reportsObserver = Observer<List<MaintenanceReport>> { reports ->
        var open = 0
        if (reports != null) {
            for (r in reports) {
                if (r.statusEnum != MaintenanceReport.Status.RESOLVED) {
                    open++
                }
            }
        }
        openTicketCount.postValue(open)

        reevaluateUrgentBanner(currentUser.value, currentBillsSource?.value, reports)
    }

    private val assignedReportsObserver = Observer<List<MaintenanceReport>> { reports ->
        var open = 0
        if (reports != null) {
            for (r in reports) {
                if (r.statusEnum != MaintenanceReport.Status.RESOLVED) {
                    open++
                }
            }
        }
        assignedTicketCount.postValue(open)
        reevaluateUrgentBanner(currentUser.value, currentBillsSource?.value, currentReportsSource?.value)
    }

    private val announcementsObserver = Observer<Announcement?> { ann ->
        latestAnnouncement.postValue(ann?.title)
    }

    val currentUser: LiveData<User?>
        get() = authRepo.currentUser

    fun refresh(user: User?) {
        if (user == null) return
        isLoading.value = true
        val role = user.roleEnum
        val uid = user.uid

        if (role == Role.SUPER_ADMIN) {
            authRepo.getTotalAdminsCount { count -> totalResidentsCount.postValue(count) }
        } else if (role == Role.DORM_ADMIN) {
            authRepo.getTotalResidentsCount(uid) { count -> totalResidentsCount.postValue(count) }
            authRepo.getPendingApprovalCount(uid, user.name) { count -> pendingApprovalCount.postValue(count) }
        } else {
            authRepo.getTotalResidentsCount { count -> totalResidentsCount.postValue(count) }
            authRepo.getPendingApprovalCount(uid, user?.name) { count -> pendingApprovalCount.postValue(count) }
        }

        val billsSource = when (role) {
            Role.RESIDENT -> if (!uid.isNullOrEmpty()) billsRepo.getBillsForResident(uid) else null
            Role.DORM_ADMIN -> if (!uid.isNullOrEmpty()) billsRepo.getBillsForAdmin(uid) else null
            Role.SUPER_ADMIN -> billsRepo.getAllBills()
            else -> null
        }

        if (billsSource != null) {
            swapBillsSource(billsSource)
        } else {
            unpaidBillCount.value = 0
            moneyCollectedThisMonth.value = 0.0
        }

        if (role == Role.MAINTENANCE_STAFF) {
            if (!uid.isNullOrEmpty()) {
                swapAssignedReportsSource(maintenanceRepo.getReportsForStaff(uid))
            }
            swapReportsSource(maintenanceRepo.getAllReports())
        } else {
            swapAssignedReportsSource(null)
            val reportsSource = when (role) {
                Role.RESIDENT -> if (!uid.isNullOrEmpty()) maintenanceRepo.getReportsForResident(uid) else null
                Role.DORM_ADMIN -> if (!uid.isNullOrEmpty()) maintenanceRepo.getReportsForAdmin(uid) else null
                else -> maintenanceRepo.getAllReports()
            }
            swapReportsSource(reportsSource)
        }

        val posterUid = when (role) {
            Role.DORM_ADMIN -> uid
            Role.RESIDENT -> user.adminUid
            else -> null
        }

        val latestSource = if (!posterUid.isNullOrEmpty()) {
            announcementsRepo.getLatestForPoster(posterUid)
        } else {
            announcementsRepo.getLatest()
        }

        swapLatestAnnouncementSource(latestSource)

        isLoading.value = false
    }

    private fun swapLatestAnnouncementSource(newSource: LiveData<Announcement?>?) {
        currentLatestAnnouncementSource?.removeObserver(announcementsObserver)
        currentLatestAnnouncementSource = newSource
        newSource?.observeForever(announcementsObserver)
    }

    private fun swapBillsSource(newSource: LiveData<List<Bill>>?) {
        currentBillsSource?.removeObserver(billsObserver)
        currentBillsSource = newSource
        newSource?.observeForever(billsObserver)
    }

    private fun swapReportsSource(newSource: LiveData<List<MaintenanceReport>>?) {
        currentReportsSource?.removeObserver(reportsObserver)
        currentReportsSource = newSource
        newSource?.observeForever(reportsObserver)
    }

    private fun swapAssignedReportsSource(newSource: LiveData<List<MaintenanceReport>>?) {
        currentAssignedReportsSource?.removeObserver(assignedReportsObserver)
        currentAssignedReportsSource = newSource
        newSource?.observeForever(assignedReportsObserver)
    }

    private fun reevaluateUrgentBanner(
        user: User?,
        bills: List<Bill>?,
        reports: List<MaintenanceReport>?
    ) {
        if (user == null || user.roleEnum == Role.SUPER_ADMIN) {
            urgentBannerState.value = UrgentBannerState(UrgentBannerState.Type.NONE, "", "", null)
            return
        }

        if (user.roleEnum == Role.MAINTENANCE_STAFF) {
            var assignedOpenCount = 0
            val assignedReports = currentAssignedReportsSource?.value
            if (assignedReports != null) {
                for (r in assignedReports) {
                    if (r.statusEnum != MaintenanceReport.Status.RESOLVED) {
                        assignedOpenCount++
                    }
                }
            }
            if (assignedOpenCount > 0) {
                val msg = "$assignedOpenCount " + if (assignedOpenCount == 1) "assigned maintenance ticket" else "assigned maintenance tickets"
                urgentBannerState.value = UrgentBannerState(
                    UrgentBannerState.Type.OPEN_TICKETS,
                    msg,
                    "View >",
                    null
                )
            } else {
                urgentBannerState.value = UrgentBannerState(
                    UrgentBannerState.Type.NONE,
                    "",
                    "",
                    null
                )
            }
            return
        }

        var overdueBill: Bill? = null
        if (bills != null) {
            for (b in bills) {
                if (!b.isPaid && (b.isOverdue() || b.isHasSurcharge)) {
                    overdueBill = b
                    break
                }
            }
        }

        if (overdueBill != null) {
            val room = if (!overdueBill.roomNumber.isNullOrBlank()) overdueBill.roomNumber else user.roomNumber
            val msg = if (!room.isNullOrBlank()) "Bill overdue — Room $room" else "Bill overdue"
            urgentBannerState.value = UrgentBannerState(
                UrgentBannerState.Type.OVERDUE_BILL,
                msg,
                "Pay >",
                overdueBill.billId
            )
            return
        }

        var openCount = 0
        if (reports != null) {
            for (r in reports) {
                if (r.statusEnum != MaintenanceReport.Status.RESOLVED) {
                    openCount++
                }
            }
        }

        if (openCount > 0) {
            val msg = "$openCount " + if (openCount == 1) "open maintenance ticket" else "open maintenance tickets"
            urgentBannerState.value = UrgentBannerState(
                UrgentBannerState.Type.OPEN_TICKETS,
                msg,
                "View >",
                null
            )
            return
        }

        urgentBannerState.value = UrgentBannerState(
            UrgentBannerState.Type.NONE,
            "",
            "",
            null
        )
    }

    override fun onCleared() {
        super.onCleared()
        currentBillsSource?.removeObserver(billsObserver)
        currentReportsSource?.removeObserver(reportsObserver)
        currentAssignedReportsSource?.removeObserver(assignedReportsObserver)
        currentLatestAnnouncementSource?.removeObserver(announcementsObserver)
    }
}
