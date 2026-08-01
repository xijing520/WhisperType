package com.whispertype.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * macOS 原生风格开关 —— 1:1 复刻配色与滑动动效。
 * 胶囊轨道 42×26，关闭态灰，开启态莫兰迪绿，滑块带软阴影。
 */
public class ToggleSwitch extends JComponent {

    private boolean on = false;
    private float knobPos = 0f; // 0=关 1=开
    private Timer anim;

    public ToggleSwitch() {
        setPreferredSize(new Dimension(42, 26));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { toggle(); }
        });
    }

    public boolean isOn() { return on; }

    public void setOn(boolean on) {
        this.on = on;
        animateTo(on ? 1f : 0f);
    }

    public void toggle() {
        setOn(!on);
        firePropertyChange("on", !on, on);
    }

    private void animateTo(float target) {
        if (anim != null && anim.isRunning()) anim.stop();
        final float start = knobPos;
        final float delta = target - start;
        anim = new Timer(16, e -> {
            knobPos += delta * 0.25f;
            boolean done = (delta > 0) ? (knobPos >= target) : (knobPos <= target);
            if (done) knobPos = target;
            repaint();
            if (done) ((Timer) e.getSource()).stop();
        });
        anim.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 轨道
        g2.setColor(on ? GlassUI.SUCCESS : new Color(120, 120, 128, 82));
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));

        // 滑块位置
        int knobSize = h - 4;
        int knobX = (int) (2 + knobPos * (w - knobSize - 4));
        int knobY = 2;

        // 滑块阴影
        g2.setComposite(AlphaComposite.SrcOver.derive(0.15f));
        g2.setColor(new Color(0, 0, 0));
        g2.fillRoundRect(knobX, knobY + 1, knobSize, knobSize, knobSize, knobSize);

        // 滑块主体
        g2.setComposite(AlphaComposite.SrcOver.derive(1f));
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(knobX, knobY, knobSize, knobSize, knobSize, knobSize);
        // 滑块细描边
        g2.setColor(new Color(0, 0, 0, 20));
        g2.drawRoundRect(knobX, knobY, knobSize, knobSize, knobSize, knobSize);

        g2.dispose();
    }
}
