package com.whispertype.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.SwingUtilities;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局热键监听模块
 *
 * 使用 com.github.kwhat:jnativehook 监听全局键盘按下/释放事件。
 * 支持两种触发模式：
 *   1. 长按模式：按下开始录音，松开停止录音（按下/释放成对判定，防止长按重复触发）
 *   2. 点击模式：点击开始录音并进入识别流程，再次点击关闭录音
 *
 * 注意：jnativehook 回调运行在独立分发线程，回调内不要做阻塞操作，
 *       业务逻辑应转交主控制器异步处理。
 */
public class GlobalHotkeyListener implements NativeKeyListener {

    /** 热键事件回调接口 */
    public interface HotkeyCallback {
        /** 热键按下（长按模式下触发开始录音） */
        void onPressed();
        /** 热键释放（长按模式下触发停止录音） */
        void onReleased();
        /** 点击模式下：切换单次录音开关 */
        void onClickToggle();
    }

    private HotkeyCallback callback;
    private int targetKeyCode;
    private int targetModifiers;
    private boolean longPressMode;

    /** 记录当前物理按下的按键，防止系统自动重复触发 KEY_PRESSED */
    private final Set<Integer> pressedKeys = new HashSet<>();
    /** 长按模式下，按下是否已上报（确保按下/释放成对） */
    private final AtomicBoolean pressReported = new AtomicBoolean(false);
    /** 点击模式下，当前是否处于录音态（用于切换） */
    private final AtomicBoolean clickRecording = new AtomicBoolean(false);

    private boolean hooked = false;

    public GlobalHotkeyListener(int keyCode, int modifiers, boolean longPressMode) {
        this.targetKeyCode = keyCode;
        this.targetModifiers = modifiers;
        this.longPressMode = longPressMode;
    }

    /**
     * 更新热键配置（运行时可调用）
     */
    public synchronized void updateHotkey(int keyCode, int modifiers, boolean longPressMode) {
        this.targetKeyCode = keyCode;
        this.targetModifiers = modifiers;
        this.longPressMode = longPressMode;
        // 重置状态
        pressReported.set(false);
        clickRecording.set(false);
        pressedKeys.clear();
    }

    public void setCallback(HotkeyCallback callback) {
        this.callback = callback;
    }

    /**
     * 注册全局钩子并开始监听。
     *
     * Windows 上 jnativehook 使用 SetWindowsHookEx(WH_KEYBOARD_LL)，
     * 该钩子需要注册线程运行消息循环（GetMessage）。这里在 EDT
     * （Swing 事件调度线程，天然有消息循环）上注册，避免只有托盘
     * 菜单弹出时钩子才被调度的问题。
     */
    public synchronized void start() throws NativeHookException {
        if (hooked) {
            return;
        }
        // 关闭 jnativehook 自身的日志输出
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        // 在 EDT 上执行注册（确保线程拥有 Windows 消息循环）
        final NativeHookException[] errBox = new NativeHookException[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable registerTask = () -> {
            try {
                if (!GlobalScreen.isNativeHookRegistered()) {
                    GlobalScreen.registerNativeHook();
                }
                GlobalScreen.addNativeKeyListener(GlobalHotkeyListener.this);
                hooked = true;
            } catch (NativeHookException e) {
                errBox[0] = e;
            } finally {
                latch.countDown();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            registerTask.run();
        } else {
            SwingUtilities.invokeLater(registerTask);
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NativeHookException("等待 EDT 注册钩子被中断");
            }
        }
        if (errBox[0] != null) {
            throw errBox[0];
        }
    }

    /**
     * 停止监听并注销钩子
     */
    public synchronized void stop() {
        if (!hooked) {
            return;
        }
        GlobalScreen.removeNativeKeyListener(this);
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            System.err.println("注销全局钩子失败: " + e.getMessage());
        }
        hooked = false;
    }

    /**
     * 重置点击模式下的录音状态（外部停止录音后调用）
     */
    public void resetClickState() {
        clickRecording.set(false);
    }

    public boolean isClickRecording() {
        return clickRecording.get();
    }

    // ====== NativeKeyListener 回调 ======

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // 自动重复触发判定：若按键已在按下集合中，则忽略后续 PRESSED 事件
        // （jnativehook 对长按会持续发送 PRESSED，需去重）
        int code = e.getKeyCode();
        // region debug-point hkey-1
        System.out.println("[DBG][HKEY] nativeKeyPressed code=" + code
                + " mods=" + e.getModifiers()
                + " | targetKeyCode=" + targetKeyCode
                + " targetModifiers=" + targetModifiers
                + " longPress=" + longPressMode);
        // endregion
        synchronized (pressedKeys) {
            if (pressedKeys.contains(code)) {
                return;
            }
            pressedKeys.add(code);
        }

        if (!matchesHotkey(e)) {
            // region debug-point hkey-2
            System.out.println("[DBG][HKEY] matchesHotkey=false, skip");
            // endregion
            return;
        }
        // region debug-point hkey-3
        System.out.println("[DBG][HKEY] matchesHotkey=true → trigger callback");
        // endregion

        HotkeyCallback cb = this.callback;
        if (cb == null) {
            return;
        }

        if (longPressMode) {
            // 长按模式：按下上报一次（防重复）
            if (pressReported.compareAndSet(false, true)) {
                cb.onPressed();
            }
        } else {
            // 点击模式：每次有效按下切换一次录音状态
            boolean nowRecording = clickRecording.compareAndSet(false, true);
            if (nowRecording) {
                cb.onClickToggle(); // 开始录音
            } else {
                // 已在录音 -> 停止
                clickRecording.set(false);
                cb.onClickToggle(); // 停止录音
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int code = e.getKeyCode();
        synchronized (pressedKeys) {
            pressedKeys.remove(code);
        }

        if (!matchesHotkey(e)) {
            return;
        }
        HotkeyCallback cb = this.callback;
        if (cb == null) {
            return;
        }
        if (longPressMode) {
            // 长按模式：释放上报一次（与按下成对）
            if (pressReported.compareAndSet(true, false)) {
                cb.onReleased();
            }
        }
        // 点击模式下不做处理（点击的开关已在 PRESSED 中完成）
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 不使用 typed 事件
    }

    /**
     * 标准修饰键掩码（仅检查 SHIFT/CTRL/ALT/META）。
     * jnativehook 会把 NUM_LOCK(0x4000)、CAPS_LOCK(0x2000) 等锁定键状态
     * 也填入 getModifiers()，若不屏蔽会导致"无修饰键"单键（如 V、CapsLock）
     * 在锁定键打开时匹配失败。
     */
    private static final int STANDARD_MODIFIERS =
            NativeInputEvent.SHIFT_MASK
          | NativeInputEvent.CTRL_MASK
          | NativeInputEvent.ALT_MASK
          | NativeInputEvent.META_MASK;

    /**
     * 判断事件是否匹配配置的热键（含修饰键）。
     * 仅比较 SHIFT/CTRL/ALT/META 四个标准修饰键，忽略 NUM_LOCK/CAPS_LOCK 等。
     */
    private boolean matchesHotkey(NativeKeyEvent e) {
        if (e.getKeyCode() != targetKeyCode) {
            return false;
        }
        int evMods = e.getModifiers() & STANDARD_MODIFIERS;
        int tgtMods = targetModifiers & STANDARD_MODIFIERS;
        // region debug-point hkey-match
        System.out.println("[DBG][HKEY] matchesHotkey: keyCode=" + e.getKeyCode()
                + " targetKeyCode=" + targetKeyCode
                + " evMods(std)=" + evMods
                + " tgtMods(std)=" + tgtMods);
        // endregion
        if (tgtMods == 0) {
            // 无修饰键要求：允许任何修饰键状态（更宽松，便于单键触发）
            return true;
        }
        return (evMods & tgtMods) == tgtMods;
    }
}
