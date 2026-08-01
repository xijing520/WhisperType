package com.whispertype.tray;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * 系统托盘管理模块。
 *
 * 使用 Swing JPopupMenu（FlatLaf 渲染）替代 AWT PopupMenu，
 * 彻底解决 Windows 下原生菜单中文乱码问题，同时获得现代化外观。
 *
 * 状态：待机(绿) / 录音中(红) / 识别中(蓝) / 出错(黄) / 已禁用(灰)
 * 右键菜单：启用/禁用、设置、切换模型、查看历史、退出
 * 左键单击：切换启用状态
 */
public class TrayManager {

    public enum State {
        IDLE, RECORDING, RECOGNIZING, ERROR, DISABLED
    }

    public interface TrayCallback {
        void onToggleEnabled();
        void onOpenSettings();
        void onSwitchModel();
        void onViewHistory();
        void onExit();
    }

    private TrayIcon trayIcon;
    private TrayCallback callback;
    private State currentState = State.IDLE;
    private boolean enabled = true;

    /** Swing 弹出菜单（FlatLaf 渲染，中文无乱码） */
    private JPopupMenu popupMenu;
    private JMenuItem toggleListenItem;
    private JDialog popupInvoker;

    public void setCallback(TrayCallback callback) {
        this.callback = callback;
    }

    public void init() throws AWTException {
        if (!SystemTray.isSupported()) {
            throw new AWTException("当前系统不支持托盘图标");
        }

        buildPopupMenu();

        // 创建托盘图标（不传 AWT PopupMenu，改用 Swing 右键弹出）
        trayIcon = new TrayIcon(createIcon(State.IDLE), "WhisperType 语音输入", null);
        trayIcon.setImageAutoSize(true);

        // 左键单击切换启用状态
        trayIcon.addActionListener(e -> {
            if (callback != null) {
                callback.onToggleEnabled();
            }
        });

        // 右键弹出 Swing 菜单（解决 AWT PopupMenu 中文乱码）
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    SwingUtilities.invokeLater(() -> showPopupAt(e.getX(), e.getY()));
                }
            }
        });

        SystemTray.getSystemTray().add(trayIcon);
    }

    /** 构建 Swing 右键菜单 */
    private void buildPopupMenu() {
        // 确保 FlatLaf 已初始化（菜单样式依赖它）
        try {
            if (!(UIManager.getLookAndFeel() instanceof FlatLightLaf)) {
                FlatLightLaf.setup();
            }
        } catch (Exception ignored) { }

        popupMenu = new JPopupMenu();
        popupMenu.setLightWeightPopupEnabled(true);
        // 莫兰迪风格菜单底色
        popupMenu.setBackground(new Color(248, 248, 252));
        popupMenu.setBorderPainted(true);

        Font menuFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

        toggleListenItem = new JMenuItem("停止监听");
        toggleListenItem.setFont(menuFont);
        toggleListenItem.addActionListener(e -> {
            if (callback != null) callback.onToggleEnabled();
        });
        popupMenu.add(toggleListenItem);

        popupMenu.add(new JSeparator());

        JMenuItem settingsItem = new JMenuItem("设置");
        settingsItem.setFont(menuFont);
        settingsItem.addActionListener(e -> {
            if (callback != null) callback.onOpenSettings();
        });
        popupMenu.add(settingsItem);

        JMenuItem switchModelItem = new JMenuItem("切换模型");
        switchModelItem.setFont(menuFont);
        switchModelItem.addActionListener(e -> {
            if (callback != null) callback.onSwitchModel();
        });
        popupMenu.add(switchModelItem);

        JMenuItem historyItem = new JMenuItem("查看识别历史");
        historyItem.setFont(menuFont);
        historyItem.addActionListener(e -> {
            if (callback != null) callback.onViewHistory();
        });
        popupMenu.add(historyItem);

        popupMenu.add(new JSeparator());

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setFont(menuFont);
        exitItem.addActionListener(e -> {
            if (callback != null) callback.onExit();
        });
        popupMenu.add(exitItem);
    }

    /** 在屏幕坐标处显示 Swing 弹出菜单 */
    private void showPopupAt(int x, int y) {
        if (popupInvoker == null) {
            popupInvoker = new JDialog();
            popupInvoker.setUndecorated(true);
            popupInvoker.setType(Window.Type.UTILITY);
            popupInvoker.setAlwaysOnTop(true);
            popupInvoker.setSize(new Dimension(0, 0));
        }
        // 隐藏后重新定位再显示，确保位置正确
        popupInvoker.setVisible(false);
        popupInvoker.setLocation(x, y);
        popupInvoker.setVisible(true);
        popupInvoker.toFront();
        popupMenu.show(popupInvoker, 0, 0);
    }

    public void setState(State state) {
        this.currentState = state;
        if (trayIcon != null) {
            trayIcon.setImage(createIcon(state));
            trayIcon.setToolTip(getTooltip(state));
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (toggleListenItem != null) {
            toggleListenItem.setText(enabled ? "停止监听" : "开始监听");
        }
        if (trayIcon != null) {
            if (!enabled) {
                setState(State.DISABLED);
            } else if (currentState == State.DISABLED) {
                setState(State.IDLE);
            }
        }
    }

    public void showMessage(String caption, String text, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, type);
        }
    }

    private String getTooltip(State state) {
        switch (state) {
            case IDLE: return "WhisperType 语音输入 - 待机";
            case RECORDING: return "WhisperType 语音输入 - 录音中";
            case RECOGNIZING: return "WhisperType 语音输入 - 识别中";
            case ERROR: return "WhisperType 语音输入 - 出错";
            case DISABLED: return "WhisperType 语音输入 - 已禁用";
            default: return "WhisperType 语音输入";
        }
    }

    /** 程序内生成 16x16 圆形状态图标 */
    private Image createIcon(State state) {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Color color;
            String label = "";
            switch (state) {
                case IDLE:        color = new Color(46, 160, 67);  break;
                case RECORDING:   color = new Color(220, 53, 69);  break;
                case RECOGNIZING: color = new Color(0, 123, 255);  label = "?"; break;
                case ERROR:       color = new Color(255, 193, 7);  label = "!"; break;
                case DISABLED:    color = new Color(150, 150, 150); break;
                default:          color = new Color(46, 160, 67);
            }

            g.setColor(color);
            g.fillOval(1, 1, size - 2, size - 2);

            if (!label.isEmpty()) {
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                java.awt.FontMetrics fm = g.getFontMetrics();
                int tx = (size - fm.stringWidth(label)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(label, tx, ty);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
        if (popupInvoker != null) {
            popupInvoker.dispose();
            popupInvoker = null;
        }
    }
}
