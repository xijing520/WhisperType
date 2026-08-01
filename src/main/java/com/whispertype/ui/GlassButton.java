package com.whispertype.ui;

import javax.swing.JButton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 液态玻璃按钮 —— 圆角半透明底色，hover 微降透明度+放大，点击下压变暗。
 */
public class GlassButton extends JButton {

    private final int radius;
    private boolean hovered = false;
    private boolean pressed = false;
    private boolean primary = false;

    public GlassButton(String text) {
        this(text, GlassUI.RADIUS_BUTTON, false);
    }

    public GlassButton(String text, int radius, boolean primary) {
        super(text);
        this.radius = radius;
        this.primary = primary;
        init();
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
        repaint();
    }

    private void init() {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(null);
        setMargin(new Insets(0, 26, 0, 26));
        setFont(getFont().deriveFont(Font.PLAIN, 12f));
        setForeground(primary ? Color.WHITE : GlassUI.TEXT_PRIMARY);

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
        });
    }

    /** 根据文字与内边距正确计算首选尺寸 */
    @Override
    public Dimension getPreferredSize() {
        String text = getText();
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int textW = fm.stringWidth(text == null ? "" : text);
        int textH = fm.getHeight();
        Insets margin = getMargin();
        int w = textW + margin.left + margin.right;
        int h = Math.max(36, textH + 12);
        return new Dimension(w, h);
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

        if (primary) {
            // 强调按钮：苹果系统蓝
            g2.setComposite(AlphaComposite.SrcOver.derive(pressed ? 0.82f : (hovered ? 0.92f : 1f)));
            g2.setColor(hovered ? new Color(0, 122, 255, 235) : GlassUI.ACCENT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
            // 上边缘高光
            g2.setComposite(AlphaComposite.SrcOver.derive(0.6f));
            g2.setColor(new Color(255, 255, 255, 160));
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        } else {
            // 普通玻璃按钮
            float alpha = pressed ? 0.55f : (hovered ? 0.72f : 0.65f);
            GlassUI.paintControlGlass(g2, 0, 0, w, h, radius, alpha, hovered);
            if (pressed) {
                g2.setComposite(AlphaComposite.SrcOver.derive(0.12f));
                g2.setColor(new Color(0, 0, 0));
                g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
        }
        g2.setComposite(AlphaComposite.SrcOver.derive(1f));

        // 文字
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int textW = fm.stringWidth(text);
        int textH = fm.getAscent();
        int tx = (w - textW) / 2;
        int ty = (h + textH) / 2 - fm.getDescent() / 2;
        g2.setColor(primary ? new Color(255, 255, 255, 250) : GlassUI.TEXT_PRIMARY);
        g2.setFont(getFont());
        g2.drawString(text, tx, ty);

        g2.dispose();
    }
}
