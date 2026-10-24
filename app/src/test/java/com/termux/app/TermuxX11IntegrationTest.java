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
}
