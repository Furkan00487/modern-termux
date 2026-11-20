package com.termux.terminal;

import java.lang.reflect.Field;

public class UpstreamTerminalFeaturesTest extends TerminalTestCase {

    public void testKeyHandlerPageUpAndPageDownTermcap() {
        assertEquals("\033[5~", KeyHandler.getCodeFromTermcap("kP", false, false));
        assertEquals("\033[6~", KeyHandler.getCodeFromTermcap("kN", false, false));
    }

    public void testTerminalSessionUses64KbBuffers() throws Exception {
        TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);

        Field queueField = TerminalSession.class.getDeclaredField("mProcessToTerminalIOQueue");
        queueField.setAccessible(true);
        ByteQueue queue = (ByteQueue) queueField.get(session);
        Field bufferField = ByteQueue.class.getDeclaredField("mBuffer");
        bufferField.setAccessible(true);
        byte[] queueBuffer = (byte[]) bufferField.get(queue);
        assertEquals(64 * 1024, queueBuffer.length);

        Field handlerField = TerminalSession.class.getDeclaredField("mMainThreadHandler");
        handlerField.setAccessible(true);
        Object handler = handlerField.get(session);
        Field recvField = handler.getClass().getDeclaredField("mReceiveBuffer");
        recvField.setAccessible(true);
        byte[] recvBuffer = (byte[]) recvField.get(handler);
        assertEquals(64 * 1024, recvBuffer.length);
    }

    public void testTerminalEmulatorCachesTopRow() {
        withTerminalSized(80, 24);
        assertEquals(0, mTerminal.getTopRow());
        mTerminal.setTopRow(-15);
        assertEquals(-15, mTerminal.getTopRow());
    }

    public void testPrimaryDeviceAttributesAdvertisesSixel() {
        withTerminalSized(80, 24);
        assertEnteringStringGivesResponse("\033[c", "\033[?64;1;2;4;6;9;15;18;21;22c");
    }

    public void testTextStyleTerminalBitmapEncoding() {
        long encoded = TextStyle.encodeTerminalBitmap(1025, 3, 2);
        assertTrue(TextStyle.isTerminalBitmap(encoded));
        assertEquals(1025, TextStyle.getTerminalBitmapNum(encoded));
        assertEquals(3, TextStyle.getTerminalBitmapX(encoded));
        assertEquals(2, TextStyle.getTerminalBitmapY(encoded));
    }

    public void testTerminalBufferBlockSetClearsLineWrap() {
        withTerminalSized(4, 3);
        enterString("abcde");
        assertTrue(mTerminal.getScreen().getLineWrap(0));
        mTerminal.getScreen().blockSet(0, 0, 4, 1, ' ', TextStyle.NORMAL);
        assertFalse(mTerminal.getScreen().getLineWrap(0));
    }

    public void testTerminalSixelAndITermImageConstants() {
        assertEquals(8192, TerminalSixel.SIXEL__MAX_REPEAT);
        assertEquals(0, ITermImage.ImageState.INIT.getValue());
        assertTrue(TerminalBitmap.MAX_BITMAP_SIZE > 0);
    }
}
