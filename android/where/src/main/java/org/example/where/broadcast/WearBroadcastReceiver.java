package org.example.where.broadcast;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.example.where.service.WhereService;

public class WearBroadcastReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Explicitly specify that WhereService will handle the intent.
        ComponentName comp = new ComponentName(context.getPackageName(),
                WhereService.class.getName());
        startWakefulService(context, intent.setComponent(comp));
    }
}
