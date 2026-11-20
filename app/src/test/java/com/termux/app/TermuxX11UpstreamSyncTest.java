package com.termux.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.termux.x11.LorieApp;
import com.termux.x11.Prefs;
import com.termux.x11.SurfaceHandoffState;
import com.termux.x11.input.InputEventSender;
import com.termux.x11.input.InputStub;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class TermuxX11UpstreamSyncTest {

    private static final InputStub NO_OP_STUB = new InputStub() {
        @Override public void sendMouseEvent(float x, float y, int whichButton, boolean buttonDown, boolean relative) {}
        @Override public void sendMouseWheelEvent(float deltaX, float deltaY) {}
        @Override public boolean sendKeyEvent(int scanCode, int keyCode, boolean keyDown) { return true; }
        @Override public void sendTextEvent(byte[] utf8Bytes) {}
        @Override public void sendTouchEvent(int action, int pointerId, int x, int y) {}
        @Override public void sendStylusEvent(float x, float y, int pressure, int tiltX, int tiltY, int orientation, int buttons, boolean eraser, boolean mouseMode) {}
        @Override public void sendLockKeysState(int state) {}
    };

    @Test
    public void testInvertScrollingPreferenceAndStringsExist() {
        Context ctx = RuntimeEnvironment.getApplication();
        String title = ctx.getString(com.termux.x11.R.string.lorie_pref_invertScrolling);
        String summary = ctx.getString(com.termux.x11.R.string.lorie_pref_invertScrolling_summary);
        assertFalse(title.isEmpty());
        assertFalse(summary.isEmpty());

        Prefs prefs = new Prefs();
        prefs.attach(ctx, ctx.getSharedPreferences(LorieApp.X11_PREFS_NAME, Context.MODE_PRIVATE));
        assertFalse(prefs.invertScrolling.get());
        assertTrue(prefs.keys.containsKey("invertScrolling"));
        prefs.invertScrolling.put(true);
        assertTrue(prefs.invertScrolling.get());
    }

    @Test
    public void testInputEventSenderSupportsInvertScrollingFlag() {
        InputEventSender sender = new InputEventSender(null, NO_OP_STUB);
        assertFalse(sender.invertScrolling);
        sender.invertScrolling = true;
        assertTrue(sender.invertScrolling);
    }

    @Test
    public void testSurfaceHandoffStateOneShotFlushOnHeartbeat() {
        SurfaceHandoffState state = new SurfaceHandoffState();
        assertFalse(state.isSurfacePending());

        assertFalse(state.markPendingIfStalled(false));
        assertFalse(state.isSurfacePending());

        assertTrue(state.markPendingIfStalled(true));
        assertTrue(state.isSurfacePending());

        assertTrue(state.consumePendingOnHeartbeat());
        assertFalse(state.isSurfacePending());
        assertFalse(state.consumePendingOnHeartbeat());
    }

    @Test
    public void testMainActivityPreservesHeartbeatFlushAndScreenIdleWithoutWatchdog() throws Exception {
        Class<?> mainActivityClass = Class.forName(
            "com.termux.x11.MainActivity", false, TermuxX11UpstreamSyncTest.class.getClassLoader()
        );

        Set<String> fieldNames = new HashSet<>();
        for (Field f : mainActivityClass.getDeclaredFields()) {
            fieldNames.add(f.getName());
        }
        assertFalse("serverWatchdog polling loop must remain removed", fieldNames.contains("serverWatchdog"));
        assertTrue(fieldNames.contains("lastServerHeartbeat"));
        assertTrue(fieldNames.contains("surfacePending"));

        Set<String> methodNames = new HashSet<>();
        for (Method m : mainActivityClass.getDeclaredMethods()) {
            methodNames.add(m.getName());
        }
        assertFalse("checkServerHeartbeat polling method must remain removed", methodNames.contains("checkServerHeartbeat"));
        assertTrue(methodNames.contains("onServerHeartbeat"));
        assertTrue(methodNames.contains("setX11SleepInhibited"));
        assertTrue(methodNames.contains("onX11ScreenSaverReset"));
    }

    @Test
    public void testManifestOmitsBindAccessibilityService() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        PackageInfo info = ctx.getPackageManager().getPackageInfo(
            ctx.getPackageName(), PackageManager.GET_PERMISSIONS
        );
        if (info.requestedPermissions != null) {
            assertFalse(
                "BIND_ACCESSIBILITY_SERVICE must remain omitted",
                Arrays.asList(info.requestedPermissions).contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
            );
        }
    }
}
