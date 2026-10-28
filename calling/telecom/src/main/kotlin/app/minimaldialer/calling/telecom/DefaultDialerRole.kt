package app.minimaldialer.calling.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** ROLE_DIALER is what lets Telecom bind our InCallService and grants call-log access. */
object DefaultDialerRole {
    fun isHeld(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_DIALER) && rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    /** Intent for the system role dialog, or null when the role doesn't exist on this device. */
    fun requestIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }
}
