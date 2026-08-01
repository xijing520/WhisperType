package com.whispertype.ui;

import com.whispertype.audio.AudioRecorder;
import com.whispertype.config.AppConfig;
import com.whispertype.hotkey.KeyCodeMapper;

import com.github.kwhat.jnativehook.NativeInputEvent;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;

/**
 * WhisperType 设置窗口 —— 液态玻璃设计语言完整实现。
 *
 * 结构：无装饰圆角窗口 + 自定义红绿灯标题栏 + 侧边栏导航 + 卡片式设置面板。
 * 材质：主面板 85% / 侧边栏 75% / 控件 65% 透明度，1px 白色柔光描边，弥散软阴影。
 */
public class SettingsWindow extends JFrame {

    private final AppConfig config;
    private final Runnable onSaveCallback;
    private Runnable onExitCallback;

    // 热键
    private int capturedKeyCode;
    private int capturedModifiers;
    private final HotkeyCaptureBox hotkeyBox = new HotkeyCaptureBox();
    private final JRadioButton longPressRadio = new JRadioButton("长按模式（按下录音，松开停止）");
    private final JRadioButton clickRadio = new JRadioButton("点击模式（点击开始/再次点击停止）");

    // 模型
    private final JTextField modelPathField = new JTextField();
    private final JTextField whisperCliPathField = new JTextField();
    private final JSpinner minRecordSpinner = new JSpinner(
            new SpinnerNumberModel(300, 0, 5000, 50));

    // 输入
    private final JRadioButton pasteRadio = new JRadioButton("粘贴模式（推荐中文）");
    private final JRadioButton typeRadio = new JRadioButton("模拟打字模式（推荐英文）");
    private final ToggleSwitch trimSwitch = new ToggleSwitch();
    private final JTextField suffixField = new JTextField();
    private final ToggleSwitch autoSendSwitch = new ToggleSwitch();

    // 音频
    private static final String DEFAULT_DEVICE_LABEL = "系统默认录音设备";
    private final JComboBox<String> audioDeviceCombo = new JComboBox<>();
    private final GlassButton refreshDeviceBtn = new GlassButton("刷新", GlassUI.RADIUS_BUTTON_SM, false);

    // 反馈
    private final ToggleSwitch soundSwitch = new ToggleSwitch();
    private final ToggleSwitch floatSwitch = new ToggleSwitch();

    // 主题
    private final JRadioButton lightThemeRadio = new JRadioButton("白天模式（浅色）");
    private final JRadioButton darkThemeRadio = new JRadioButton("黑夜模式（深色）");

    // 导航
    private final String[] navItems = {"热键设置", "模型配置", "文字输入", "音频设备", "反馈设置", "主题设置", "关于"};
    private final SidebarItem[] sidebarItems = new SidebarItem[navItems.length];
    private final ExitButtonItem exitItem = new ExitButtonItem();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    public SettingsWindow(AppConfig config, Runnable onSaveCallback) {
        super("WhisperType 设置");
        this.config = config;
        this.onSaveCallback = onSaveCallback;
        // 在创建组件前先应用主题，确保所有组件使用正确的颜色
        GlassUI.applyTheme(config.isDarkTheme());
        // 在创建组件前设置正确的 LAF，避免组件使用错误的默认颜色
        try {
            if (config.isDarkTheme()) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
        } catch (Exception ignored) { }
        initWindow();
        initUI();
        loadFromConfig();

        // 构造结束后再次刷新前景色（延迟到 EDT 之后），
        // 覆盖 loadFromConfig() 值填充过程中 LAF 可能引入的颜色变化
        SwingUtilities.invokeLater(() -> {
            refreshForegrounds(getContentPane());
            repaint();
        });
    }

    /** 设置退出回调（点击侧边栏"退出软件"时触发） */
    public void setOnExitCallback(Runnable onExitCallback) {
        this.onExitCallback = onExitCallback;
    }

    /* ===================== 窗口初始化 ===================== */
    private void initWindow() {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setResizable(true);
        setSize(780, 560);
        setMinimumSize(new Dimension(600, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        // 圆角窗口形状（随尺寸变化实时更新）
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                GlassUI.RADIUS_WINDOW, GlassUI.RADIUS_WINDOW));
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        GlassUI.RADIUS_WINDOW, GlassUI.RADIUS_WINDOW));
            }
        });
    }

    /* ===================== 界面构建 ===================== */
    private ResizeHandler resizeHandler;

    private void initUI() {
        // 使用标准的 BorderLayout 容器，缩放边框通过事件拦截实现
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        GlassPanel root = new GlassPanel(new BorderLayout(),
                GlassUI.RADIUS_WINDOW, GlassUI.ALPHA_PRIMARY);
        root.setBorder(BorderFactory.createEmptyBorder());
        root.setOpaque(false);

        MacTitleBar titleBar = new MacTitleBar("WhisperType 设置",
                () -> setVisible(false),
                () -> setState(Frame.ICONIFIED),
                () -> setExtendedState(getExtendedState() == Frame.MAXIMIZED_BOTH
                        ? Frame.NORMAL : Frame.MAXIMIZED_BOTH));
        root.add(titleBar, BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildButtonBar(), BorderLayout.SOUTH);

        contentPanel.add(root, BorderLayout.CENTER);

        setContentPane(contentPanel);

        // 设置缩放事件处理器
        resizeHandler = new ResizeHandler(contentPanel, this);

        // 为所有子组件注册鼠标事件，确保缩放检测能覆盖整个窗口
        registerGlobalResize(contentPanel);

        // 初始刷新所有前景色，并在 EDT 稳定后再执行一次
        // 防止 FlatLaf UI 委托安装/组件添加后覆盖自定义颜色
        refreshForegrounds(getContentPane());
        SwingUtilities.invokeLater(() -> refreshForegrounds(getContentPane()));
    }

    /** 递归注册鼠标事件监听，使窗口边缘的缩放检测能覆盖所有子组件 */
    private void registerGlobalResize(Component comp) {
        comp.addMouseMotionListener(resizeHandler);
        comp.addMouseListener(resizeHandler);
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                registerGlobalResize(child);
            }
        }
    }

    /* ===================== 窗口缩放处理器 ===================== */
    private static class ResizeHandler extends MouseAdapter {
        private static final int INSET = 8;
        private final SettingsWindow window;
        private final JPanel root;

        private int edge = 0;
        private Point dragStart;
        private int startW, startH, startX, startY;

        ResizeHandler(JPanel root, SettingsWindow window) {
            this.root = root;
            this.window = window;
        }

        boolean isAtEdge(Component source, Point ptInSource) {
            Point winPt = convertToWindow(source, ptInSource);
            if (winPt == null) return false;
            int w = window.getWidth();
            int h = window.getHeight();
            return winPt.x <= INSET || winPt.x >= w - INSET
                    || winPt.y <= INSET || winPt.y >= h - INSET;
        }

        private Point convertToWindow(Component source, Point pt) {
            try {
                return SwingUtilities.convertPoint(source, pt, window);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            Point winPt = convertToWindow(e.getComponent(), e.getPoint());
            if (winPt == null) return;
            int w = window.getWidth();
            int h = window.getHeight();
            int newEdge = 0;
            if (winPt.x <= INSET) newEdge |= 1;
            if (winPt.x >= w - INSET) newEdge |= 2;
            if (winPt.y <= INSET) newEdge |= 4;
            if (winPt.y >= h - INSET) newEdge |= 8;
            edge = newEdge;
            Component src = e.getComponent();
            if (src != null) {
                src.setCursor(edge != 0 ? getResizeCursor(edge) : Cursor.getDefaultCursor());
            }
            // 同时更新根容器的光标
            if (root.getCursor() == null || !isResizeCursor(root.getCursor())) {
                root.setCursor(edge != 0 ? getResizeCursor(edge) : Cursor.getDefaultCursor());
            }
        }

        private boolean isResizeCursor(Cursor c) {
            if (c == null) return false;
            int type = c.getType();
            return type != Cursor.DEFAULT_CURSOR;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (edge != 0) {
                Point winPt = convertToWindow(e.getComponent(), e.getPoint());
                if (winPt == null) return;
                // 确认在边缘
                int w = window.getWidth();
                int h = window.getHeight();
                if (winPt.x <= INSET || winPt.x >= w - INSET
                        || winPt.y <= INSET || winPt.y >= h - INSET) {
                    dragStart = e.getLocationOnScreen();
                    startW = window.getWidth();
                    startH = window.getHeight();
                    startX = window.getX();
                    startY = window.getY();
                    e.consume();
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            edge = 0;
            dragStart = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragStart == null || edge == 0) return;
            Point now = e.getLocationOnScreen();
            int dx = now.x - dragStart.x;
            int dy = now.y - dragStart.y;

            int newW = startW, newH = startH, newX = startX, newY = startY;
            if ((edge & 1) != 0) { newW = startW - dx; newX = startX + dx; }
            if ((edge & 2) != 0) { newW = startW + dx; }
            if ((edge & 4) != 0) { newH = startH - dy; newY = startY + dy; }
            if ((edge & 8) != 0) { newH = startH + dy; }

            Dimension min = window.getMinimumSize();
            if (newW < min.width) {
                if ((edge & 1) != 0) newX -= (min.width - newW);
                newW = min.width;
            }
            if (newH < min.height) {
                if ((edge & 4) != 0) newY -= (min.height - newH);
                newH = min.height;
            }
            window.setBounds(newX, newY, newW, newH);
            e.consume();
        }

        private Cursor getResizeCursor(int edge) {
            switch (edge) {
                case 1: return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case 2: return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case 4: return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case 8: return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case 5: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case 6: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case 9: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case 10: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default: return Cursor.getDefaultCursor();
            }
        }
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);

        // 侧边栏（75% 透明度）
        GlassPanel sidebar = new GlassPanel(new BorderLayout(),
                0, GlassUI.ALPHA_SECONDARY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 6, 16, 6));
        sidebar.setPreferredSize(new Dimension(176, 0));

        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setOpaque(false);
        String[] icons = {"⌨", "◈", "¶", "◉", "♪", "◐", "ⓘ"};
        for (int i = 0; i < navItems.length; i++) {
            SidebarItem item = new SidebarItem(icons[i], navItems[i], i);
            final int idx = i;
            item.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { switchCard(idx); }
            });
            sidebarItems[i] = item;
            navPanel.add(item);
            navPanel.add(Box.createVerticalStrut(2));
        }
        sidebar.add(navPanel, BorderLayout.NORTH);

        // 侧边栏底部：退出软件按钮
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.add(Box.createVerticalGlue());
        bottomPanel.add(exitItem);
        sidebar.add(bottomPanel, BorderLayout.CENTER);

        body.add(sidebar, BorderLayout.WEST);

        // 内容卡片
        cardPanel.setOpaque(false);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        cardPanel.add(scrollWrap(buildHotkeyCard()), "0");
        cardPanel.add(scrollWrap(buildModelCard()), "1");
        cardPanel.add(scrollWrap(buildInputCard()), "2");
        cardPanel.add(scrollWrap(buildAudioCard()), "3");
        cardPanel.add(scrollWrap(buildFeedbackCard()), "4");
        cardPanel.add(scrollWrap(buildThemeCard()), "5");
        cardPanel.add(scrollWrap(buildAboutCard()), "6");

        body.add(cardPanel, BorderLayout.CENTER);
        return body;
    }

    private JScrollPane scrollWrap(JComponent view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setWheelScrollingEnabled(true);
        return sp;
    }

    private void switchCard(int index) {
        for (int i = 0; i < sidebarItems.length; i++) {
            sidebarItems[i].setActive(i == index);
        }
        cardLayout.show(cardPanel, String.valueOf(index));
    }

    /* ---------- 设置卡片构建 ---------- */

    /** 热键设置卡片 */
    private JComponent buildHotkeyCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(longPressRadio);
        modeGroup.add(clickRadio);
        styleRadio(longPressRadio);
        styleRadio(clickRadio);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(sectionTitle("全局热键"), BorderLayout.NORTH);
        content.add(settingRow("热键", "点击右侧框后按下目标组合键", hotkeyBox), BorderLayout.CENTER);
        content.add(Box.createVerticalStrut(4), BorderLayout.SOUTH);

        JPanel modePanel = new JPanel(new GridLayout(0, 1, 0, 8));
        modePanel.setOpaque(false);
        modePanel.add(sectionTitle("录音模式"));
        modePanel.add(longPressRadio);
        modePanel.add(clickRadio);

        card.add(content, BorderLayout.NORTH);
        card.add(modePanel, BorderLayout.CENTER);
        return wrapInGlass(card);
    }

    /** 模型配置卡片 */
    private JComponent buildModelCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(sectionTitle("Whisper 模型"));
        rows.add(settingRow("Whisper CLI 路径", "whisper-cli.exe 可执行文件",
                pathRow(whisperCliPathField, "选择 whisper-cli.exe")));
        rows.add(dividerRow());
        rows.add(settingRow("模型文件", "ggml 格式 Whisper 模型",
                pathRow(modelPathField, "选择 Whisper 模型文件")));
        rows.add(dividerRow());
        rows.add(settingRow("最短录音时长", "低于此时长的录音将被忽略",
                minRecordSpinner));

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /** 文字输入卡片 */
    private JComponent buildInputCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        ButtonGroup inputGroup = new ButtonGroup();
        inputGroup.add(pasteRadio);
        inputGroup.add(typeRadio);
        styleRadio(pasteRadio);
        styleRadio(typeRadio);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(sectionTitle("输入方式"));
        rows.add(pasteRadio);
        rows.add(typeRadio);
        rows.add(dividerRow());
        rows.add(settingRow("去除首尾空格", "识别结果自动去除两端空白字符", trimSwitch));
        rows.add(dividerRow());
        suffixField.setPreferredSize(new Dimension(140, 32));
        rows.add(settingRow("文本后缀", "如 空格 / 换行 \\n，留空不添加", suffixField));
        rows.add(dividerRow());
        rows.add(settingRow("自动发送消息", "末尾补回车，适用于聊天框", autoSendSwitch));

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /** 音频设备卡片 */
    private JComponent buildAudioCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        audioDeviceCombo.addItem(DEFAULT_DEVICE_LABEL);
        audioDeviceCombo.setSelectedItem(DEFAULT_DEVICE_LABEL);
        audioDeviceCombo.setPreferredSize(new Dimension(200, 32));
        refreshDeviceBtn.setPreferredSize(new Dimension(56, 32));
        refreshDeviceBtn.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { refreshAudioDevices(); }
        });
        JPanel deviceRow = new JPanel(new BorderLayout(8, 0));
        deviceRow.setOpaque(false);
        deviceRow.add(audioDeviceCombo, BorderLayout.CENTER);
        deviceRow.add(refreshDeviceBtn, BorderLayout.EAST);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(sectionTitle("音频输入设备"));
        rows.add(settingRow("录音设备", "选择用于语音录入的麦克风", deviceRow));

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /** 反馈设置卡片 */
    private JComponent buildFeedbackCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(sectionTitle("反馈"));
        rows.add(settingRow("启用声音提示", "录音开始与结束时播放提示音", soundSwitch));
        rows.add(dividerRow());
        rows.add(settingRow("显示悬浮窗", "录音时显示悬浮状态窗", floatSwitch));

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /** 主题设置卡片 */
    private JComponent buildThemeCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);

        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(lightThemeRadio);
        themeGroup.add(darkThemeRadio);
        styleRadio(lightThemeRadio);
        styleRadio(darkThemeRadio);
        lightThemeRadio.addActionListener(e -> applyThemeAndRepaint(false));
        darkThemeRadio.addActionListener(e -> applyThemeAndRepaint(true));

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(sectionTitle("主题"));
        rows.add(lightThemeRadio);
        rows.add(darkThemeRadio);
        rows.add(dividerRow());
        JLabel hint = new JLabel("选择白天或黑夜模式，切换后立即预览，保存后生效。") {
            @Override
            public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_TERTIARY);
                super.paintComponent(g);
            }
        };
        hint.setOpaque(false);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(GlassUI.TEXT_TERTIARY);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        rows.add(hint);

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /** 应用主题并刷新整个窗口 */
    private void applyThemeAndRepaint(boolean dark) {
        GlassUI.applyTheme(dark);
        try {
            if (dark) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
            // 只更新 LAF 相关的 UI 属性，不重置所有颜色
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) { }
        // 在 LAF 更新后立即重新应用自定义前景色
        refreshForegrounds(getContentPane());
        // 再次延迟刷新，确保 updateComponentTreeUI 引起的 LAF 颜色重置被完全覆盖
        SwingUtilities.invokeLater(() -> {
            refreshForegrounds(getContentPane());
            revalidate();
            repaint();
        });
        revalidate();
        repaint();
    }

    /** 递归刷新手动绘制组件的前景色，确保主题切换后文字清晰可见 */
    private void refreshForegrounds(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof GlassButton) {
                // GlassButton 自定义绘制文字，不通过 setForeground 控制
                comp.setForeground(GlassUI.TEXT_PRIMARY);
            } else if (comp instanceof AbstractButton) {
                comp.setForeground(GlassUI.TEXT_PRIMARY);
            } else if (comp instanceof JLabel) {
                JLabel lbl = (JLabel) comp;
                Font f = lbl.getFont();
                float size = f.getSize2D();
                // 使用字体样式区分：BOLD = TEXT_PRIMARY，PLAIN = TEXT_SECONDARY
                // 小字号（<=11f）的纯文本标签也用 TEXT_SECONDARY
                if (f.isBold() || size >= 12f) {
                    lbl.setForeground(GlassUI.TEXT_PRIMARY);
                } else {
                    lbl.setForeground(GlassUI.TEXT_SECONDARY);
                }
            } else if (comp instanceof JTextField || comp instanceof JFormattedTextField) {
                comp.setForeground(GlassUI.TEXT_PRIMARY);
                comp.setBackground(GlassUI.isDarkMode()
                        ? new Color(60, 60, 70) : Color.WHITE);
            } else if (comp instanceof JTextArea || comp instanceof JTextPane) {
                comp.setForeground(GlassUI.TEXT_PRIMARY);
                comp.setBackground(GlassUI.isDarkMode()
                        ? new Color(60, 60, 70) : Color.WHITE);
            } else if (comp instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) comp;
                comp.setForeground(GlassUI.TEXT_PRIMARY);
                comp.setBackground(GlassUI.isDarkMode()
                        ? new Color(60, 60, 70) : Color.WHITE);
                // 更新组合框渲染器颜色
                ListCellRenderer<?> renderer = combo.getRenderer();
                if (renderer instanceof JComponent) {
                    ((JComponent) renderer).setForeground(GlassUI.TEXT_PRIMARY);
                    ((JComponent) renderer).setBackground(GlassUI.isDarkMode()
                            ? new Color(60, 60, 70) : Color.WHITE);
                }
            } else if (comp instanceof JSpinner) {
                comp.setForeground(GlassUI.TEXT_PRIMARY);
                JSpinner sp = (JSpinner) comp;
                JComponent editor = sp.getEditor();
                if (editor != null) {
                    for (java.awt.Component ec : editor.getComponents()) {
                        ec.setForeground(GlassUI.TEXT_PRIMARY);
                        ec.setBackground(GlassUI.isDarkMode()
                                ? new Color(60, 60, 70) : Color.WHITE);
                        if (ec instanceof java.awt.Container) {
                            refreshForegrounds((java.awt.Container) ec);
                        }
                    }
                }
            } else if (comp instanceof JToggleButton) {
                comp.setForeground(GlassUI.TEXT_PRIMARY);
            }

            // 递归处理子容器
            if (comp instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) comp;
                JViewport vp = sp.getViewport();
                if (vp != null) {
                    Component view = vp.getView();
                    if (view instanceof java.awt.Container) {
                        refreshForegrounds((java.awt.Container) view);
                    }
                }
                // 处理 JScrollPane 的列头
                if (sp.getColumnHeader() != null && sp.getColumnHeader().getView() instanceof java.awt.Container) {
                    refreshForegrounds((java.awt.Container) sp.getColumnHeader().getView());
                }
            } else if (comp instanceof JTabbedPane) {
                JTabbedPane tp = (JTabbedPane) comp;
                for (int i = 0; i < tp.getTabCount(); i++) {
                    Component tab = tp.getComponentAt(i);
                    if (tab instanceof java.awt.Container) {
                        refreshForegrounds((java.awt.Container) tab);
                    }
                }
            } else if (comp instanceof JComboBox) {
                // JComboBox 本身也是容器，递归处理其内部组件
                refreshForegrounds((java.awt.Container) comp);
            } else if (comp instanceof java.awt.Container) {
                // 对于 CardLayout 等布局，getComponents() 仍返回所有组件
                refreshForegrounds((java.awt.Container) comp);
            }
        }
    }

    /** 关于卡片 */
    private JComponent buildAboutCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 12));
        rows.setOpaque(false);
        rows.add(sectionTitle("关于 WhisperType"));

        JLabel name = new JLabel("WhisperType 语音输入助手") {
            @Override public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_PRIMARY); super.paintComponent(g);
            }
        };
        name.setOpaque(false);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
        name.setForeground(GlassUI.TEXT_PRIMARY);
        rows.add(name);

        // desc 用 HTML：使用内联 CSS 颜色
        Color cDesc = GlassUI.TEXT_SECONDARY;
        String hexDesc = String.format("#%02x%02x%02x", cDesc.getRed(), cDesc.getGreen(), cDesc.getBlue());
        JLabel desc = new JLabel(
                "<html><body style='margin:0;padding:0;color:" + hexDesc + "'>"
                + "<div style='color:" + hexDesc + "'>"
                + "离线语音转文字桌面工具，基于 OpenAI Whisper 模型。<br>"
                + "长按热键说话，松开即可将语音转化为文字输入到光标位置。"
                + "</div></body></html>") {
            @Override public void paintComponent(Graphics g) {
                Color c = GlassUI.TEXT_SECONDARY;
                String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                String expected =
                        "<html><body style='margin:0;padding:0;color:" + hex + "'>"
                        + "<div style='color:" + hex + "'>"
                        + "离线语音转文字桌面工具，基于 OpenAI Whisper 模型。<br>"
                        + "长按热键说话，松开即可将语音转化为文字输入到光标位置。"
                        + "</div></body></html>";
                if (!expected.equals(getText())) setText(expected);
                super.paintComponent(g);
            }
        };
        desc.setOpaque(false);
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 12f));
        desc.setForeground(GlassUI.TEXT_SECONDARY);
        rows.add(desc);

        rows.add(dividerRow());

        JLabel author = new JLabel("作者：小寒") {
            @Override public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_SECONDARY); super.paintComponent(g);
            }
        };
        author.setOpaque(false);
        author.setFont(author.getFont().deriveFont(Font.PLAIN, 12f));
        author.setForeground(GlassUI.TEXT_SECONDARY);
        rows.add(author);

        JLabel version = new JLabel("版本：2.0.0") {
            @Override public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_TERTIARY); super.paintComponent(g);
            }
        };
        version.setOpaque(false);
        version.setFont(version.getFont().deriveFont(Font.PLAIN, 12f));
        version.setForeground(GlassUI.TEXT_TERTIARY);
        rows.add(version);

        card.add(rows, BorderLayout.NORTH);
        return wrapInGlass(card);
    }

    /* ---------- 底部按钮栏 ---------- */
    private JComponent buildButtonBar() {
        GlassPanel bar = new GlassPanel(new BorderLayout(),
                0, GlassUI.ALPHA_PRIMARY);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 28, 14, 28));
        bar.setPreferredSize(new Dimension(0, 60));

        GlassButton cancelBtn = new GlassButton("取消", GlassUI.RADIUS_BUTTON, false);
        cancelBtn.setPreferredSize(new Dimension(96, 36));
        cancelBtn.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                loadFromConfig();
                setVisible(false);
            }
        });
        GlassButton saveBtn = new GlassButton("保存并关闭", GlassUI.RADIUS_BUTTON, true);
        saveBtn.setPreferredSize(new Dimension(120, 36));
        saveBtn.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { saveAndClose(); }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        bar.add(btnPanel, BorderLayout.CENTER);
        return bar;
    }

    /* ===================== UI 辅助方法 ===================== */

    private JComponent wrapInGlass(JComponent content) {
        GlassPanel glass = new GlassPanel(new BorderLayout(),
                GlassUI.RADIUS_PANEL, GlassUI.ALPHA_PRIMARY);
        glass.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        glass.add(content, BorderLayout.CENTER);
        return glass;
    }

    private JComponent sectionTitle(String text) {
        // 自定义 JLabel：每次绘制时强制设置前景色，拦截 LAF 重置
        JLabel label = new JLabel(text) {
            @Override
            public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_SECONDARY);
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setForeground(GlassUI.TEXT_SECONDARY);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    /** 一行设置：左侧标签+说明，右侧控件。使用 GridBagLayout 确保控件不被挤压 */
    private JComponent settingRow(String label, String hint, JComponent control) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        // 左侧列：标签 + 提示
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 16);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.0;
        gbc.weighty = 1.0;

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(170, 44));
        left.setMinimumSize(new Dimension(160, 36));
        left.setMaximumSize(new Dimension(180, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(label) {
            @Override
            public void paintComponent(Graphics g) {
                setForeground(GlassUI.TEXT_PRIMARY);
                super.paintComponent(g);
            }
        };
        titleLabel.setOpaque(false);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        titleLabel.setForeground(GlassUI.TEXT_PRIMARY);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        left.add(titleLabel);

        if (hint != null && !hint.isEmpty()) {
            Color tc = GlassUI.TEXT_TERTIARY;
            String hexColor = String.format("#%02x%02x%02x", tc.getRed(), tc.getGreen(), tc.getBlue());
            JLabel hintLabel = new JLabel(
                    "<html><body style='margin:0;padding:0;color:" + hexColor + "'>"
                    + "<div style='width:160px'>"
                    + hint + "</div></body></html>") {
                @Override
                public void paintComponent(Graphics g) {
                    Color c = GlassUI.TEXT_TERTIARY;
                    String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                    String newText =
                            "<html><body style='margin:0;padding:0;color:" + hex + "'>"
                            + "<div style='width:160px'>"
                            + hint + "</div></body></html>";
                    if (!newText.equals(getText())) {
                        setText(newText);
                    }
                    super.paintComponent(g);
                }
            };
            hintLabel.setOpaque(false);
            hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
            hintLabel.setForeground(GlassUI.TEXT_TERTIARY);
            hintLabel.setAlignmentX(LEFT_ALIGNMENT);
            hintLabel.setHorizontalAlignment(SwingConstants.LEFT);
            hintLabel.setHorizontalTextPosition(SwingConstants.LEFT);
            left.add(hintLabel);
        }
        gbc.gridx = 0;
        gbc.gridy = 0;
        row.add(left, gbc);

        // 右侧控件
        if (control != null) {
            Dimension ps = control.getPreferredSize();
            if (ps.height < 30) {
                control.setPreferredSize(new Dimension(ps.width, 30));
            }
            JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            right.setOpaque(false);
            right.add(control);
            gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.gridx = 1;
            gbc.gridy = 0;
            row.add(right, gbc);
        }

        return row;
    }

    private JComponent pathRow(JTextField field, String dialogTitle) {
        field.setPreferredSize(new Dimension(200, 32));
        GlassButton browseBtn = new GlassButton("浏览", GlassUI.RADIUS_BUTTON_SM, false);
        browseBtn.setPreferredSize(new Dimension(56, 32));
        browseBtn.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                browseFile(field, dialogTitle);
            }
        });
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.add(field, BorderLayout.CENTER);
        p.add(browseBtn, BorderLayout.EAST);
        return p;
    }

    private JComponent dividerRow() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0, 0, 0, 20));
        sep.setBackground(new Color(0, 0, 0, 20));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(sep, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return p;
    }

    private void styleRadio(JRadioButton radio) {
        radio.setOpaque(false);
        radio.setFont(radio.getFont().deriveFont(Font.PLAIN, 12f));
        radio.setForeground(GlassUI.TEXT_PRIMARY);
        radio.setFocusPainted(false);
    }

    /* ===================== 热键捕获框 ===================== */
    private class HotkeyCaptureBox extends JComponent {
        private boolean capturing = false;
        private String display = "";

        HotkeyCaptureBox() {
            setPreferredSize(new Dimension(180, 36));
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    capturing = true;
                    display = "请按下目标键...";
                    repaint();
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
                            || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META) {
                        return;
                    }
                    capturedKeyCode = code;
                    capturedModifiers = e.getModifiersEx();
                    updateHotkeyLabel();
                    capturing = false;
                    e.consume();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { capturing = false; repaint(); }
            });
        }

        void setDisplay(String text) {
            this.display = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            // 内嵌磨砂玻璃底
            GlassUI.paintControlGlass(g2, 0, 0, w, h, GlassUI.RADIUS_INPUT,
                    0.5f, capturing);
            if (capturing || isFocusOwner()) {
                g2.setColor(new Color(0, 122, 255, 60));
                g2.drawRoundRect(0, 0, w - 1, h - 1, GlassUI.RADIUS_INPUT, GlassUI.RADIUS_INPUT);
            }
            // 文字
            g2.setColor(capturing ? GlassUI.TEXT_TERTIARY : GlassUI.TEXT_PRIMARY);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(display);
            g2.drawString(display, (w - tw) / 2, (h + fm.getAscent()) / 2 - 3);
            g2.dispose();
        }
    }

    /* ===================== 侧边栏导航项 ===================== */
    private class SidebarItem extends JComponent {
        private final String icon;
        private final String text;
        private final int index;
        private boolean active = false;
        private boolean hovered = false;

        SidebarItem(String icon, String text, int index) {
            this.icon = icon;
            this.text = text;
            this.index = index;
            // 不设 maxSize，让 BoxLayout 拉伸填充可用宽度
            setPreferredSize(new Dimension(150, 36));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth();
            int h = getHeight();
            // 与侧边栏边缘留 5px 间距
            int margin = 5;
            int innerW = Math.max(1, w - margin * 2);

            if (active) {
                g2.setColor(new Color(0, 122, 255, 28));
                g2.fillRoundRect(margin, 0, innerW, h - 1,
                        GlassUI.RADIUS_BUTTON_SM, GlassUI.RADIUS_BUTTON_SM);
            } else if (hovered) {
                g2.setColor(GlassUI.isDarkMode()
                        ? new Color(255, 255, 255, 18)
                        : new Color(0, 0, 0, 12));
                g2.fillRoundRect(margin, 0, innerW, h - 1,
                        GlassUI.RADIUS_BUTTON_SM, GlassUI.RADIUS_BUTTON_SM);
            }

            // 图标
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
            g2.setColor(active ? GlassUI.ACCENT : GlassUI.TEXT_SECONDARY);
            FontMetrics fm = g2.getFontMetrics();
            int iconX = margin + 10;
            g2.drawString(icon, iconX, (h + fm.getAscent()) / 2 - 3);

            // 文字 — 动态计算可用宽度，防止截断
            g2.setFont(g2.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 12f));
            g2.setColor(active ? GlassUI.ACCENT : GlassUI.TEXT_SECONDARY);
            fm = g2.getFontMetrics();
            int iconEndX = iconX + fm.stringWidth(icon);
            // 图标与文字之间 8px 间距，文字右侧留 6px 安全边距
            int textX = Math.max(iconEndX + 8, margin + 34);
            int maxTextW = Math.max(10, w - textX - margin - 6);
            String displayText = text;
            int textW = fm.stringWidth(displayText);
            if (textW > maxTextW) {
                // 截断过长文字（先减字，最后加省略号）
                while (displayText.length() > 1
                        && fm.stringWidth(displayText + "…") > maxTextW) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                if (displayText.length() < text.length()) {
                    displayText = displayText + "…";
                }
            }
            // 确保绘制在可见区域内，使用 clip 防止溢出
            g2.setClip(textX, 0, maxTextW + 4, h);
            g2.drawString(displayText, textX, (h + fm.getAscent()) / 2 - 3);
            g2.dispose();
        }
    }

    /* ===================== 侧边栏底部退出按钮 ===================== */
    private class ExitButtonItem extends JComponent {
        private boolean hovered = false;

        ExitButtonItem() {
            setPreferredSize(new Dimension(150, 36));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (onExitCallback != null) {
                        onExitCallback.run();
                    } else {
                        // 默认行为：直接退出 JVM
                        System.exit(0);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth();
            int h = getHeight();
            int margin = 5;
            int innerW = Math.max(1, w - margin * 2);

            if (hovered) {
                g2.setColor(new Color(220, 53, 69, 40));
                g2.fillRoundRect(margin, 0, innerW, h - 1,
                        GlassUI.RADIUS_BUTTON_SM, GlassUI.RADIUS_BUTTON_SM);
            }

            // 图标（电源符号）
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
            Color dangerColor = new Color(220, 53, 69);
            g2.setColor(dangerColor);
            FontMetrics fm = g2.getFontMetrics();
            int iconX = margin + 10;
            String iconStr = "⏻";
            g2.drawString(iconStr, iconX, (h + fm.getAscent()) / 2 - 3);

            // 文字 — 添加与 SidebarItem 一致的截断保护
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            g2.setColor(dangerColor);
            fm = g2.getFontMetrics();
            int iconEndX = iconX + fm.stringWidth(iconStr);
            int textX = Math.max(iconEndX + 8, margin + 34);
            int maxTextW = Math.max(10, w - textX - margin - 6);
            String text = "退出软件";
            String displayText = text;
            if (fm.stringWidth(displayText) > maxTextW) {
                while (displayText.length() > 1
                        && fm.stringWidth(displayText + "…") > maxTextW) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                if (displayText.length() < text.length()) {
                    displayText = displayText + "…";
                }
            }
            g2.setClip(textX, 0, maxTextW + 4, h);
            g2.drawString(displayText, textX, (h + fm.getAscent()) / 2 - 3);
            g2.dispose();
        }
    }

    /* ===================== 业务逻辑（保留原功能） ===================== */

    private void browseFile(JTextField targetField, String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle(dialogTitle);
        String current = targetField.getText();
        if (current != null && !current.isEmpty()) {
            File curFile = new File(current);
            if (curFile.getParentFile() != null && curFile.getParentFile().exists()) {
                chooser.setCurrentDirectory(curFile.getParentFile());
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void refreshAudioDevices() {
        String prevSelected = (String) audioDeviceCombo.getSelectedItem();
        audioDeviceCombo.removeAllItems();
        audioDeviceCombo.addItem(DEFAULT_DEVICE_LABEL);

        try {
            AudioFormat fmt = new AudioFormat(
                    config.getSampleRate(),
                    config.getSampleSizeBits(),
                    config.getChannels(),
                    true, false);
            List<Mixer.Info> devices = AudioRecorder.listInputDevices(fmt);
            for (Mixer.Info mi : devices) {
                String name = mi.getName();
                if (name != null && !name.isEmpty()) {
                    audioDeviceCombo.addItem(name);
                }
            }
            if (devices.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "未检测到支持当前音频格式（" + config.getSampleRate()
                                + "Hz / " + config.getSampleSizeBits() + "bit / "
                                + config.getChannels() + "声道）的录音设备。\n"
                                + "请检查麦克风连接或系统音频设置。",
                        "提示", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "扫描录音设备失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }

        if (prevSelected != null) {
            for (int i = 0; i < audioDeviceCombo.getItemCount(); i++) {
                if (prevSelected.equals(audioDeviceCombo.getItemAt(i))) {
                    audioDeviceCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        audioDeviceCombo.setSelectedItem(DEFAULT_DEVICE_LABEL);
    }

    private void updateHotkeyLabel() {
        StringBuilder sb = new StringBuilder();
        if ((capturedModifiers & KeyEvent.CTRL_DOWN_MASK) != 0) sb.append("Ctrl + ");
        if ((capturedModifiers & KeyEvent.ALT_DOWN_MASK) != 0) sb.append("Alt + ");
        if ((capturedModifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift + ");
        if ((capturedModifiers & KeyEvent.META_DOWN_MASK) != 0) sb.append("Meta + ");
        sb.append(KeyEvent.getKeyText(capturedKeyCode));
        hotkeyBox.setDisplay(sb.toString());
    }

    private void loadFromConfig() {
        capturedKeyCode = KeyCodeMapper.toJavaKeyCode(config.getHotkeyCode());
        capturedModifiers = nativeModsToJavaEx(config.getHotkeyModifiers());
        updateHotkeyLabel();

        if (config.isLongPressMode()) {
            longPressRadio.setSelected(true);
        } else {
            clickRadio.setSelected(true);
        }

        whisperCliPathField.setText(config.getWhisperCliPath());
        modelPathField.setText(config.getModelPath());
        minRecordSpinner.setValue(config.getMinRecordingMs());

        if (config.isPasteMode()) {
            pasteRadio.setSelected(true);
        } else {
            typeRadio.setSelected(true);
        }
        trimSwitch.setOn(config.isTrimWhitespace());
        suffixField.setText(config.getTextSuffix());
        autoSendSwitch.setOn(config.isAutoSendMode());
        soundSwitch.setOn(config.isSoundFeedback());
        floatSwitch.setOn(config.isShowFloatingWindow());

        // 主题（LAF 已在构造函数中设置，这里仅选中对应单选按钮）
        if (config.isDarkTheme()) {
            darkThemeRadio.setSelected(true);
        } else {
            lightThemeRadio.setSelected(true);
        }

        refreshAudioDevices();
        String cfgDevice = config.getAudioInputDevice();
        if (cfgDevice != null && !cfgDevice.isEmpty()) {
            boolean matched = false;
            for (int i = 0; i < audioDeviceCombo.getItemCount(); i++) {
                if (cfgDevice.equals(audioDeviceCombo.getItemAt(i))) {
                    audioDeviceCombo.setSelectedIndex(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                audioDeviceCombo.addItem(cfgDevice + " (已失效)");
                audioDeviceCombo.setSelectedItem(cfgDevice + " (已失效)");
            }
        } else {
            audioDeviceCombo.setSelectedItem(DEFAULT_DEVICE_LABEL);
        }

        // 默认选中第一个导航项
        switchCard(0);
    }

    private void saveAndClose() {
        config.setHotkeyCode(KeyCodeMapper.toNativeKeyCode(capturedKeyCode));
        config.setHotkeyModifiers(javaExToNativeMods(capturedModifiers));
        config.setLongPressMode(longPressRadio.isSelected());

        config.setWhisperCliPath(whisperCliPathField.getText().trim());
        config.setModelPath(modelPathField.getText().trim());
        config.setMinRecordingMs((Integer) minRecordSpinner.getValue());
        config.setPasteMode(pasteRadio.isSelected());
        config.setTrimWhitespace(trimSwitch.isOn());
        config.setTextSuffix(suffixField.getText());
        config.setAutoSendMode(autoSendSwitch.isOn());
        config.setSoundFeedback(soundSwitch.isOn());
        config.setShowFloatingWindow(floatSwitch.isOn());
        config.setDarkTheme(darkThemeRadio.isSelected());

        String selectedDevice = (String) audioDeviceCombo.getSelectedItem();
        if (selectedDevice == null || DEFAULT_DEVICE_LABEL.equals(selectedDevice)
                || selectedDevice.endsWith(" (已失效)")) {
            config.setAudioInputDevice("");
        } else {
            config.setAudioInputDevice(selectedDevice);
        }

        config.save();

        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
        setVisible(false);
    }

    private int nativeModsToJavaEx(int nativeMods) {
        int ex = 0;
        if ((nativeMods & NativeInputEvent.SHIFT_MASK) != 0) ex |= KeyEvent.SHIFT_DOWN_MASK;
        if ((nativeMods & NativeInputEvent.CTRL_MASK) != 0) ex |= KeyEvent.CTRL_DOWN_MASK;
        if ((nativeMods & NativeInputEvent.ALT_MASK) != 0) ex |= KeyEvent.ALT_DOWN_MASK;
        if ((nativeMods & NativeInputEvent.META_MASK) != 0) ex |= KeyEvent.META_DOWN_MASK;
        return ex;
    }

    private int javaExToNativeMods(int ex) {
        int nativeMods = 0;
        if ((ex & KeyEvent.SHIFT_DOWN_MASK) != 0) nativeMods |= NativeInputEvent.SHIFT_MASK;
        if ((ex & KeyEvent.CTRL_DOWN_MASK) != 0) nativeMods |= NativeInputEvent.CTRL_MASK;
        if ((ex & KeyEvent.ALT_DOWN_MASK) != 0) nativeMods |= NativeInputEvent.ALT_MASK;
        if ((ex & KeyEvent.META_DOWN_MASK) != 0) nativeMods |= NativeInputEvent.META_MASK;
        return nativeMods;
    }
}
