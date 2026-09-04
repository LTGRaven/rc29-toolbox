package com.rc29.toolbox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class PlayStoreActivity extends Activity {
    static boolean available(Context context) {
        try { return context.getPackageManager().getLaunchIntentForPackage("com.android.vending") != null; }
        catch (RuntimeException unavailable) { return false; }
    }
    static boolean open(Context context) {
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if (launch == null) {
                Toast.makeText(context, "Google Play is missing or disabled on this device.", Toast.LENGTH_LONG).show();
                return false;
            }
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(context, "Google Play could not be opened.", Toast.LENGTH_LONG).show();
            return false;
        }
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        open(this);
        finish();
    }
}
