package com.mtwabpin.kolahal.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

/**
 * The two test widgets' setup screen. It answers OK at once, for the widget it was opened for, and
 * tells the tests which widget that was ({@link #OPENED}).
 *
 * <p>It is written in Java because it runs in the test APK's own process, which has no Kotlin
 * library: the tests' process is Kotlin's own. A static set here wouldn't reach the tests either,
 * so it sends Kolahal's package a broadcast.
 */
public class TestWidgetSetup extends Activity {
    /** The broadcast it sends as it opens, with the widget's id in {@link AppWidgetManager#EXTRA_APPWIDGET_ID}. */
    public static final String OPENED = "com.mtwabpin.kolahal.test.WIDGET_SETUP_OPENED";

    private static final String TEST_SUFFIX = ".test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String own = getPackageName();
        String kolahal = own.endsWith(TEST_SUFFIX) ? own.substring(0, own.length() - TEST_SUFFIX.length()) : own;
        sendBroadcast(new Intent(OPENED).setPackage(kolahal).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        finish();
    }
}
