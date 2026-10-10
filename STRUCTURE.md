# DormMate — Project Structure (Part 1 + Part 2)

```
dormmate_p2/
├── build.gradle                     # Project-level plugin declarations
├── firestore.rules                  # ★ Updated: users + maintenance_reports + bills
│
└── app/
    ├── build.gradle                 # All deps (Firebase BOM, M3, Glide…)
    │
    └── src/main/
        ├── AndroidManifest.xml      # Permissions, activities, FCM service
        │
        ├── java/com/dormmate/
        │   │
        │   ├── DormMateApp.java             # FCM notification channel
        │   ├── MainActivity.java            # BottomNav shell + role-aware tabs
        │   │
        │   ├── model/
        │   │   ├── Role.java                # RESIDENT | DORM_ADMIN | MAINTENANCE_STAFF | SUPER_ADMIN
        │   │   ├── User.java                # uid, name, email, role, roomNumber, isVerified, fcmToken
        │   │   ├── MaintenanceReport.java   # ★ reportId + all 12 fields + Status enum
        │   │   └── Bill.java                # ★ billId + all 13 fields + getEffectiveAmount()
        │   │
        │   ├── repository/
        │   │   ├── AuthRepository.java      # Firebase Auth + users Firestore (singleton)
        │   │   ├── MaintenanceRepository.java  # ★ CRUD + real-time listeners (singleton)
        │   │   └── BillsRepository.java        # ★ CRUD + real-time listeners (singleton)
        │   │
        │   ├── auth/
        │   │   ├── AuthViewModel.java
        │   │   ├── SplashActivity.java
        │   │   ├── LoginActivity.java
        │   │   ├── RegisterActivity.java
        │   │   └── PendingVerificationActivity.java
        │   │
        │   ├── home/
        │   │   ├── HomeFragment.java        # ★ wired to live counts (Part 2)
        │   │   └── HomeViewModel.java       # ★ pulls counts from Bills + Maintenance repos
        │   │
        │   ├── maintenance/
        │   │   ├── MaintenanceFragment.java   # ★ SwipeRefresh, chip filter, empty state
        │   │   ├── MaintenanceAdapter.java    # ListAdapter + DiffUtil, role-aware buttons
        │   │   ├── SubmitReportBottomSheet.java  # Title, desc, room, photo → Storage
        │   │   └── AssignStaffDialog.java       # Fetches MAINTENANCE_STAFF list, assigns
        │   │
        │   ├── bills/
        │   │   ├── BillsFragment.java         # ★ SwipeRefresh, month filter, unpaid banner
        │   │   ├── BillsAdapter.java          # Paid/unpaid badge, surcharge warning
        │   │   └── GenerateBillBottomSheet.java  # Resident spinner, utilities, due date, auto-total
        │   │
        │   └── util/
        │       ├── DormMateFcmService.java  # Token refresh + foreground notifications
        │       └── SessionManager.java      # SharedPrefs cache
        │
        └── res/
            ├── layout/
            │   ├── activity_main.xml
            │   ├── activity_login.xml
            │   ├── activity_register.xml
            │   ├── activity_pending_verification.xml
            │   ├── fragment_home.xml
            │   ├── fragment_maintenance.xml       # ★ AppBar+chips, SwipeRefresh, empty state
            │   ├── fragment_bills.xml             # ★ AppBar+filter btn, SwipeRefresh, empty state
            │   ├── item_maintenance_report.xml    # CardView + status badge + action button
            │   ├── item_bill.xml                  # CardView + paid/unpaid badge + pay button
            │   ├── bottom_sheet_submit_report.xml
            │   └── bottom_sheet_generate_bill.xml
            │
            ├── menu/
            │   └── bottom_nav_menu.xml
            │
            ├── drawable/
            │   ├── bg_badge.xml              # Oval (red dot)
            │   ├── bg_status_badge.xml       # ★ Rounded pill (status text badges)
            │   ├── ic_home.xml  ic_build.xml  ic_receipt.xml  ic_announcement.xml
            │   ├── ic_person.xml  ic_email.xml  ic_lock.xml  ic_door.xml
            │   ├── ic_arrow_back.xml  ic_chevron_right.xml  ic_check_circle.xml
            │   ├── ic_pending_approval.xml  ic_notification.xml
            │   ├── ic_add.xml          ★ FAB icon
            │   ├── ic_filter.xml       ★ Month-filter button icon
            │   ├── ic_warning.xml      ★ Surcharge / unpaid banner icon
            │   ├── ic_photo.xml        ★ Photo attach icon
            │   ├── ic_calendar.xml     ★ Due date picker icon
            │   └── ic_money.xml        ★ Bills / peso icon
            │
            ├── color/
            │   └── bottom_nav_icon_tint.xml
            │
            └── values/
                ├── strings.xml          # ★ + empty states, status labels, filter labels
                ├── colors.xml           # M3 tokens + status_pending/in_progress/resolved
                ├── themes.xml
                └── values-night/colors.xml
```

## Firestore schema (Part 2)

### `users/{uid}` (unchanged)
| Field | Type | Notes |
|---|---|---|
| name | String | |
| email | String | |
| role | String | RESIDENT \| DORM_ADMIN \| MAINTENANCE_STAFF \| SUPER_ADMIN |
| roomNumber | String | |
| isVerified | Boolean | false until admin approves |
| fcmToken | String | refreshed by DormMateFcmService |
| createdAt | Timestamp | server-set |
| profileImageUrl | String | null until Part 3 |

### `maintenance_reports/{reportId}`
| Field | Type | Notes |
|---|---|---|
| reportId | String | auto-set after Firestore .add() |
| residentUid | String | |
| residentName | String | |
| roomNumber | String | |
| title | String | |
| description | String | |
| photoUrl | String | nullable; set after Storage upload |
| status | String | PENDING \| IN_PROGRESS \| RESOLVED |
| assignedStaffUid | String | nullable |
| assignedStaffName | String | nullable |
| createdAt | Timestamp | server-set |
| updatedAt | Date | set on every write |

Storage path: `maintenance_photos/{reportId}`

### `bills/{billId}`
| Field | Type | Notes |
|---|---|---|
| billId | String | auto-set after Firestore .add() |
| residentUid | String | |
| residentName | String | |
| roomNumber | String | |
| month | Int | 1–12 |
| year | Int | |
| roomRent | Double | |
| electricity | Double | |
| water | Double | |
| wifi | Double | |
| totalAmount | Double | sum of the four above |
| isPaid | Boolean | |
| dueDate | Date | |
| paidAt | Date | nullable |
| hasSurcharge | Boolean | computed via getEffectiveAmount() |
| createdAt | Timestamp | server-set |

## // PART3 HERE markers

| File | Location | What goes there |
|---|---|---|
| `MaintenanceFragment.java` | `onCycleStatusClicked → onSuccess` | FCM → resident: status changed |
| `MaintenanceRepository.java` | `submitReport → onSuccess` | FCM → admin: new report |
| `MaintenanceRepository.java` | `assignStaff → onSuccess` | FCM → staff: new assignment |
| `MaintenanceRepository.java` | `cycleStatus → onSuccess` | FCM → resident: status update |
| `AssignStaffDialog.java` | `assignStaff → onSuccess` | FCM → assigned staff |
| `BillsFragment.java` | `onConfirmPaymentClicked → onSuccess` | FCM → admin: payment confirmed |
| `BillsRepository.java` | `generateBill → onSuccess` | FCM → resident: new bill |
| `BillsRepository.java` | `confirmPayment → onSuccess` | FCM → admin: payment confirmed |
| `HomeViewModel.java` | `refresh()` body | Wire AnnouncementsRepository.getLatest() |
| `MainActivity.java` | `nav_announcements` branch | `loadFragment(new AnnouncementsFragment())` |
| `MainActivity.java` | `nav_profile` branch | `loadFragment(new ProfileFragment())` |

## Firestore indexes needed (create in Firebase Console)

```
maintenance_reports:
  residentUid ASC, createdAt DESC
  assignedStaffUid ASC, createdAt DESC
  (collection-scope — Firestore will prompt you on first query)

bills:
  residentUid ASC, year DESC, month DESC
  month ASC, year ASC, residentUid ASC
```
