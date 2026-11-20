package com.termux.app;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE;
import com.termux.shared.termux.plugins.TermuxPluginUtils;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TextStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class UpstreamSyncAppFixesTest {

    // 1x1 red RGBA PNG
    private static final String TINY_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==";

    private static class RecordingTerminalOutput extends TerminalOutput {
        final List<String> clipboardTexts = new ArrayList<>();

        @Override
        public void write(byte[] data, int offset, int count) {
        }

        @Override
        public void titleChanged(String oldTitle, String newTitle) {
        }

        @Override
        public void onCopyTextToClipboard(String text) {
            clipboardTexts.add(text);
        }

        @Override
        public void onPasteTextFromClipboard() {
        }

        @Override
        public void onBell() {
        }

        @Override
        public void onColorsChanged() {
        }
    }

    @Test
    public void testRunCommandServiceChecksAllowExternalAppsBeforeFileResultConfig() {
        File resultDir = new File(Environment.getExternalStorageDirectory(), "run_cmd_policy_test_" + System.nanoTime());
        File leakedFile = new File(resultDir, "leaked.txt");
        try {
            assertNotNull(
                "allow-external-apps should be violated in default test environment",
                TermuxPluginUtils.checkIfAllowExternalAppsPolicyIsViolated(RuntimeEnvironment.getApplication(), "RunCommandService")
            );

            Intent intent = new Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/echo");
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_DIRECTORY, resultDir.getAbsolutePath());
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_SINGLE_FILE, true);
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_BASENAME, "leaked.txt");
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, "allow-external-apps=true");

            RunCommandService service = Robolectric.buildService(RunCommandService.class).create().get();
            service.onStartCommand(intent, 0, 1);

            assertFalse("Result file must not be written when allow-external-apps is false", leakedFile.exists());
        } finally {
            if (leakedFile.exists()) leakedFile.delete();
            if (resultDir.exists()) resultDir.delete();
        }
    }

    @Test
    public void testTermuxOpenContentProviderGetTypeHandlesNullLastPathSegment() {
        TermuxOpenReceiver.ContentProvider provider = new TermuxOpenReceiver.ContentProvider();
        assertNull(provider.getType(Uri.parse("content://com.termux.files/")));
        assertNull(provider.getType(Uri.parse("content://com.termux.files/home/noextension")));
    }

    @Test
    public void testNotificationUtilsSkipsBigTextStyleWhenBigTextIsNull() {
        Notification.Builder builder = NotificationUtils.geNotificationBuilder(
            RuntimeEnvironment.getApplication(),
            "test_channel",
            Notification.PRIORITY_LOW,
            "Title",
            "Text",
            null,
            null,
            null,
            NotificationUtils.NOTIFICATION_MODE_SILENT
        );
        assertNotNull(builder);
        Notification notification = builder.build();
        assertFalse(
            "BigTextStyle must not be applied when notificationBigText is null",
            Notification.BigTextStyle.class.getName().equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
        );
    }

    @Test
    public void testTerminalEmulatorOsc52SupportsLargePayloadUpTo100Kb() {
        RecordingTerminalOutput output = new RecordingTerminalOutput();
        TerminalEmulator emulator = new TerminalEmulator(output, 80, 24, 13, 15, 200, null);

        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 32 * 1024; i++) {
            raw.append('A');
        }
        String payload = raw.toString();
        String b64 = android.util.Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        byte[] seq = ("\033]52;c;" + b64 + "\007").getBytes(StandardCharsets.UTF_8);
        emulator.append(seq, seq.length);

        assertEquals(1, output.clipboardTexts.size());
        assertEquals(payload, output.clipboardTexts.get(0));
    }

    @Test
    public void testTerminalEmulatorProcessesSixelAndIterm2Bitmaps() {
        RecordingTerminalOutput output = new RecordingTerminalOutput();
        TerminalEmulator emulator = new TerminalEmulator(output, 80, 24, 13, 15, 200, null);

        byte[] sixel = "\033Pq#0;2;0;0;0#1;2;100;100;0#1~~@@vv@@~~@@~~\033\\".getBytes(StandardCharsets.UTF_8);
        emulator.append(sixel, sixel.length);

        TerminalBuffer screen = emulator.getScreen();
        long sixelStyle = screen.getStyleAt(0, 0);
        assertTrue("Sixel sequence should encode a TerminalBitmap cell", TextStyle.isTerminalBitmap(sixelStyle));
        assertNotNull("Sixel TerminalBitmap should be stored in TerminalBuffer", screen.getTerminalBitmap(sixelStyle));

        byte[] newline = "\r\n".getBytes(StandardCharsets.UTF_8);
        emulator.append(newline, newline.length);

        int itermRow = emulator.getCursorRow();
        byte[] iterm = ("\033]1337;File=inline=1;width=13px;height=15px:" + TINY_PNG_BASE64 + "\033\\").getBytes(StandardCharsets.UTF_8);
        emulator.append(iterm, iterm.length);

        long itermStyle = screen.getStyleAt(itermRow, 0);
        assertTrue("OSC 1337 sequence should encode a TerminalBitmap cell", TextStyle.isTerminalBitmap(itermStyle));
        assertNotNull("OSC 1337 TerminalBitmap should be stored in TerminalBuffer", screen.getTerminalBitmap(itermStyle));
    }
}
