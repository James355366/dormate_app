package com.dormmate.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.dormmate.R
import com.dormmate.model.Role
import com.dormmate.model.User
import java.util.Locale

object AvatarUtils {

    @JvmStatic
    fun formatDisplayName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val trimmed = name.trim()
        val parts = trimmed.split("\\s+".toRegex())
        return parts.joinToString(" ") { word ->
            if (word.isNotEmpty()) {
                word.substring(0, 1).uppercase(Locale.ROOT) + word.substring(1).lowercase(Locale.ROOT)
            } else {
                ""
            }
        }
    }

    @JvmStatic
    fun bindAvatar(
        user: User?,
        tvAvatar: TextView?,
        ivAvatar: ImageView?
    ) {
        if (user == null || tvAvatar == null || ivAvatar == null) return

        val rawName = if (!user.name.isNullOrBlank()) user.name!! else (user.email ?: "")
        val initial = if (rawName.isNotBlank()) rawName.trim().substring(0, 1).uppercase(Locale.ROOT) else "?"
        val photoUrl = user.profileImageUrl

        if (!photoUrl.isNullOrBlank()) {
            tvAvatar.visibility = View.GONE
            ivAvatar.visibility = View.VISIBLE
            Glide.with(ivAvatar.context)
                .load(photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(ivAvatar)
        } else {
            ivAvatar.visibility = View.GONE
            tvAvatar.visibility = View.VISIBLE
            tvAvatar.text = initial
        }
    }

    @JvmStatic
    fun bindRoleBadge(
        user: User?,
        tvRoleBadge: TextView?
    ) {
        if (user == null || tvRoleBadge == null) return
        val role = user.roleEnum
        tvRoleBadge.text = role.getDisplayName()
        tvRoleBadge.visibility = View.VISIBLE

        val context = tvRoleBadge.context
        val bgRes = when (role) {
            Role.SUPER_ADMIN -> R.color.md_theme_error_container
            Role.DORM_ADMIN -> R.color.md_theme_tertiary_container
            Role.MAINTENANCE_STAFF -> R.color.status_in_progress
            Role.RESIDENT -> R.color.md_theme_primary_container
        }
        val fgRes = when (role) {
            Role.SUPER_ADMIN -> R.color.md_theme_on_error_container
            Role.DORM_ADMIN -> R.color.md_theme_on_tertiary_container
            Role.MAINTENANCE_STAFF -> R.color.md_theme_on_primary
            Role.RESIDENT -> R.color.md_theme_on_primary_container
        }

        val bgColor = ContextCompat.getColor(context, bgRes)
        val fgColor = ContextCompat.getColor(context, fgRes)

        tvRoleBadge.backgroundTintList = ColorStateList.valueOf(bgColor)
        tvRoleBadge.setTextColor(fgColor)
    }
}
