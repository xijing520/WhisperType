package com.whispertype.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * 液态玻璃设计系统 —— 主题常量与绘制工具。
 *
 * 对标 macOS Sonoma Liquid Glass 设计语言：
 *  - 透明度分层：主面板 85% / 次级面板 75% / 控件 65%
 *  - 玻璃边缘：1px 极细白色柔光描边
 *  - 圆角：窗口 16 / 面板 14 / 按钮 12 / 小按钮 8 / 输入框 10
 *  - 莫兰迪低饱和色系，拒绝纯黑文字
 *  - 弥散软阴影，无硬阴影
 *
 * 主题：支持浅色（默认）与深色两种，通过 applyTheme() 切换。
 */
public final class GlassUI {

    private GlassUI() {}

    /* ========== 透明度分层（0~1） ========== */
    public static final float ALPHA_PRIMARY   = 0.85f;
    public static final float ALPHA_SECONDARY = 0.75f;
    public static final float ALPHA_CONTROL   = 0.65f;

    /* ========== 圆角 ========== */
    public static final int RADIUS_WINDOW   = 16;
    public static final int RADIUS_PANEL    = 14;
    public static final int RADIUS_BUTTON   = 12;
    public static final int RADIUS_BUTTON_SM = 8;
    public static final int RADIUS_INPUT    = 10;

    /* ========== 莫兰迪色系（两种主题通用） ========== */
    public static final Color ACCENT       = new Color(0, 122, 255);
    public static final Color ACCENT_SOFT  = new Color(0, 122, 255, 31);
    public static final Color SUCCESS      = new Color(76, 175, 122);
    public static final Color WARNING      = new Color(214, 168, 92);
    public static final Color DANGER       = new Color(196, 92, 92);
    public static final Color RECORDING    = new Color(220, 90, 90);

    /* ========== 文字色阶（按主题切换，默认浅色） ========== */
    public static Color TEXT_PRIMARY   = new Color(28, 28, 30);
    public static Color TEXT_SECONDARY = new Color(108, 108, 112);
    public static Color TEXT_TERTIARY  = new Color(142, 142, 147);

    /* ========== 玻璃基底色（按主题切换，默认浅色） ========== */
    public static Color GLASS_TOP    = new Color(255, 255, 255);
    public static Color GLASS_BOTTOM = new Color(244, 245, 250);
    public static Color EDGE_LIGHT   = new Color(255, 255, 255, 200);
    public static Color EDGE_SHADOW  = new Color(0, 0, 0, 18);

    /* ========== 弥散软阴影 ========== */
    public static Color SHADOW_COLOR = new Color(0, 0, 0, 28);

    /* ========== 控件玻璃底色（按主题切换） ========== */
    public static Color CONTROL_GLASS_NORMAL = new Color(255, 255, 255, 190);
    public static Color CONTROL_GLASS_HOVER  = new Color(255, 255, 255, 220);

    /* ========== 当前主题标记 ========== */
    private static boolean darkMode = false;

    /** 切换主题（true=深色，false=浅色） */
    public static void applyTheme(boolean dark) {
        darkMode = dark;
        if (dark) {
            // 深色模式：玻璃基底改为深灰半透明
            TEXT_PRIMARY   = new Color(240, 240, 245);
            TEXT_SECONDARY = new Color(185, 185, 195);
            TEXT_TERTIARY  = new Color(155, 155, 168);
            GLASS_TOP    = new Color(44, 44, 52);
            GLASS_BOTTOM = new Color(28, 28, 34);
            EDGE_LIGHT   = new Color(255, 255, 255, 38);
            EDGE_SHADOW  = new Color(0, 0, 0, 90);
            SHADOW_COLOR = new Color(0, 0, 0, 80);
            CONTROL_GLASS_NORMAL = new Color(60, 60, 70, 200);
            CONTROL_GLASS_HOVER  = new Color(75, 75, 85, 220);
        } else {
            // 浅色模式
            TEXT_PRIMARY   = new Color(28, 28, 30);
            TEXT_SECONDARY = new Color(108, 108, 112);
            TEXT_TERTIARY  = new Color(142, 142, 147);
            GLASS_TOP    = new Color(255, 255, 255);
            GLASS_BOTTOM = new Color(244, 245, 250);
            EDGE_LIGHT   = new Color(255, 255, 255, 200);
            EDGE_SHADOW  = new Color(0, 0, 0, 18);
            SHADOW_COLOR = new Color(0, 0, 0, 28);
            CONTROL_GLASS_NORMAL = new Color(255, 255, 255, 190);
            CONTROL_GLASS_HOVER  = new Color(255, 255, 255, 220);
        }
    }

    public static boolean isDarkMode() { return darkMode; }

    /**
     * 绘制液态玻璃面板背景。
     */
    public static void paintGlass(Graphics2D g2, int x, int y, int w, int h,
                                  int radius, float alpha) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. 弥散软阴影
        g2.setComposite(AlphaComposite.SrcOver.derive(0.5f * alpha));
        g2.setColor(SHADOW_COLOR);
        g2.fillRoundRect(x + 2, y + 6, w - 4, h - 4, radius, radius);

        // 2. 玻璃主体：纵向渐变（顶部高光 → 底部偏冷）
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        Paint glass = new LinearGradientPaint(
                x, y, x, y + h,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        new Color(GLASS_TOP.getRed(), GLASS_TOP.getGreen(),
                                GLASS_TOP.getBlue(), 252),
                        new Color(
                                (GLASS_TOP.getRed() + GLASS_BOTTOM.getRed()) / 2,
                                (GLASS_TOP.getGreen() + GLASS_BOTTOM.getGreen()) / 2,
                                (GLASS_TOP.getBlue() + GLASS_BOTTOM.getBlue()) / 2,
                                240),
                        new Color(GLASS_BOTTOM.getRed(), GLASS_BOTTOM.getGreen(),
                                GLASS_BOTTOM.getBlue(), 230)
                });
        g2.setPaint(glass);
        g2.fillRoundRect(x, y, w - 1, h - 1, radius, radius);

        // 3. 顶部内高光（1px 柔光描边）
        g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));
        g2.setColor(EDGE_LIGHT);
        g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);

        // 4. 底部极淡外描边
        g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
        g2.setColor(EDGE_SHADOW);
        g2.drawRoundRect(x, y + 1, w - 1, h - 1, radius, radius);

        g2.setComposite(AlphaComposite.SrcOver.derive(1f));
    }

    /**
     * 绘制控件级玻璃（按钮/输入框）。
     */
    public static void paintControlGlass(Graphics2D g2, int x, int y, int w, int h,
                                         int radius, float alpha, boolean hovered) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        g2.setColor(hovered ? CONTROL_GLASS_HOVER : CONTROL_GLASS_NORMAL);
        g2.fillRoundRect(x, y, w - 1, h - 1, radius, radius);

        // 上边缘高光
        g2.setComposite(AlphaComposite.SrcOver.derive(0.8f));
        g2.setColor(EDGE_LIGHT);
        g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);

        g2.setComposite(AlphaComposite.SrcOver.derive(1f));
    }

    /** 圆角矩形剪裁 */
    public static RoundRectangle2D roundRect(int x, int y, int w, int h, int radius) {
        return new RoundRectangle2D.Double(x, y, w, h, radius, radius);
    }
}
