package com.termux.app;

import com.termux.x11.CmdEntryPoint;
import com.termux.x11.LorieApp;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class TermuxX11IntegrationTest {
    @Test
    public void testLorieAppUsesIsolatedPreferencesName() {
        assertEquals("com.termux.x11_preferences", LorieApp.X11_PREFS_NAME);
    }

    @Test
    public void testResolveNativeLibraryPathPrefersExistingSymlinkOrNativeDir() throws Exception {
        File tempLib = File.createTempFile("libXlorie", ".so");
        tempLib.deleteOnExit();
        String resolved = CmdEntryPoint.resolveNativeLibraryPathFromCandidates(
            tempLib.getAbsolutePath(), "/nonexistent/libXlorie.so", null
        );
        assertEquals(tempLib.getAbsolutePath(), resolved);
    }

    @Test
    public void testTermuxX11ScriptDoesNotRequireLoaderApkAndUsesCmdEntryPoint() {
        String script = TermuxInstaller.getTermuxX11ScriptContent();
        assertFalse(script.contains("loader.apk"));
        assertTrue(script.contains("com.termux.x11.CmdEntryPoint"));
        assertTrue(script.contains("/data/data/com.termux/files/usr/libexec/termux-x11/base.apk"));
        assertTrue(script.contains("pm path com.termux"));
    }

    @Test
    public void testTermuxX11PreferenceScriptUsesDirectReceiverMain() {
        String script = TermuxInstaller.getTermuxX11PreferenceScriptContent();
        assertFalse(script.contains("loader.apk"));
        assertTrue(script.contains("com.termux.x11.LoriePreferences\\$Receiver"));
        assertTrue(script.contains("-p\" \"com.termux\""));
    }

    @Test
    public void testTermuxApplicationExtendsLorieApp() {
        assertTrue(LorieApp.class.isAssignableFrom(TermuxApplication.class));
    }
}
