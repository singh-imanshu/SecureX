package com.himanshu.securex.services;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.W32APIOptions;

/**
 * A service for securely copying text to the system clipboard.
 * This implementation uses JNA to interact with the native Windows clipboard API
 * to prevent the copied content from being stored in the clipboard history.
 * For non-Windows operating systems, it falls back to the standard JavaFX clipboard.
 * This difference in operation based on OS is because Windows does not
 * allow an app to programmatically clear the clipboard. cz
 */
public class ClipboardService {

    // --- JNA Interface Definitions for native Windows API calls ---

    private interface ExtendedUser32 extends User32 {
        ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean OpenClipboard(HWND hWndNewOwner);
        boolean EmptyClipboard();
        Pointer SetClipboardData(int uFormat, Pointer hMem);
        boolean CloseClipboard();
        int RegisterClipboardFormat(String lpszFormat);
    }

    private interface ExtendedKernel32 extends Kernel32 {
        ExtendedKernel32 INSTANCE = Native.load("kernel32", ExtendedKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GlobalAlloc(int uFlags, int dwBytes);
        Pointer GlobalLock(Pointer hMem);
        boolean GlobalUnlock(Pointer hMem);
        Pointer GlobalFree(Pointer hMem);
    }

    // --- Native Windows Constants ---

    private static final int CF_UNICODETEXT = 13;
    private static final int GMEM_MOVEABLE = 0x0002;
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    /**
     * Copies the given text to the system clipboard. On Windows, it uses a special
     * format to prevent the text from being saved to the clipboard history.
     *
     * @param text The text to be copied.
     */
    public static void copyToClipboard(String text) {
        if (OS_NAME.contains("win")) {
            copyToClipboardWindows(text);
        } else {
            // Fallback for macOS, Linux, etc.
            copyToClipboardStandard(text);
        }
    }

    /**
     * Standard, cross-platform clipboard copy using JavaFX.
     */
    private static void copyToClipboardStandard(String text) {
        javafx.scene.input.Clipboard fxClipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        fxClipboard.setContent(content);
    }

    /**
     * Advanced clipboard copy for Windows using JNA to prevent history storage.
     */
    private static void copyToClipboardWindows(String text) {
        ExtendedUser32 user32 = ExtendedUser32.INSTANCE;
        ExtendedKernel32 kernel32 = ExtendedKernel32.INSTANCE;

        if (!user32.OpenClipboard(null)) {
            System.err.println("Error: Could not open clipboard.");
            return;
        }

        try {
            user32.EmptyClipboard();

            // Register the special format that tells history monitors to ignore this content
            int excludeFormat = user32.RegisterClipboardFormat("ExcludeClipboardContentFromMonitorProcessing");
            if (excludeFormat != 0) {
                user32.SetClipboardData(excludeFormat, null);
            }

            // Prepare the text data for the native API
            String textWithNull = text + "\0";
            byte[] bytes = textWithNull.getBytes("UTF-16LE");

            Pointer hGlobal = kernel32.GlobalAlloc(GMEM_MOVEABLE, bytes.length);
            if (hGlobal == null) {
                System.err.println("Error: Could not allocate global memory for clipboard.");
                return;
            }

            Pointer pointer = kernel32.GlobalLock(hGlobal);
            if (pointer != null) {
                try {
                    pointer.write(0, bytes, 0, bytes.length);
                } finally {
                    kernel32.GlobalUnlock(hGlobal);
                }

                // Set the clipboard data. If this fails, free the memory.
                Pointer result = user32.SetClipboardData(CF_UNICODETEXT, hGlobal);
                if (result == null) {
                    System.err.println("Error: Failed to set clipboard data.");
                    kernel32.GlobalFree(hGlobal);
                }
            } else {
                System.err.println("Error: Failed to lock memory for clipboard.");
                kernel32.GlobalFree(hGlobal);
            }
        } catch (Exception e) {
            System.err.println("An exception occurred during the native clipboard operation.");
            e.printStackTrace();
        } finally {
            user32.CloseClipboard();
        }
    }
}