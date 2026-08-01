package com.whispertype.hotkey;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Java AWT 键码（KeyEvent.VK_*）与 jnativehook 键码（NativeKeyEvent.VC_*）双向映射。
 *
 * 背景：jnativehook 使用独立的虚拟键码体系（基于 Linux evdev），
 * 与 Java AWT 的 KeyEvent.VK_* 完全不同。例如：
 *   - Caps Lock: jnativehook VC_CAPS_LOCK=58  vs  Java VK_CAPS_LOCK=20
 *   - V 键:      jnativehook VC_V=47          vs  Java VK_V=86
 *
 * 本项目策略：配置文件中统一存储 jnativehook 键码（与运行时监听一致），
 * 设置界面用 Swing KeyEvent 捕获后通过本类转换为 jnativehook 键码。
 *
 * 修饰键掩码同样不一致：
 *   - Java AWT:    SHIFT_MASK=1, CTRL_MASK=2, ALT_MASK=8,  META_MASK=4
 *   - jnativehook: SHIFT_MASK=1, CTRL_MASK=2, ALT_MASK=4,  META_MASK=8
 */
public final class KeyCodeMapper {

    private KeyCodeMapper() {}

    /** Java VK_* → jnativehook VC_* */
    private static final Map<Integer, Integer> JAVA_TO_NATIVE = new HashMap<>();
    /** jnativehook VC_* → Java VK_*（用于反向查找键名等） */
    private static final Map<Integer, Integer> NATIVE_TO_JAVA = new HashMap<>();

    static {
        // 字母键 A-Z
        put(KeyEvent.VK_A, NativeKeyEvent.VC_A);
        put(KeyEvent.VK_B, NativeKeyEvent.VC_B);
        put(KeyEvent.VK_C, NativeKeyEvent.VC_C);
        put(KeyEvent.VK_D, NativeKeyEvent.VC_D);
        put(KeyEvent.VK_E, NativeKeyEvent.VC_E);
        put(KeyEvent.VK_F, NativeKeyEvent.VC_F);
        put(KeyEvent.VK_G, NativeKeyEvent.VC_G);
        put(KeyEvent.VK_H, NativeKeyEvent.VC_H);
        put(KeyEvent.VK_I, NativeKeyEvent.VC_I);
        put(KeyEvent.VK_J, NativeKeyEvent.VC_J);
        put(KeyEvent.VK_K, NativeKeyEvent.VC_K);
        put(KeyEvent.VK_L, NativeKeyEvent.VC_L);
        put(KeyEvent.VK_M, NativeKeyEvent.VC_M);
        put(KeyEvent.VK_N, NativeKeyEvent.VC_N);
        put(KeyEvent.VK_O, NativeKeyEvent.VC_O);
        put(KeyEvent.VK_P, NativeKeyEvent.VC_P);
        put(KeyEvent.VK_Q, NativeKeyEvent.VC_Q);
        put(KeyEvent.VK_R, NativeKeyEvent.VC_R);
        put(KeyEvent.VK_S, NativeKeyEvent.VC_S);
        put(KeyEvent.VK_T, NativeKeyEvent.VC_T);
        put(KeyEvent.VK_U, NativeKeyEvent.VC_U);
        put(KeyEvent.VK_V, NativeKeyEvent.VC_V);
        put(KeyEvent.VK_W, NativeKeyEvent.VC_W);
        put(KeyEvent.VK_X, NativeKeyEvent.VC_X);
        put(KeyEvent.VK_Y, NativeKeyEvent.VC_Y);
        put(KeyEvent.VK_Z, NativeKeyEvent.VC_Z);

        // 数字键 0-9（顶排）
        put(KeyEvent.VK_0, NativeKeyEvent.VC_0);
        put(KeyEvent.VK_1, NativeKeyEvent.VC_1);
        put(KeyEvent.VK_2, NativeKeyEvent.VC_2);
        put(KeyEvent.VK_3, NativeKeyEvent.VC_3);
        put(KeyEvent.VK_4, NativeKeyEvent.VC_4);
        put(KeyEvent.VK_5, NativeKeyEvent.VC_5);
        put(KeyEvent.VK_6, NativeKeyEvent.VC_6);
        put(KeyEvent.VK_7, NativeKeyEvent.VC_7);
        put(KeyEvent.VK_8, NativeKeyEvent.VC_8);
        put(KeyEvent.VK_9, NativeKeyEvent.VC_9);

        // 功能键 F1-F12
        put(KeyEvent.VK_F1, NativeKeyEvent.VC_F1);
        put(KeyEvent.VK_F2, NativeKeyEvent.VC_F2);
        put(KeyEvent.VK_F3, NativeKeyEvent.VC_F3);
        put(KeyEvent.VK_F4, NativeKeyEvent.VC_F4);
        put(KeyEvent.VK_F5, NativeKeyEvent.VC_F5);
        put(KeyEvent.VK_F6, NativeKeyEvent.VC_F6);
        put(KeyEvent.VK_F7, NativeKeyEvent.VC_F7);
        put(KeyEvent.VK_F8, NativeKeyEvent.VC_F8);
        put(KeyEvent.VK_F9, NativeKeyEvent.VC_F9);
        put(KeyEvent.VK_F10, NativeKeyEvent.VC_F10);
        put(KeyEvent.VK_F11, NativeKeyEvent.VC_F11);
        put(KeyEvent.VK_F12, NativeKeyEvent.VC_F12);

        // 特殊键
        put(KeyEvent.VK_CAPS_LOCK, NativeKeyEvent.VC_CAPS_LOCK);
        put(KeyEvent.VK_TAB, NativeKeyEvent.VC_TAB);
        put(KeyEvent.VK_ENTER, NativeKeyEvent.VC_ENTER);
        put(KeyEvent.VK_SPACE, NativeKeyEvent.VC_SPACE);
        put(KeyEvent.VK_BACK_SPACE, NativeKeyEvent.VC_BACKSPACE);
        put(KeyEvent.VK_ESCAPE, NativeKeyEvent.VC_ESCAPE);
        put(KeyEvent.VK_INSERT, NativeKeyEvent.VC_INSERT);
        put(KeyEvent.VK_DELETE, NativeKeyEvent.VC_DELETE);
        put(KeyEvent.VK_HOME, NativeKeyEvent.VC_HOME);
        put(KeyEvent.VK_END, NativeKeyEvent.VC_END);
        put(KeyEvent.VK_PAGE_UP, NativeKeyEvent.VC_PAGE_UP);
        put(KeyEvent.VK_PAGE_DOWN, NativeKeyEvent.VC_PAGE_DOWN);

        // 方向键
        put(KeyEvent.VK_UP, NativeKeyEvent.VC_UP);
        put(KeyEvent.VK_DOWN, NativeKeyEvent.VC_DOWN);
        put(KeyEvent.VK_LEFT, NativeKeyEvent.VC_LEFT);
        put(KeyEvent.VK_RIGHT, NativeKeyEvent.VC_RIGHT);

        // 修饰键
        put(KeyEvent.VK_SHIFT, NativeKeyEvent.VC_SHIFT);
        put(KeyEvent.VK_CONTROL, NativeKeyEvent.VC_CONTROL);
        put(KeyEvent.VK_ALT, NativeKeyEvent.VC_ALT);
        put(KeyEvent.VK_META, NativeKeyEvent.VC_META);
    }

    private static void put(int javaCode, int nativeCode) {
        JAVA_TO_NATIVE.put(javaCode, nativeCode);
        NATIVE_TO_JAVA.put(nativeCode, javaCode);
    }

    /**
     * Java KeyEvent.VK_* → jnativehook VC_*
     * @return 对应的 jnativehook 键码；若无映射则返回原值（兜底）
     */
    public static int toNativeKeyCode(int javaKeyCode) {
        return JAVA_TO_NATIVE.getOrDefault(javaKeyCode, javaKeyCode);
    }

    /**
     * jnativehook VC_* → Java KeyEvent.VK_*
     * @return 对应的 Java 键码；若无映射则返回原值
     */
    public static int toJavaKeyCode(int nativeKeyCode) {
        return NATIVE_TO_JAVA.getOrDefault(nativeKeyCode, nativeKeyCode);
    }

    /**
     * Java AWT 修饰键掩码（InputEvent.*_MASK）→ jnativehook 修饰键掩码（NativeInputEvent.*_MASK）
     *
     * Java:    SHIFT=1, CTRL=2, ALT=8, META=4
     * jnative: SHIFT=1, CTRL=2, ALT=4, META=8
     */
    public static int toNativeModifiers(int javaModifiers) {
        int nativeMods = 0;
        if ((javaModifiers & InputEvent.SHIFT_MASK) != 0) {
            nativeMods |= NativeInputEvent.SHIFT_MASK;
        }
        if ((javaModifiers & InputEvent.CTRL_MASK) != 0) {
            nativeMods |= NativeInputEvent.CTRL_MASK;
        }
        if ((javaModifiers & InputEvent.ALT_MASK) != 0) {
            nativeMods |= NativeInputEvent.ALT_MASK;
        }
        if ((javaModifiers & InputEvent.META_MASK) != 0) {
            nativeMods |= NativeInputEvent.META_MASK;
        }
        return nativeMods;
    }

    /**
     * jnativehook 修饰键掩码（NativeInputEvent.*_MASK）→ Java AWT 修饰键掩码（InputEvent.*_MASK）
     */
    public static int toJavaModifiers(int nativeModifiers) {
        int javaMods = 0;
        if ((nativeModifiers & NativeInputEvent.SHIFT_MASK) != 0) {
            javaMods |= InputEvent.SHIFT_MASK;
        }
        if ((nativeModifiers & NativeInputEvent.CTRL_MASK) != 0) {
            javaMods |= InputEvent.CTRL_MASK;
        }
        if ((nativeModifiers & NativeInputEvent.ALT_MASK) != 0) {
            javaMods |= InputEvent.ALT_MASK;
        }
        if ((nativeModifiers & NativeInputEvent.META_MASK) != 0) {
            javaMods |= InputEvent.META_MASK;
        }
        return javaMods;
    }

    /**
     * 获取 jnativehook 键码对应的可读键名（用于 UI 显示）。
     * 优先使用 jnativehook 自带的键名，回退到 Java 键名。
     */
    public static String getNativeKeyText(int nativeKeyCode) {
        try {
            String text = NativeKeyEvent.getKeyText(nativeKeyCode);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        } catch (Exception ignored) {}
        Integer javaCode = NATIVE_TO_JAVA.get(nativeKeyCode);
        if (javaCode != null) {
            return KeyEvent.getKeyText(javaCode);
        }
        return "Key(" + nativeKeyCode + ")";
    }
}
