package ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * 全局视觉主题（浅色）。
 *
 * <p>所有颜色、字号、圆角与尺寸集中在此定义。界面代码只引用这里的常量，
 * 不再各自 {@code setBackground()} / {@code setFont()} 硬编码——这样改一处即全局生效。
 *
 * <p>配色与字号取自需求报告 R-002 的第 2、3、8 轮结论。
 */
public final class Theme {

    // ---------------------------------------------------------------- 品牌色

    /** 主色。白色文字在其上的对比度为 5.52:1，满足 WCAG AA 对正文的要求 */
    public static final Color ACCENT = new Color(0x25, 0x68, 0xBE);
    /** 主色的悬停 / 按下状态 */
    public static final Color ACCENT_DARK = new Color(0x1E, 0x54, 0x96);
    /** 主色在深色背景上的提亮版（登录页左侧品牌区用） */
    public static final Color ACCENT_LIGHT = new Color(0xB8, 0xD4, 0xF0);
    /** 危险操作（删除）。仅用于文字与描边，不做实心填充 */
    public static final Color DANGER = new Color(0xD9, 0x53, 0x4F);
    /** 成功提示文字色 */
    public static final Color SUCCESS = new Color(0x1E, 0x7B, 0x34);

    // ------------------------------------------------------------ 背景与文字

    public static final Color PAGE_BG = new Color(0xF5, 0xF8, 0xFC);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SUBTLE_BG = new Color(0xF5, 0xF7, 0xFA);
    public static final Color CHROME_BG = new Color(0xED, 0xEF, 0xF2);
    /** 表格行鼠标悬停 */
    public static final Color HOVER_BG = new Color(0xF0, 0xF4, 0xF8);
    public static final Color TABLE_HEADER_BG = new Color(0xF2, 0xF4, 0xF6);

    public static final Color TEXT_PRIMARY = new Color(0x1F, 0x1F, 0x1F);
    public static final Color TEXT_HEADING = new Color(0x0F, 0x17, 0x2A);
    public static final Color TEXT_SECONDARY = new Color(0x6B, 0x72, 0x80);
    public static final Color TEXT_ON_ACCENT = Color.WHITE;

    // ---------------------------------------------------------------- 边框

    public static final Color BORDER = new Color(0xDD, 0xE1, 0xE6);
    /** 表格内部分隔线，比 BORDER 更浅 */
    public static final Color BORDER_LIGHT = new Color(0xEE, 0xF0, 0xF2);
    public static final Color BORDER_INPUT = new Color(0xC9, 0xCD, 0xD1);

    // -------------------------------------------------------------- 禁用态

    public static final Color DISABLED_BG = new Color(0xF0, 0xF1, 0xF3);
    public static final Color DISABLED_FG = new Color(0xA8, 0xAD, 0xB5);
    public static final Color DISABLED_BORDER = new Color(0xE0, 0xE2, 0xE6);

    // ---------------------------------------------------------------- 字体

    private static final String FONT_FAMILY = "微软雅黑";

    /** 正文与表格，13px */
    public static final Font FONT_BODY = new Font(FONT_FAMILY, Font.PLAIN, 13);
    /** 页面标题与对话框标题，15px */
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 15);
    /** 应用栏系统名、分组标题，13px 加粗 */
    public static final Font FONT_SUBTITLE = new Font(FONT_FAMILY, Font.BOLD, 13);
    /** 次要说明文字，12px */
    public static final Font FONT_CAPTION = new Font(FONT_FAMILY, Font.PLAIN, 12);
    /** 表格表头，13px 加粗 */
    public static final Font FONT_TABLE_HEADER = new Font(FONT_FAMILY, Font.BOLD, 13);
    /**
     * 登录页品牌名，20px 加粗。
     * 这是字号体系中唯一的展示型例外——登录页是「门面」，系统名需要一定的视觉分量，
     * 用正文级别的 15px 会显得单薄。
     */
    public static final Font FONT_BRAND = new Font(FONT_FAMILY, Font.BOLD, 20);
    /**
     * 概览页指标卡上的数字，22px 加粗。
     * 与 FONT_BRAND 同属展示型例外——指标数字是概览页唯一需要「一眼看到」的内容，
     * 用正文级别字号会让页面失去重心。
     */
    public static final Font FONT_METRIC = new Font(FONT_FAMILY, Font.BOLD, 22);

    // ---------------------------------------------------------------- 尺寸

    public static final int RADIUS = 8;
    public static final int RADIUS_SMALL = 6;
    public static final int TABLE_ROW_HEIGHT = 38;
    public static final int SIDEBAR_WIDTH = 120;
    public static final int SIDEBAR_COLLAPSED_WIDTH = 38;
    public static final int ICON_SIZE = 15;

    public static final int LOGIN_WIDTH = 760;
    public static final int LOGIN_HEIGHT = 460;
    public static final int MAIN_WIDTH = 1100;
    public static final int MAIN_HEIGHT = 700;

    private Theme() {
    }

    /** 必须在创建任何 Swing 组件之前调用 */
    public static void init() {
        FlatLightLaf.setup();

        // 圆角与字体
        UIManager.put("Component.arc", RADIUS);
        UIManager.put("Button.arc", RADIUS_SMALL);
        UIManager.put("TextComponent.arc", RADIUS);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("defaultFont", FONT_BODY);

        // 强调色与边框
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("Component.borderColor", BORDER_INPUT);

        // 按钮禁用态（对应 R-001 权限控制的置灰）
        UIManager.put("Button.disabledBackground", DISABLED_BG);
        UIManager.put("Button.disabledText", DISABLED_FG);
        UIManager.put("Button.disabledBorderColor", DISABLED_BORDER);

        // 表格
        UIManager.put("Table.rowHeight", TABLE_ROW_HEIGHT);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", BORDER_LIGHT);
        UIManager.put("Table.selectionBackground", ACCENT);
        UIManager.put("Table.selectionForeground", TEXT_ON_ACCENT);
        UIManager.put("Table.hoverBackground", HOVER_BG);
        UIManager.put("Table.headerBackground", TABLE_HEADER_BG);
        UIManager.put("Table.headerForeground", new Color(0x3C, 0x40, 0x43));
        UIManager.put("Table.headerSeparatorColor", BORDER_LIGHT);

        // 滚动条与标签页
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorColor", BORDER_LIGHT);
    }
}
