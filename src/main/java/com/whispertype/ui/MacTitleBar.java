package com.whispertype.ui;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;

/**
 * macOS 原生红绿灯标题栏 —— 关闭/最小化/最大化 + 居中标题。
 * 支持窗口拖拽（非按钮区域按下拖动移动窗口）。
 */
public class MacTitleBar extends JPanel {

    private static final Color RED    = new Color(255, 95, 87);
    private static final Color YELLOW = new Color(254, 188, 46);
    private static final Color GREEN  = new Color(40, 200, 64);

    private final Runnable onClose;
    private final Runnable onMinimize;
    private final Runnable onMaximize;
    private String title;
    private int hoverIndex = -1; // 0=red 1=yellow 2=green

    // 窗口拖拽状态
    private Point dragStart = null;
    private Point winStart = null;

    public MacTitleBar(String title, Runnable onClose, Runnable onMinimize, Runnable onMaximize) {
        this.title = title;
        this.onClose = onClose;
        this.onMinimize = onMinimize;
        this.onMaximize = onMaximize;
        setOpaque(false);
        setPreferredSize(new Dimension(getPreferredSize().width, 44));
        setCursor(Cursor.getDefaultCursor());

        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                int btn = hitButton(e.getX(), e.getY());
                if (btn != -1) {
                    // 点击红绿灯：执行对应动作
                    handlePress(btn);
                    return;
                }
                // 非按钮区域：开始拖拽窗口
                if (SwingUtilities.isLeftMouseButton(e)) {
                    Window win = SwingUtilities.getWindowAncestor(MacTitleBar.this);
                    if (win != null) {
                        try {
                            dragStart = e.getLocationOnScreen();
                            winStart = win.getLocation();
                        } catch (Exception ex) {
                            dragStart = null;
                        }
                    }
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int prev = hoverIndex;
                hoverIndex = hitButton(e.getX(), e.getY());
                if (prev != hoverIndex) repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    try {
                        Point now = e.getLocationOnScreen();
                        int dx = now.x - dragStart.x;
                        int dy = now.y - dragStart.y;
                        Window win = SwingUtilities.getWindowAncestor(MacTitleBar.this);
                        if (win != null) {
                            win.setLocation(winStart.x + dx, winStart.y + dy);
                        }
                    } catch (Exception ex) {
                        dragStart = null;
                    }
                }
            }
        });
    }

    public void setTitle(String title) {
        this.title = title;
        repaint();
    }

    private int hitButton(int x, int y) {
        int cx = 20;
        int cy = getHeight() / 2;
        int gap = 20;
        for (int i = 0; i < 3; i++) {
            Ellipse2D circle = new Ellipse2D.Double(cx + i * gap - 7, cy - 7, 14, 14);
            if (circle.contains(x, y)) return i;
        }
        return -1;
    }

    private void handlePress(int idx) {
        switch (idx) {
            case 0: if (onClose != null) onClose.run(); break;
            case 1: if (onMinimize != null) onMinimize.run(); break;
            case 2: if (onMaximize != null) onMaximize.run(); break;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int h = getHeight();
        int w = getWidth();

        // 标题栏轻微玻璃底
        g2.setComposite(AlphaComposite.SrcOver.derive(0.3f));
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        // 底部细分割线
        g2.setComposite(AlphaComposite.SrcOver.derive(0.06f));
        g2.setColor(new Color(0, 0, 0));
        g2.fillRect(0, h - 1, w, 1);

        g2.setComposite(AlphaComposite.SrcOver.derive(1f));

        // 三个红绿灯
        int cx = 20;
        int cy = h / 2;
        int gap = 20;
        Color[] colors = {RED, YELLOW, GREEN};
        for (int i = 0; i < 3; i++) {
            g2.setColor(colors[i]);
            Ellipse2D circle = new Ellipse2D.Double(cx + i * gap - 6, cy - 6, 12, 12);
            g2.fill(circle);
            // 悬浮时显示符号
            if (hoverIndex == i) {
                g2.setColor(new Color(0, 0, 0, 130));
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                String sym = i == 0 ? "×" : (i == 1 ? "−" : "+");
                int sw = g2.getFontMetrics().stringWidth(sym);
                g2.drawString(sym, cx + i * gap - sw / 2 - 1, cy + 3);
            }
        }

        // 居中标题
        if (title != null && !title.isEmpty()) {
            g2.setColor(GlassUI.TEXT_PRIMARY);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, (w - tw) / 2, h / 2 + 5);
        }

        g2.dispose();
    }
}
