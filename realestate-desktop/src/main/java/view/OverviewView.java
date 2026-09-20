package view;

import controller.LogController;
import controller.StatsController;
import model.OperationLog;
import model.Overview;
import util.DataAccessException;
import ui.Theme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * 系统概览页。对应需求报告 G-009。
 *
 * <p>五张指标卡（房源 / 客户 / 房东 / 带看 / 空置）+ 户型分布图 + 最近操作。
 * 之所以把它做成登录后的默认落地页：几个数字一眼可见，比直接扔一张空表格
 * 更能说明「这个系统里有什么」。
 *
 * <p>本页是只读统计，不涉及权限与数据变更。
 */
public class OverviewView extends JPanel {

    /** 概览页展示最近几条操作（G-017） */
    private static final int RECENT_LOG_LIMIT = 5;

    private final StatsController statsController;
    private final LogController logController;
    private final Consumer<String> statusReporter;

    private final StatCard houseCard = new StatCard("房源总数", Theme.METRIC_HOUSE);
    private final StatCard customerCard = new StatCard("客户总数", Theme.METRIC_CUSTOMER);
    private final StatCard landlordCard = new StatCard("房东总数", Theme.METRIC_LANDLORD);
    private final StatCard viewingCard = new StatCard("带看记录", Theme.METRIC_VIEWING);
    /** R-003：原「平均面积」卡已换成「空置房源」——空置数最接近这个系统的「库存」 */
    private final StatCard vacantCard = new StatCard("空置房源", Theme.METRIC_VACANT);

    private final DistributionPanel distribution = new DistributionPanel();
    private final ActivityPanel activity = new ActivityPanel();

    public OverviewView(StatsController statsController, LogController logController,
                        Consumer<String> statusReporter) {
        this.statsController = statsController;
        this.logController = logController;
        this.statusReporter = statusReporter;

        setLayout(new BorderLayout());
        setBackground(Theme.PAGE_BG);
        setBorder(new EmptyBorder(18, 18, 18, 18));

        add(createContent(), BorderLayout.NORTH);
        refresh();
    }

    private JPanel createContent() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createTitleRow());
        content.add(Box.createVerticalStrut(14));
        content.add(createCardRow());
        content.add(Box.createVerticalStrut(18));
        content.add(distribution);
        content.add(Box.createVerticalStrut(18));
        content.add(activity);
        return content;
    }

    private JPanel createTitleRow() {
        JLabel title = new JLabel("系统概览");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_HEADING);

        JButton refreshButton = new JButton("刷新数据");
        refreshButton.setFont(Theme.FONT_BODY);
        refreshButton.setBackground(Theme.SURFACE);
        refreshButton.setForeground(Theme.TEXT_PRIMARY);
        refreshButton.setBorder(new javax.swing.border.CompoundBorder(
                new javax.swing.border.LineBorder(Theme.BORDER_INPUT, 1, true),
                new EmptyBorder(5, 14, 5, 14)));
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshButton.addActionListener(e -> {
            refresh();
            Toast.success(this, "统计数据已刷新");
        });

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.add(title, BorderLayout.WEST);
        row.add(refreshButton, BorderLayout.EAST);
        return row;
    }

    private JPanel createCardRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 14, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        row.setPreferredSize(new Dimension(400, 96));
        row.add(houseCard);
        row.add(customerCard);
        row.add(landlordCard);
        row.add(viewingCard);
        row.add(vacantCard);
        return row;
    }

    // ------------------------------------------------------------ 数据加载

    /**
     * 重新读取统计数据与最近操作。切换到本页时由 MainView 调用。
     *
     * <p>读取失败时把卡片显示为占位符并提示，而不是让异常冒到事件分发线程上——
     * 那样整个界面会直接卡死（对应需求报告 G-012）。
     *
     * <p>最近操作单独 try：它与统计是两件互不相干的事，任何一方读不出来
     * 都不该把另一方也拖下水。
     */
    public void refresh() {
        try {
            Overview data = statsController.loadOverview();

            houseCard.setValue(data.getHouseCount() + " 套");
            customerCard.setValue(data.getCustomerCount() + " 位");
            landlordCard.setValue(data.getLandlordCount() + " 位");
            viewingCard.setValue(data.getViewingCount() + " 次");
            vacantCard.setValue(data.getVacantCount() + " 套");

            distribution.setData(data.getTypeCounts());

            if (statusReporter != null) {
                statusReporter.accept("共 " + data.getHouseCount() + " 套房屋（空置 "
                        + data.getVacantCount() + " 套）、"
                        + data.getCustomerCount() + " 位客户、"
                        + data.getViewingCount() + " 条带看记录");
            }
        } catch (DataAccessException e) {
            houseCard.setValue("—");
            customerCard.setValue("—");
            landlordCard.setValue("—");
            viewingCard.setValue("—");
            vacantCard.setValue("—");
            distribution.setData(List.of());
            if (statusReporter != null) {
                statusReporter.accept("统计数据读取失败");
            }
            JOptionPane.showMessageDialog(this, e.userMessage(),
                    "读取失败", JOptionPane.ERROR_MESSAGE);
        }

        try {
            activity.setData(logController.getRecent(RECENT_LOG_LIMIT));
        } catch (DataAccessException e) {
            // 日志读不出来 ≠ 没有操作记录，所以清空并给出提示，而不是静静地显示「暂无」
            activity.setData(List.of());
            activity.setFailed(true);
            if (statusReporter != null) {
                statusReporter.accept("操作日志读取失败");
            }
        }
    }

    // ------------------------------------------------------------ 指标卡

    /** 单张指标卡：白底圆角 + 左侧强调色短条 + 「标题 / 数值」两行 */
    private static final class StatCard extends JPanel {

        private final JLabel valueLabel = new JLabel("—");
        private final Color accent;

        private StatCard(String caption, Color accent) {
            this.accent = accent;

            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(16, 18, 16, 18));

            JLabel captionLabel = new JLabel(caption);
            captionLabel.setFont(Theme.FONT_CAPTION);
            captionLabel.setForeground(Theme.TEXT_SECONDARY);
            captionLabel.setAlignmentX(LEFT_ALIGNMENT);

            valueLabel.setFont(Theme.FONT_METRIC);
            valueLabel.setForeground(Theme.TEXT_HEADING);
            valueLabel.setAlignmentX(LEFT_ALIGNMENT);

            add(captionLabel);
            add(Box.createVerticalStrut(6));
            add(valueLabel);
        }

        private void setValue(String text) {
            valueLabel.setText(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(Theme.SURFACE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));

                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, 12, 12));

                // 左侧强调短条。只占垂直中线一段，避免与卡片圆角相交
                int tickHeight = 30;
                g2.setColor(accent);
                g2.fill(new RoundRectangle2D.Double(0, (getHeight() - tickHeight) / 2.0,
                        3, tickHeight, 3, 3));
            } finally {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------ 户型分布

    /** 横向条形图。用图形而不是另一种表格，让这一页不至于像「又一张表」 */
    private static final class DistributionPanel extends JPanel {

        private static final int HEADER_H = 44;
        private static final int ROW_H = 32;
        private static final int LABEL_W = 100;
        private static final int VALUE_W = 54;
        private static final int PADDING = 18;

        private List<Overview.TypeCount> data = List.of();
        private int max;

        private DistributionPanel() {
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            updateHeight();
        }

        private void setData(List<Overview.TypeCount> data) {
            this.data = data == null ? List.of() : data;
            this.max = 0;
            for (Overview.TypeCount item : this.data) {
                max = Math.max(max, item.getCount());
            }
            updateHeight();
        }

        /** 高度随行数变化；在 BoxLayout 中必须同时限制最大高度，否则会被拉满 */
        private void updateHeight() {
            int rows = Math.max(1, data.size());
            int height = HEADER_H + rows * ROW_H + 14;
            setPreferredSize(new Dimension(400, height));
            setMinimumSize(new Dimension(240, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(Theme.SURFACE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, 12, 12));

                g2.setFont(Theme.FONT_SUBTITLE);
                g2.setColor(Theme.TEXT_HEADING);
                g2.drawString("户型分布", PADDING, 27);

                if (data.isEmpty()) {
                    g2.setFont(Theme.FONT_CAPTION);
                    g2.setColor(Theme.TEXT_SECONDARY);
                    g2.drawString("暂无房屋数据", PADDING, HEADER_H + 16);
                    return;
                }

                paintBars(g2);
            } finally {
                g2.dispose();
            }
        }

        private void paintBars(Graphics2D g2) {
            int trackLeft = PADDING + LABEL_W;
            int trackRight = getWidth() - PADDING - VALUE_W;
            int trackWidth = Math.max(40, trackRight - trackLeft);
            int barHeight = 10;

            for (int i = 0; i < data.size(); i++) {
                Overview.TypeCount item = data.get(i);
                int centerY = HEADER_H + i * ROW_H + ROW_H / 2;

                // 户型名
                g2.setFont(Theme.FONT_BODY);
                g2.setColor(Theme.TEXT_PRIMARY);
                FontMetrics labelMetrics = g2.getFontMetrics();
                String label = fit(g2, item.getType(), LABEL_W - 10);
                g2.drawString(label, PADDING,
                        centerY + (labelMetrics.getAscent() - labelMetrics.getDescent()) / 2);

                // 轨道
                int barY = centerY - barHeight / 2;
                g2.setColor(Theme.BORDER_LIGHT);
                g2.fill(new RoundRectangle2D.Double(trackLeft, barY, trackWidth, barHeight,
                        barHeight, barHeight));

                // 已填充部分。数量不为 0 时至少留一个圆头，避免短条看起来像没有数据
                int filled = max == 0 ? 0 : (int) Math.round(trackWidth * (item.getCount() / (double) max));
                if (item.getCount() > 0) {
                    filled = Math.max(barHeight, filled);
                }
                if (filled > 0) {
                    g2.setColor(Theme.ACCENT);
                    g2.fill(new RoundRectangle2D.Double(trackLeft, barY, filled, barHeight,
                            barHeight, barHeight));
                }

                // 数量
                g2.setFont(Theme.FONT_CAPTION);
                g2.setColor(Theme.TEXT_SECONDARY);
                FontMetrics countMetrics = g2.getFontMetrics();
                String count = item.getCount() + " 套";
                g2.drawString(count, getWidth() - PADDING - countMetrics.stringWidth(count),
                        centerY + (countMetrics.getAscent() - countMetrics.getDescent()) / 2);
            }
        }

        /** 户型名过长时截断加省略号，避免压到右侧条形 */
        private String fit(Graphics2D g2, String text, int maxWidth) {
            if (text == null) {
                return "";
            }
            if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
                return text;
            }
            int end = text.length();
            while (end > 0 && g2.getFontMetrics().stringWidth(text.substring(0, end) + "…") > maxWidth) {
                end--;
            }
            return text.substring(0, end) + "…";
        }
    }

    // ------------------------------------------------------------ 最近操作

    /**
     * 最近操作列表（G-017）。
     *
     * <p>日志若只写进数据库而无人查看，等于没做。这里把它做成「看得见」的一块，
     * 样式与户型分布卡保持一致。
     */
    private static final class ActivityPanel extends JPanel {

        private static final int HEADER_H = 44;
        private static final int ROW_H = 28;
        private static final int PADDING = 18;

        private List<OperationLog> data = List.of();
        private boolean failed;

        private ActivityPanel() {
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            updateHeight();
        }

        private void setData(List<OperationLog> data) {
            this.data = data == null ? List.of() : data;
            this.failed = false;
            updateHeight();
        }

        /** 标记为「读取失败」。与「暂无操作记录」是两种不同的空，必须分开展示 */
        private void setFailed(boolean failed) {
            this.failed = failed;
            updateHeight();
        }

        /** 高度随行数变化；在 BoxLayout 中必须同时限制最大高度，否则会被拉满 */
        private void updateHeight() {
            int rows = Math.max(1, data.size());
            int height = HEADER_H + rows * ROW_H + 14;
            setPreferredSize(new Dimension(400, height));
            setMinimumSize(new Dimension(240, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(Theme.SURFACE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, 12, 12));

                g2.setFont(Theme.FONT_SUBTITLE);
                g2.setColor(Theme.TEXT_HEADING);
                g2.drawString("最近操作", PADDING, 27);

                if (data.isEmpty()) {
                    g2.setFont(Theme.FONT_CAPTION);
                    g2.setColor(failed ? Theme.DANGER : Theme.TEXT_SECONDARY);
                    g2.drawString(failed ? "操作日志读取失败" : "暂无操作记录",
                            PADDING, HEADER_H + 14);
                    return;
                }

                g2.setFont(Theme.FONT_BODY);
                g2.setColor(Theme.TEXT_PRIMARY);
                FontMetrics metrics = g2.getFontMetrics();
                int maxWidth = getWidth() - PADDING * 2;

                for (int i = 0; i < data.size(); i++) {
                    int baseline = HEADER_H + i * ROW_H
                            + (ROW_H + metrics.getAscent() - metrics.getDescent()) / 2;
                    g2.drawString(fit(g2, data.get(i).toLine(), maxWidth), PADDING, baseline);
                }
            } finally {
                g2.dispose();
            }
        }

        /** 内容过长时截断加省略号，避免画到卡片外面去 */
        private String fit(Graphics2D g2, String text, int maxWidth) {
            if (text == null) {
                return "";
            }
            if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
                return text;
            }
            int end = text.length();
            while (end > 0 && g2.getFontMetrics().stringWidth(text.substring(0, end) + "…") > maxWidth) {
                end--;
            }
            return text.substring(0, end) + "…";
        }
    }
}
