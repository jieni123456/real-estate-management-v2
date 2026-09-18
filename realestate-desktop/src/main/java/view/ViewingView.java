package view;

import controller.ViewingController;
import model.Customer;
import model.House;
import model.Viewing;
import util.CsvExporter;
import util.DataAccessException;
import util.Formats;
import util.Result;
import util.SearchMatcher;
import ui.Theme;
import util.ViewingRules;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * 带看记录页面。对应需求报告 G-008。
 *
 * <p>结构与房屋页、客户页保持一致：搜索框 + 可排序表格 + 增改删刷新导出。
 * 对话框里客户与房屋都改为下拉选择——写带看记录时人是记不住那些 ID 的，
 * 而且这里同时也是录入新数据，必须能选到已有的客户和房源。
 */
public class ViewingView extends JPanel {

    private static final String[] COLUMNS =
            {"客户ID", "客户姓名", "房屋ID", "地址", "带看时间", "结果", "备注"};

    private final ViewingController viewingController;
    private final Consumer<String> statusReporter;

    private final JTable viewingTable;
    private final JTextField searchField = new JTextField(16);
    private final JButton editButton = new JButton("编辑带看");
    private final JButton deleteButton = new JButton("删除带看");

    /** 数据库中的全部带看记录 */
    private List<Viewing> allViewings = new ArrayList<>();
    /** 按搜索框筛选后、与表格行一一对应的数据 */
    private List<Viewing> visibleViewings = new ArrayList<>();

    public ViewingView(ViewingController viewingController, Consumer<String> statusReporter) {
        this.viewingController = viewingController;
        this.statusReporter = statusReporter;

        setLayout(new BorderLayout());
        setBackground(Theme.PAGE_BG);
        setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = createHeader();
        viewingTable = createTable();

        JScrollPane scrollPane = new JScrollPane(viewingTable);
        scrollPane.setBorder(new LineBorder(Theme.BORDER, 1, true));
        scrollPane.getViewport().setBackground(Theme.SURFACE);

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        applyPermissions();
        refresh();
    }

    // ------------------------------------------------------------ 顶部区域

    private JPanel createHeader() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("带看记录");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_HEADING);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);

        JButton addButton = primaryButton("登记带看");
        addButton.addActionListener(e -> showViewingDialog(null));

        editButton.setFont(Theme.FONT_BODY);
        editButton.setFocusPainted(false);
        editButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editButton.addActionListener(e -> editSelectedViewing());
        styleAsSecondary(editButton);

        deleteButton.setFont(Theme.FONT_BODY);
        deleteButton.setFocusPainted(false);
        deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        deleteButton.addActionListener(e -> deleteSelectedViewing());

        JButton refreshButton = secondaryOutlineButton("刷新数据");
        refreshButton.addActionListener(e -> refresh());

        JButton exportButton = secondaryOutlineButton("导出 CSV");
        exportButton.addActionListener(e -> exportCsv());

        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);
        buttons.add(exportButton);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(buttons, BorderLayout.WEST);
        toolbar.add(createSearchBox(), BorderLayout.EAST);

        top.add(title, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.SOUTH);
        return top;
    }

    /** 关键字搜索框（复用 G-004 的能力）。输入即筛选，按 Esc 清空 */
    private JPanel createSearchBox() {
        searchField.setFont(Theme.FONT_BODY);
        searchField.setPreferredSize(new Dimension(220, 30));
        searchField.putClientProperty("JTextField.placeholderText", "搜索 客户 / 房屋 / 结果 / 备注");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.setToolTipText("空格分隔多个关键字，需全部命中；按 Esc 清空");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        searchField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
        searchField.getActionMap().put("clearSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
            }
        });

        JPanel box = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        box.setOpaque(false);
        box.add(searchField);
        return box;
    }

    private JTable createTable() {
        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            /**
             * 全部声明为文本。带看时间的格式是 yyyy-MM-dd HH:mm，
             * 字典序恰好等于时间序，因此不需要像面积那样特殊处理。
             */
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        };

        JTable table = new JTable(model);
        table.setFont(Theme.FONT_BODY);
        table.setRowHeight(Theme.TABLE_ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Theme.BORDER_LIGHT);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setBackground(Theme.SURFACE);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Theme.TEXT_ON_ACCENT);

        table.setAutoCreateRowSorter(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.FONT_TABLE_HEADER);
        header.setBackground(Theme.TABLE_HEADER_BG);
        header.setForeground(new Color(0x3C, 0x40, 0x43));
        header.setReorderingAllowed(false);
        Dimension headerSize = header.getPreferredSize();
        header.setPreferredSize(new Dimension(headerSize.width, 38));
        return table;
    }

    // ------------------------------------------------------------ 数据加载

    /** 重新读取并刷新表格。读取失败在此提示——空表格与「读不出来」必须区分 */
    public void refresh() {
        try {
            allViewings = viewingController.getAllViewings();
        } catch (DataAccessException e) {
            allViewings = new ArrayList<>();
            showError(this, "读取失败", e.userMessage());
        }
        applyFilter();
    }

    private void applyFilter() {
        String keyword = searchField.getText();

        visibleViewings = new ArrayList<>();
        for (Viewing viewing : allViewings) {
            if (SearchMatcher.matches(keyword,
                    viewing.getCustomerId(), viewing.getCustomerName(),
                    viewing.getHouseId(), viewing.getHouseAddress(),
                    viewing.getResult(), viewing.getNote())) {
                visibleViewings.add(viewing);
            }
        }

        rebuildTable();
        reportStatus();
    }

    private void rebuildTable() {
        DefaultTableModel model = (DefaultTableModel) viewingTable.getModel();
        model.setRowCount(0);
        for (Viewing viewing : visibleViewings) {
            model.addRow(new Object[]{
                    viewing.getCustomerId(),
                    viewing.getCustomerName(),
                    viewing.getHouseId(),
                    viewing.getHouseAddress(),
                    Formats.dateTime(viewing.getViewedAt()),
                    viewing.getResult(),
                    viewing.getNote()
            });
        }
    }

    private void reportStatus() {
        if (statusReporter == null) {
            return;
        }

        if (SearchMatcher.isBlank(searchField.getText())) {
            statusReporter.accept("共 " + allViewings.size() + " 条带看记录");
        } else if (visibleViewings.isEmpty()) {
            statusReporter.accept("未找到匹配的带看记录（共 " + allViewings.size() + " 条）");
        } else {
            statusReporter.accept("筛选出 " + visibleViewings.size() + " 条 / 共 "
                    + allViewings.size() + " 条带看记录");
        }
    }

    // ------------------------------------------------------------ 权限控制

    /**
     * 按当前用户权限启用或置灰「删除带看」按钮。
     *
     * <p>与房屋页同理：本视图在登录之前就被构造，那时 Session 为空、按钮必然禁用，
     * 因此登录成功后必须由 MainView 再次调用本方法。
     */
    public void applyPermissions() {
        boolean allowed = viewingController.canDelete();
        deleteButton.setEnabled(allowed);

        if (allowed) {
            deleteButton.setForeground(Theme.DANGER);
            deleteButton.setBackground(Theme.SURFACE);
            deleteButton.setBorder(outlineBorder(Theme.DANGER));
            deleteButton.setToolTipText(null);
        } else {
            deleteButton.setForeground(Theme.DISABLED_FG);
            deleteButton.setBackground(Theme.DISABLED_BG);
            deleteButton.setBorder(outlineBorder(Theme.DISABLED_BORDER));
            deleteButton.setToolTipText("需要管理员权限");
        }
        deleteButton.repaint();
    }

    // ------------------------------------------------------------ 编辑 / 删除

    private void editSelectedViewing() {
        Viewing selected = getSelectedViewing("编辑");
        if (selected == null) {
            return;
        }
        showViewingDialog(selected);
    }

    private void deleteSelectedViewing() {
        Viewing selected = getSelectedViewing("删除");
        if (selected == null) {
            return;
        }

        Object[] options = {"取消", "确认删除"};
        int choice = JOptionPane.showOptionDialog(this,
                "确定要删除「" + selected.getCustomerName() + " 看 "
                        + selected.getHouseAddress() + "（" + Formats.dateTime(selected.getViewedAt())
                        + "）」这条记录吗？此操作不可撤销。",
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]); // 默认焦点在「取消」，避免习惯性回车误删

        if (choice != 1) {
            return;
        }

        Result result = viewingController.deleteViewing(selected.getId());
        if (result.isSuccess()) {
            Toast.success(this, result.getMessage());
            refresh();
        } else {
            warn(this, result.getMessage());
        }
    }

    /** 取当前选中的记录。行号经 convertRowIndexToModel 换算，排序后不会取错行 */
    private Viewing getSelectedViewing(String action) {
        int viewRow = viewingTable.getSelectedRow();
        if (viewRow == -1) {
            warn(this, "请先选择要" + action + "的带看记录");
            return null;
        }

        int modelRow = viewingTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= visibleViewings.size()) {
            warn(this, "数据已发生变化，请重新选择");
            refresh();
            return null;
        }
        return visibleViewings.get(modelRow);
    }

    // -------------------------------------------------------- 登记 / 编辑对话框

    /**
     * 登记与编辑共用一个对话框。
     *
     * @param existing 为 null 表示新登记；否则为编辑
     */
    private void showViewingDialog(Viewing existing) {
        final boolean editing = existing != null;

        // 客户与房源是这条记录的必填关联，两者缺一都登记不了
        List<Customer> customers;
        List<House> allHouses;
        try {
            customers = viewingController.getAllCustomers();
            allHouses = viewingController.getAllHouses();
        } catch (DataAccessException e) {
            showError(this, "读取失败", e.userMessage());
            return;
        }
        if (customers.isEmpty()) {
            warn(this, "请先添加客户与房屋，才能登记带看记录");
            return;
        }

        // G-020：已租出的房子不能再登记带看，所以下拉里只放空置房源。
        // 唯一的例外是编辑时这条记录原本挂着的房源——房子后来租出去了，
        // 也得允许把备注改完，否则这条记录就动不了了。
        // 过滤规则由 ViewingRules 统一提供，与控制器里的兜底拦截是同一个定义
        List<House> selectableHouses = ViewingRules.selectable(allHouses,
                editing ? existing.getHouseId() : null);
        if (selectableHouses.isEmpty()) {
            warn(this, "当前没有空置房源，无法登记带看。\n"
                    + "已租出的房源如需重新带看，请先到「房屋管理」把它改回「空置」。");
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                editing ? "编辑带看记录" : "登记带看", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JLabel title = new JLabel(editing ? "编辑带看记录" : "登记带看");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_HEADING);
        title.setBorder(new EmptyBorder(18, 20, 0, 20));
        dialog.add(title, BorderLayout.NORTH);

        JComboBox<Object> customerBox = new JComboBox<>();
        customerBox.setFont(Theme.FONT_BODY);
        customerBox.setMaximumRowCount(12);
        customerBox.setRenderer(new NamedRenderer());
        for (Customer customer : customers) {
            customerBox.addItem(customer);
        }

        JComboBox<Object> houseBox = new JComboBox<>();
        houseBox.setFont(Theme.FONT_BODY);
        houseBox.setMaximumRowCount(12);
        houseBox.setRenderer(new NamedRenderer());
        houseBox.setToolTipText("只列出空置房源。已租出的房源需先改回「空置」才能登记带看");
        for (House house : selectableHouses) {
            houseBox.addItem(house);
        }

        // 日期时间选择器：用 JSpinner 而不是文本框手输日期，
        // 省掉一套「格式对不对」的校验，也不会出现 2026-13-45 这种输入
        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        dateSpinner.setFont(Theme.FONT_BODY);
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, Formats.DATE_TIME_PATTERN));
        dateSpinner.setPreferredSize(new Dimension(190, 30));

        JComboBox<String> resultBox = new JComboBox<>(Viewing.RESULTS);
        resultBox.setFont(Theme.FONT_BODY);
        resultBox.setPreferredSize(new Dimension(190, 30));

        JTextField note = new JTextField();

        if (editing) {
            selectById(customerBox, existing.getCustomerId(), true);
            selectById(houseBox, existing.getHouseId(), false);
            dateSpinner.setValue(toDate(existing.getViewedAt()));
            resultBox.setSelectedItem(existing.getResult());
            note.setText(existing.getNote());
        } else {
            dateSpinner.setValue(new Date());
        }

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 20, 0, 20));
        body.add(groupLabel("带看信息"));
        body.add(twoColumnForm(
                new String[]{"客户", "房屋"},
                new JComponent[]{customerBox, houseBox}));
        body.add(twoColumnForm(
                new String[]{"带看时间", "带看结果"},
                new JComponent[]{dateSpinner, resultBox}));
        body.add(twoColumnForm(
                new String[]{"备注", ""},
                new JComponent[]{note, new JLabel()}));

        dialog.add(body, BorderLayout.CENTER);

        JButton cancel = secondaryOutlineButton("取消");
        cancel.addActionListener(e -> dialog.dispose());

        JButton submit = primaryButton(editing ? "保存" : "提交");
        submit.addActionListener(e -> {
            String customerId = idOf(customerBox.getSelectedItem(), true);
            String houseId = idOf(houseBox.getSelectedItem(), false);
            LocalDateTime viewedAt = toLocalDateTime((Date) dateSpinner.getValue());
            String result = (String) resultBox.getSelectedItem();

            Result outcome = editing
                    ? viewingController.updateViewing(existing.getId(), customerId, houseId,
                            viewedAt, result, note.getText())
                    : viewingController.addViewing(customerId, houseId,
                            viewedAt, result, note.getText());

            if (outcome.isSuccess()) {
                Toast.success(this, outcome.getMessage());
                refresh();
                dialog.dispose();
                noticeIfStatusNotReverted(editing, existing, result);
            } else {
                warn(dialog, outcome.getMessage());
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(20, 20, 18, 20));
        actions.add(cancel);
        actions.add(submit);
        dialog.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** 在下拉里按 ID 选中某项 */
    private void selectById(JComboBox<Object> box, String id, boolean customer) {
        for (int i = 0; i < box.getItemCount(); i++) {
            Object item = box.getItemAt(i);
            String itemId = customer
                    ? ((Customer) item).getId()
                    : ((House) item).getId();
            if (itemId.equals(id)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private String idOf(Object item, boolean customer) {
        if (item == null) {
            return "";
        }
        return customer ? ((Customer) item).getId() : ((House) item).getId();
    }

    /** 秒与纳秒一律归零：界面上只能选到分钟，存进去也不该出现「14:30:47」 */
    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                .withSecond(0).withNano(0);
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ---------------------------------------------------------------- 小工具

    /**
     * R-003：把「已成交」改成其它结果时，房屋状态<b>不会</b>自动跟着回退，这里提示一下。
     *
     * <p>刻意只提示、不代改。两种错误的代价不对称：把已租出当空置会导致重复推荐
     * （对客户失信），把空置当已租出只是少推一套。释放必须由人到房屋页确认。
     * 不提示的话，用户会以为这是 bug。详见需求报告 4.3。
     */
    private void noticeIfStatusNotReverted(boolean editing, Viewing previous, String newResult) {
        if (!editing || previous == null
                || !Viewing.RESULT_DEAL.equals(previous.getResult())
                || Viewing.RESULT_DEAL.equals(newResult)) {
            return;
        }
        JOptionPane.showMessageDialog(this,
                "这条记录原本是「已成交」，房屋 " + previous.getHouseId()
                        + " 目前的状态仍为「已租出」。\n\n"
                        + "房屋状态不会随带看记录自动改回——若该房屋已经不再出租，\n"
                        + "请到「房屋管理」页把它的状态改回「空置」。",
                "房屋状态未变动", JOptionPane.INFORMATION_MESSAGE);
    }

    private void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "无法保存", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** 导出当前列表为 CSV（G-014）。导出的是筛选后的结果 */
    private void exportCsv() {
        if (visibleViewings.isEmpty()) {
            warn(this, "当前列表没有可导出的数据");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出带看记录");
        chooser.setSelectedFile(new File("带看记录_" + CsvExporter.today() + ".csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(COLUMNS.clone());
        for (Viewing viewing : visibleViewings) {
            rows.add(new String[]{
                    viewing.getCustomerId(), viewing.getCustomerName(),
                    viewing.getHouseId(), viewing.getHouseAddress(),
                    Formats.dateTime(viewing.getViewedAt()),
                    viewing.getResult(), viewing.getNote()});
        }

        Path file = chooser.getSelectedFile().toPath();
        try {
            CsvExporter.write(file, rows);
            viewingController.recordExport(visibleViewings.size(), file.getFileName().toString());
            Toast.success(this, "已导出 " + visibleViewings.size() + " 条到 " + file.getFileName());
        } catch (IOException e) {
            showError(this, "导出失败",
                    "无法写入文件：" + e.getMessage() + "\n请确认该文件未被 Excel 打开。");
        }
    }

    private JLabel groupLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SUBTITLE);
        label.setForeground(Theme.ACCENT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_LIGHT),
                new EmptyBorder(0, 0, 6, 0)));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return label;
    }

    private JPanel twoColumnForm(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < fields.length; i++) {
            int column = (i % 2) * 2;
            int row = i / 2;

            JLabel label = new JLabel(labels[i]);
            label.setFont(Theme.FONT_CAPTION);
            label.setForeground(Theme.TEXT_SECONDARY);

            JComponent field = fields[i];
            field.setFont(Theme.FONT_BODY);
            field.setPreferredSize(new Dimension(190, 30));

            gbc.gridx = column;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.insets = new Insets(0, 0, 10, 10);
            panel.add(label, gbc);

            gbc.gridx = column + 1;
            gbc.weightx = 1;
            gbc.insets = new Insets(0, 0, 10, 18);
            panel.add(field, gbc);
        }
        return panel;
    }

    private CompoundBorder outlineBorder(Color color) {
        return new CompoundBorder(new LineBorder(color, 1, true),
                new EmptyBorder(5, 14, 5, 14));
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_BODY);
        button.setBackground(Theme.ACCENT);
        button.setForeground(Theme.TEXT_ON_ACCENT);
        button.setBorder(new EmptyBorder(6, 16, 6, 16));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton secondaryOutlineButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_BODY);
        styleAsSecondary(button);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void styleAsSecondary(JButton button) {
        button.setBackground(Theme.SURFACE);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setBorder(outlineBorder(Theme.BORDER_INPUT));
    }

    /** 客户 / 房屋下拉的渲染：显示「ID · 名称」，而不是对象默认的 toString */
    private static final class NamedRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Object display = value;
            if (value instanceof Customer) {
                Customer item = (Customer) value;
                display = item.getId() + " · " + item.getName();
            } else if (value instanceof House) {
                House item = (House) value;
                // 已租出的房源正常不会出现在这里，只有「编辑一条挂在已租出房源上的
                // 旧记录」时会，标出来免得用户以为过滤没生效（G-020）
                display = item.getId() + " · " + item.getAddress()
                        + (item.isRented() ? "　（已租出）" : "");
            }
            Component component = super.getListCellRendererComponent(
                    list, display, index, isSelected, cellHasFocus);
            component.setFont(Theme.FONT_BODY);
            return component;
        }
    }
}
