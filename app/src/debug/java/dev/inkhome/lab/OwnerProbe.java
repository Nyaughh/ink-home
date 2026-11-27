package dev.inkhome.lab;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

/** Passive admin. Probe calls require an attached debugger in the owner process. */
public final class OwnerProbe extends DeviceAdminReceiver {
    private static DevicePolicyManager policy(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (!manager.isDeviceOwnerApp(context.getPackageName())) {
            throw new IllegalStateException("Probe requires the debug app to be device owner");
        }
        return manager;
    }

    private static ComponentName admin(Context context) {
        return new ComponentName(context, OwnerProbe.class);
    }

    public static boolean statusBar(Context context, boolean disabled) {
        return policy(context).setStatusBarDisabled(admin(context), disabled);
    }

    public static int lockFeatures(Context context) {
        return policy(context).getLockTaskFeatures(admin(context));
    }

    public static void lockFeatures(Context context, int flags) {
        policy(context).setLockTaskFeatures(admin(context), flags);
    }

    public static boolean hideFixture(Context context, boolean hidden) {
        return policy(context).setApplicationHidden(admin(context), "dev.inkhome.probefixture", hidden);
    }
}
