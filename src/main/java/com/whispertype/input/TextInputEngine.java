package com.whispertype.input;

import com.whispertype.config.AppConfig;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * 文字输入模块
 *
 * 模拟键盘输入到当前活动窗口，支持两种模式：
 *   1. 粘贴模式（默认，推荐用于中文）：复制文字到剪贴板，发送 Ctrl+V，
 *      操作前后备份/恢复原剪贴板内容，避免干扰用户。
 *   2. 模拟打字模式（推荐用于英文/数字）：用 Robot 逐字符输入。
 *
 * 自动后处理：去除首尾空格、追加后缀、自动发送回车。
 */
public class TextInputEngine {

    private final Robot robot;
    private final AppConfig config;

    public TextInputEngine(AppConfig config) {
        this.config = config;
        try {
            this.robot = new Robot();
            this.robot.setAutoWaitForIdle(true);
        } catch (Exception e) {
            throw new RuntimeException("无法初始化 Robot: " + e.getMessage(), e);
        }
    }

    /**
     * 输入识别后的文本（含后处理与自动发送）
     *
     * @param rawText 识别得到的原始文本
     */
    public void input(String rawText) {
        // region debug-point input-1
        System.out.println("[DBG][INPUT] input() rawText=" + (rawText == null ? "null" : "\"" + rawText + "\""));
        // endregion
        if (rawText == null || rawText.isEmpty()) {
            return;
        }

        // 1. 文本标准化：去除 CJK 字符间的空格、合并多空格
        String text = normalizeText(rawText);

        // 2. 后处理
        if (config.isTrimWhitespace()) {
            text = text.trim();
        }
        if (text.isEmpty()) {
            return;
        }
        if (config.getTextSuffix() != null && !config.getTextSuffix().isEmpty()) {
            text = text + config.getTextSuffix();
        }
        // region debug-point input-2
        System.out.println("[DBG][INPUT] after post-process: text=\"" + text
                + "\" pasteMode=" + config.isPasteMode() + " autoSend=" + config.isAutoSendMode());
        // endregion

        // 2. 输入到当前焦点窗口
        try {
            if (config.isPasteMode()) {
                pasteText(text);
            } else {
                typeText(text);
            }
            // region debug-point input-3
            System.out.println("[DBG][INPUT] 模拟输入完成");
            // endregion
        } catch (Exception e) {
            // region debug-point input-ex
            System.out.println("[DBG][INPUT] 模拟输入 EXCEPTION: " + e);
            e.printStackTrace();
            // endregion
        }

        // 3. 自动发送模式：补一个回车键
        if (config.isAutoSendMode()) {
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
        }
    }

    /**
     * 粘贴模式：备份剪贴板 -> 写入文本 -> Ctrl+V -> 恢复剪贴板
     */
    private void pasteText(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // region debug-point input-paste-1
        System.out.println("[DBG][INPUT] pasteText() text=\"" + text + "\"");
        // endregion
        // 备份原剪贴板内容
        Transferable backup = null;
        try {
            backup = clipboard.getContents(null);
        } catch (Exception e) {
            // 部分系统在剪贴板为空时可能抛异常，忽略即可
        }

        try {
            clipboard.setContents(new StringSelection(text), null);
            // region debug-point input-paste-2
            System.out.println("[DBG][INPUT] 剪贴板写入完成，等待 30ms 后发送 Ctrl+V");
            // endregion
            // 短暂等待确保剪贴板写入完成
            Thread.sleep(30);
            sendCtrlV();
            // region debug-point input-paste-3
            System.out.println("[DBG][INPUT] Ctrl+V 已发送，等待 80ms");
            // endregion
            // 等待粘贴动作完成再恢复剪贴板
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 恢复原剪贴板内容（仅当原来是字符串或可转移内容时）
            restoreClipboard(clipboard, backup);
        }
    }

    private void restoreClipboard(Clipboard clipboard, Transferable backup) {
        if (backup == null) {
            return;
        }
        try {
            // 仅恢复可识别的内容，避免恢复大块二进制数据引发问题
            if (backup.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                clipboard.setContents(backup, null);
            }
        } catch (Exception e) {
            // 恢复失败不影响主流程
        }
    }

    /**
     * 发送 Ctrl+V 粘贴快捷键
     * 注意：controlKey 仅按一次，避免与连续输入冲突
     */
    private void sendCtrlV() {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    /**
     * 模拟打字模式：用 Robot 逐字符输入
     * 对于含 CJK 字符（中文/日文/韩文）的文本，整段粘贴避免字符间隔
     */
    private void typeText(String text) {
        if (containsCJK(text)) {
            pasteText(text);
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            typeChar(c);
        }
    }

    /**
     * 判断文本是否包含 CJK 字符（中文、日文、韩文）
     */
    private boolean containsCJK(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
            if (c >= 0x3040 && c <= 0x30FF) return true;
            if (c >= 0xAC00 && c <= 0xD7AF) return true;
            if (c >= 0x3400 && c <= 0x4DBF) return true;
            if (c >= 0x20000 && c <= 0x2A6DF) return true;
        }
        return false;
    }

    /**
     * 文本标准化：
     * 1. 去除 CJK 字符之间的空格（中文书写无空格）
     * 2. 将连续多个空格合并为单个空格
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean lastWasSpace = false;
        boolean lastWasCJK = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isSpace = (c == ' ' || c == '\t');
            boolean isCJK = isCJKCharacter(c);

            if (isSpace) {
                if (lastWasCJK) {
                    continue;
                }
                if (!lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
            } else {
                sb.append(c);
                lastWasSpace = false;
                lastWasCJK = isCJK;
            }
        }
        return sb.toString().trim();
    }

    /**
     * 判断字符是否为 CJK（中文/日文/韩文）
     */
    private boolean isCJKCharacter(char c) {
        if (c >= 0x4E00 && c <= 0x9FFF) return true;
        if (c >= 0x3040 && c <= 0x30FF) return true;
        if (c >= 0xAC00 && c <= 0xD7AF) return true;
        if (c >= 0x3400 && c <= 0x4DBF) return true;
        if (c >= 0x20000 && c <= 0x2A6DF) return true;
        return false;
    }

    /**
     * 逐字符模拟按键
     * 仅支持 ASCII 可打印字符，其他字符回退为粘贴单字符
     */
    private void typeChar(char c) {
        if (c <= 31 || c == 127) {
            // 控制字符，仅处理常见换行
            if (c == '\n') {
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
            }
            return;
        }

        // 非 ASCII 字符（如中文）无法用 keyPress 直接输入，回退到粘贴单字符
        if (c > 127) {
            pasteSingleChar(c);
            return;
        }

        // ASCII 可打印字符
        boolean shift = needsShift(c);
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            pasteSingleChar(c);
            return;
        }
        if (shift) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        if (shift) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    /**
     * 判断字符是否需要按 Shift（大写字母与上档符号）
     */
    private boolean needsShift(char c) {
        if (Character.isUpperCase(c)) {
            return true;
        }
        // 上档符号集合
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }

    /**
     * 对单个非 ASCII 字符回退使用剪贴板粘贴
     */
    private void pasteSingleChar(char c) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable backup = null;
        try {
            backup = clipboard.getContents(null);
        } catch (Exception ignored) {
        }
        try {
            clipboard.setContents(new StringSelection(String.valueOf(c)), null);
            Thread.sleep(20);
            sendCtrlV();
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            restoreClipboard(clipboard, backup);
        }
    }
}
