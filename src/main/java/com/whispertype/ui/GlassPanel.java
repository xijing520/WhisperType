package com.whispertype.ui;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/**
 * 液态玻璃面板 —— 自定义绘制圆角半透明玻璃背景。
 * 透明度分层参照 GlassUI：主面板 85% / 次级 75%。
 */
public class GlassPanel extends JPanel {

    private final int radius;
    private final float alpha;
    private boolean controlStyle = false;

    public GlassPanel() {
        this(GlassUI.RADIUS_PANEL, GlassUI.ALPHA_PRIMARY);
    }

    public GlassPanel(LayoutManager layout) {
        this(layout, GlassUI.RADIUS_PANEL, GlassUI.ALPHA_PRIMARY);
    }

    public GlassPanel(int radius, float alpha) {
        super();
        this.radius = radius;
        this.alpha = alpha;
        init();
    }

    public GlassPanel(LayoutManager layout, int radius, float alpha) {
        super(layout);
        this.radius = radius;
        this.alpha = alpha;
        init();
    }

    /** 控件级玻璃（按钮内嵌等） */
    public void setControlStyle(boolean controlStyle) {
        this.controlStyle = controlStyle;
    }

    private void init() {
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (controlStyle) {
            GlassUI.paintControlGlass(g2, 0, 0, getWidth(), getHeight(),
                    radius, alpha, false);
        } else {
            GlassUI.paintGlass(g2, 0, 0, getWidth(), getHeight(), radius, alpha);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
