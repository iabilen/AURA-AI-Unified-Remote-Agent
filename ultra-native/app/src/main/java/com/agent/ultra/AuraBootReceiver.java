package com.agent.ultra;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;

/** Restarts the persistent AURA background lifecycle after boot or app replacement. */
public final class AuraBootReceiver extends BroadcastReceiver {
    private static final String TAG = "AuraBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Intent service = new Intent(context.getApplicationContext(), AgentBackgroundService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, service);
            } else {
                context.startService(service);
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "AURA background restart deferred by Android", e);
        }
    }
}
