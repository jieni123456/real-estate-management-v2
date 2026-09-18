package ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 侧边栏图标。
 *
 * <p>按 R-002 第 8 轮决定，仅侧边栏使用图标，工具栏按钮保持纯文字。
 *
 * <p>图标用 Java2D 直接绘制，不引入外部图标资源，因此也无需为不同主题准备多套
 * 图片——颜色在绘制时传入即可。
 */
public final class Icons {

    private Icons() {
    }

    /** 房屋图标，用于「房屋管理」导航项 */
    public static Icon house(Color color, int size) {
        return new NavIcon(color, size, Kind.HOUSE);
    }

    /** 人物图标，用于「客户管理」导航项 */
    public static Icon person(Color color, int size) {
        return new NavIcon(color, size, Kind.PERSON);
    }

    /** 四方格图标，用于「系统概览」导航项 */
    public static Icon dashboard(Color color, int size) {
        return new NavIcon(color, size, Kind.DASHBOARD);
    }

    /** 日历图标，用于「带看记录」导航项 */
    public static Icon calendar(Color color, int size) {
        return new NavIcon(color, size, Kind.CALENDAR);
    }

    private enum Kind {
        HOUSE, PERSON, DASHBOARD, CALENDAR
    }

    /**
     * 线描导航图标。绘制坐标按 24 × 24 的画布设计，再按目标尺寸等比缩放，
     * 这样在不同尺寸下都是同一套比例。
     */
    private static final class NavIcon implements Icon {

        private final Color color;
        private final int size;
        private final Kind kind;

        private NavIcon(Color color, int size, Kind kind) {
            this.color = color;
            this.size = size;
            this.kind = kind;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(Math.max(1.2f, size / 11f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                double scale = size / 24.0;
                switch (kind) {
                    case HOUSE:
                        paintHouse(g2, scale);
                        break;
                    case PERSON:
                        paintPerson(g2, scale);
                        break;
                    case CALENDAR:
                        paintCalendar(g2, scale);
                        break;
                    default:
                        paintDashboard(g2, scale);
                        break;
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintHouse(Graphics2D g2, double s) {
            Path2D roof = new Path2D.Double();
            roof.moveTo(3 * s, 10.5 * s);
            roof.lineTo(12 * s, 3 * s);
            roof.lineTo(21 * s, 10.5 * s);
            g2.draw(roof);

            Path2D body = new Path2D.Double();
            body.moveTo(5.5 * s, 9.2 * s);
            body.lineTo(5.5 * s, 20 * s);
            body.lineTo(18.5 * s, 20 * s);
            body.lineTo(18.5 * s, 9.2 * s);
            g2.draw(body);
        }

        private void paintPerson(Graphics2D g2, double s) {
            g2.draw(new Ellipse2D.Double(8.6 * s, 4.6 * s, 6.8 * s, 6.8 * s));

            Path2D shoulders = new Path2D.Double();
            shoulders.moveTo(5.5 * s, 20 * s);
            shoulders.curveTo(5.5 * s, 14.2 * s, 18.5 * s, 14.2 * s, 18.5 * s, 20 * s);
            g2.draw(shoulders);
        }

        /** 四方格，象征「总览」。比画图表更简单，小尺寸下也更清晰 */
        private void paintDashboard(Graphics2D g2, double s) {
            double box = 6.4 * s;
            double gap = 2.8 * s;
            double origin = 4.2 * s;

            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 2; column++) {
                    g2.draw(new RoundRectangle2D.Double(
                            origin + column * (box + gap),
                            origin + row * (box + gap),
                            box, box, 2.0 * s, 2.0 * s));
                }
            }
        }

        /** 日历：外框 + 顶栏加粗 + 两个挂环，象征「带看记录」按时间发生 */
        private void paintCalendar(Graphics2D g2, double s) {
            // 外框。顶部留出挂环空间
            g2.draw(new RoundRectangle2D.Double(4 * s, 6 * s, 16 * s, 14.5 * s, 2.5 * s, 2.5 * s));
            // 顶栏分隔线
            g2.draw(new java.awt.geom.Line2D.Double(4 * s, 10.5 * s, 20 * s, 10.5 * s));
            // 两个挂环
            g2.draw(new java.awt.geom.Line2D.Double(8.5 * s, 3.5 * s, 8.5 * s, 7.5 * s));
            g2.draw(new java.awt.geom.Line2D.Double(15.5 * s, 3.5 * s, 15.5 * s, 7.5 * s));
            // 一个日期点，避免整块看起来是空的
            g2.fill(new RoundRectangle2D.Double(8 * s, 14 * s, 3 * s, 3 * s, 1 * s, 1 * s));
        }
    }
}
