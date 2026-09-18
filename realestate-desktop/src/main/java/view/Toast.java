package view;

import ui.Theme;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Path2D;

/**
 * 轻提示（Toast）。
 *
 * <p>对应需求报告 R-002 第 9 轮：把反馈分为两类——「知道了就行的」用轻提示，
 * 「必须确认的」才用模态框。轻提示浮在窗口右下角，约 2 秒后自动淡出，不打断操作。
 *
 * <p>它直接挂在窗口的 {@link JLayeredPane} 上，不额外创建窗口，因此不需要处理
 * 窗口透明与多屏定位问题；同一时刻只保留一条，新的会顶掉旧的。
 */
public final class Toast {

    /** 完全显示后停留的时长 */
    private static final int HOLD_MS = 2000;
    /** 动画帧间隔 */
    private static final int TICK_MS = 25;
    private static final int FADE_IN_TICKS = 8;
    private static final int FADE_OUT_TICKS = 12;
    private static final int MARGIN_RIGHT = 24;
    private static final int MARGIN_BOTTOM = 52;

    private Toast() {
    }

    /** 操作成功。右下角主色轻提示。source 传窗口内任意组件即可 */
    public static void success(Component source, String message) {
        show(source, message, Theme.ACCENT);
    }

    /** 操作失败。右下角红色轻提示 */
    public static void error(Component source, String message) {
        show(source, message, Theme.DANGER);
    }

    private static void show(Component source, String message, Color background) {
        if (source == null || message == null || message.isEmpty()) {
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(source);
        if (!(window instanceof JFrame) || !window.isShowing()) {
            return;
        }

        JLayeredPane layered = ((JFrame) window).getLayeredPane();

        // 顶掉已存在的提示，避免多条叠加
        for (Component component : layered.getComponents()) {
            if (component instanceof Bubble) {
                layered.remove(component);
            }
        }

        Bubble bubble = new Bubble(message, background);
        Dimension size = bubble.getPreferredSize();
        int x = layered.getWidth() - size.width - MARGIN_RIGHT;
        int y = layered.getHeight() - size.height - MARGIN_BOTTOM;
        bubble.setBounds(x, Math.max(0, y), size.width, size.height);
        bubble.setAlpha(0f);
        layered.add(bubble, JLayeredPane.POPUP_LAYER);

        final int holdTicks = HOLD_MS / TICK_MS;
        final int totalTicks = FADE_IN_TICKS + holdTicks + FADE_OUT_TICKS;
        final int[] tick = {0};

        Timer timer = new Timer(TICK_MS, null);
        timer.addActionListener(e -> {
            int current = tick[0]++;

            if (current >= totalTicks) {
                timer.stop();
                layered.remove(bubble);
                layered.repaint();
                return;
            }

            float alpha;
            if (current < FADE_IN_TICKS) {
                alpha = (float) current / FADE_IN_TICKS;
            } else if (current < FADE_IN_TICKS + holdTicks) {
                alpha = 1f;
            } else {
                alpha = 1f - (float) (current - FADE_IN_TICKS - holdTicks) / FADE_OUT_TICKS;
            }

            bubble.setAlpha(Math.max(0f, Math.min(1f, alpha)));
        });
        timer.start();
    }

    /** 圆角气泡本体。用 AlphaComposite 控制淡入淡出 */
    private static final class Bubble extends JPanel {

        private static final int HEIGHT = 38;
        private static final int PADDING_LEFT = 14;
        private static final int CHECK_WIDTH = 11;
        private static final int CHECK_GAP = 8;
        private static final int PADDING_RIGHT = 16;

        private final String message;
        private final Color background;
        private float alpha = 1f;

        private Bubble(String message, Color background) {
            this.message = message;
            this.background = background;
            setOpaque(false);

            FontMetrics metrics = getFontMetrics(Theme.FONT_BODY);
            int width = PADDING_LEFT + CHECK_WIDTH + CHECK_GAP
                    + metrics.stringWidth(message) + PADDING_RIGHT;
            setPreferredSize(new Dimension(Math.max(120, width), HEIGHT));
        }

        private void setAlpha(float alpha) {
            this.alpha = alpha;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                int centerY = getHeight() / 2;

                // 对勾图标
                int iconX = PADDING_LEFT;
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Double();
                check.moveTo(iconX, centerY + 1);
                check.lineTo(iconX + 4, centerY + 5);
                check.lineTo(iconX + CHECK_WIDTH, centerY - 4);
                g2.draw(check);

                // 文案
                g2.setFont(Theme.FONT_BODY);
                g2.setColor(Color.WHITE);
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
                g2.drawString(message, iconX + CHECK_WIDTH + CHECK_GAP, baseline);
            } finally {
                g2.dispose();
            }
        }
    }
}
