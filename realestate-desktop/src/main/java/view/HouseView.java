package view;

import controller.HouseController;
import model.House;
import model.Landlord;
import util.CsvExporter;
import util.DataAccessException;
import util.Formats;
import util.Result;
import util.SearchMatcher;
import ui.Theme;

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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HouseView extends JPanel {

    private static final String[] COLUMNS =
            {"ID", "户型", "面积(m²)", "地址", "状态", "房东ID", "房东姓名", "房东电话"};

    /** 面积列的下标。它在模型里存 Double 而不是格式化后的字符串，见 createHouseTable */
    private static final int AREA_COLUMN = 2;

    /**
     * 状态列的下标（R-003）。刻意放在「地址」之后而不是排在最末：
     * 它与「这是哪套房」一起扫视更自然，也不至于被房东电话列挤到看不见。
     */
    private static final int STATUS_COLUMN = 4;

    /** 状态下拉里代表「不筛选」的选项 */
    private static final String ALL_STATUSES = "全部";

    /** 房东下拉中代表「新建房东」的哨兵项 */
    private static final Object NEW_LANDLORD_ITEM = new Object() {
        @Override
        public String toString() {
            return "＋ 新建房东";
        }
    };

    private final HouseController houseController;
    private final Consumer<String> statusReporter;

    private final JTable houseTable;
    private final JTextField searchField = new JTextField(16);
    /** 状态筛选（R-003）：全部 / 空置 / 已租出 */
    private final JComboBox<String> statusFilter = new JComboBox<>();
    private final JButton editButton = new JButton("编辑房屋");
    private final JButton deleteButton = new JButton("删除房屋");

    /** 数据库中的全部房屋 */
    private List<House> allHouses = new ArrayList<>();
    /** 按搜索框筛选后、与表格行一一对应的数据 */
    private List<House> visibleHouses = new ArrayList<>();

    public HouseView(HouseController houseController, Consumer<String> statusReporter) {
        this.houseController = houseController;
        this.statusReporter = statusReporter;

        setLayout(new BorderLayout());
        setBackground(Theme.PAGE_BG);
        setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = createHeader();
        houseTable = createHouseTable();

        JScrollPane scrollPane = new JScrollPane(houseTable);
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

        JLabel title = new JLabel("房屋信息管理");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_HEADING);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);

        JButton addButton = primaryButton("添加房屋");
        addButton.addActionListener(e -> showHouseDialog(null));

        editButton.setFont(Theme.FONT_BODY);
        editButton.setFocusPainted(false);
        editButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editButton.addActionListener(e -> editSelectedHouse());

        deleteButton.setFont(Theme.FONT_BODY);
        deleteButton.setFocusPainted(false);
        deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        deleteButton.addActionListener(e -> deleteSelectedHouse());

        JButton refreshButton = secondaryOutlineButton("刷新数据");
        refreshButton.addActionListener(e -> refresh());

        JButton exportButton = secondaryOutlineButton("导出 CSV");
        exportButton.addActionListener(e -> exportCsv());

        // 编辑与刷新同为次要操作，用同一套描边样式
        styleAsSecondary(editButton);

        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);
        buttons.add(exportButton);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(buttons, BorderLayout.WEST);
        toolbar.add(createFilterBox(), BorderLayout.EAST);

        top.add(title, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.SOUTH);
        return top;
    }

    /**
     * 右侧的筛选区：状态下拉（R-003）+ 关键字搜索框（G-004）。两者叠加生效。
     *
     * <p>筛选在已读出的数据上做，不再打数据库——本系统的数据量是「一个中介门店」级别，
     * 全量加载后再过滤比每次输入都发一次 SQL 更简单也更快。
     */
    private JPanel createFilterBox() {
        statusFilter.setFont(Theme.FONT_BODY);
        statusFilter.addItem(ALL_STATUSES);
        for (String status : House.STATUSES) {
            statusFilter.addItem(status);
        }
        statusFilter.setPreferredSize(new Dimension(92, 30));
        statusFilter.setToolTipText("只看某一种状态的房屋");
        statusFilter.addActionListener(e -> applyFilter());

        JLabel statusCaption = new JLabel("状态");
        statusCaption.setFont(Theme.FONT_CAPTION);
        statusCaption.setForeground(Theme.TEXT_SECONDARY);

        searchField.setFont(Theme.FONT_BODY);
        searchField.setPreferredSize(new Dimension(200, 30));
        searchField.putClientProperty("JTextField.placeholderText", "搜索 ID / 户型 / 地址 / 房东");
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

        JPanel box = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        box.setOpaque(false);
        box.add(statusCaption);
        box.add(statusFilter);
        box.add(searchField);
        return box;
    }

    private JTable createHouseTable() {
        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            /**
             * 面积列声明为 Double，表头排序才会按数值比较（G-005）。
             * 若沿用默认的 Object.class，排序器会退化成字符串比较，
             * 「100」就被排在「89」前面了。
             */
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == AREA_COLUMN ? Double.class : String.class;
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

        // G-005：点击表头排序。取值一律经 convertRowIndexToModel 换算，
        // 因此排序之后「编辑 / 删除」拿到的仍是用户看到的那一行
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(AREA_COLUMN).setCellRenderer(new AreaCellRenderer());
        table.getColumnModel().getColumn(STATUS_COLUMN).setCellRenderer(new StatusCellRenderer());

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

    /**
     * 重新读取并刷新表格，同时把记录数写入底部状态栏。
     *
     * <p>读取失败（数据库连不上等）在这里捕获并提示：空表格与「读不出来」必须
     * 区分开，否则用户会以为数据丢了。对应需求报告 G-012。
     */
    public void refresh() {
        try {
            allHouses = houseController.getAllHouses();
        } catch (DataAccessException e) {
            allHouses = new ArrayList<>();
            showError(this, "读取失败", e.userMessage());
        }
        applyFilter();
    }

    /**
     * 按搜索关键字（G-004）与状态（R-003）过滤并重建表格。不重新查库。
     * 两个条件是「与」的关系：先过状态，再看关键字。
     */
    private void applyFilter() {
        String keyword = searchField.getText();
        String status = selectedStatus();

        visibleHouses = new ArrayList<>();
        for (House house : allHouses) {
            if (!matchesStatus(house, status)) {
                continue;
            }
            if (SearchMatcher.matches(keyword,
                    house.getId(), house.getType(), house.getAddress(),
                    house.getLandlord().getId(), house.getLandlord().getName(),
                    house.getLandlord().getContact())) {
                visibleHouses.add(house);
            }
        }

        rebuildTable();
        reportStatus();
    }

    /** 当前状态筛选值；「全部」表示不筛 */
    private String selectedStatus() {
        Object selected = statusFilter.getSelectedItem();
        return selected == null ? ALL_STATUSES : selected.toString();
    }

    private boolean matchesStatus(House house, String status) {
        return ALL_STATUSES.equals(status) || status.equals(house.getStatus());
    }

    private void rebuildTable() {
        DefaultTableModel model = (DefaultTableModel) houseTable.getModel();
        model.setRowCount(0);
        for (House house : visibleHouses) {
            model.addRow(new Object[]{
                    house.getId(),
                    house.getType(),
                    // 存原始数值，显示交给 AreaCellRenderer——这样排序才对
                    house.getArea(),
                    house.getAddress(),
                    house.getStatus(),
                    house.getLandlord().getId(),
                    house.getLandlord().getName(),
                    house.getLandlord().getContact()
            });
        }
    }

    private void reportStatus() {
        if (statusReporter == null) {
            return;
        }

        boolean filtered = !SearchMatcher.isBlank(searchField.getText())
                || !ALL_STATUSES.equals(selectedStatus());

        if (!filtered) {
            statusReporter.accept("共 " + allHouses.size() + " 条房屋记录");
        } else if (visibleHouses.isEmpty()) {
            statusReporter.accept("未找到匹配的房屋（共 " + allHouses.size() + " 条）");
        } else {
            statusReporter.accept("筛选出 " + visibleHouses.size() + " 条 / 共 "
                    + allHouses.size() + " 条房屋记录");
        }
    }

    // ------------------------------------------------------------ 权限控制

    /**
     * 按当前用户权限启用或置灰「删除房屋」按钮。
     *
     * <p><b>登录成功后必须由 MainView 再次调用本方法。</b>本视图是在登录之前就被
     * 构造的（见 RealEstateSystem.main），那时 Session 里还没有用户，
     * {@code Session.can} 按 fail-safe 返回 false，按钮必然是禁用态。
     *
     * <p>界面层的置灰只是体验优化——真正的防护在 {@code HouseController.deleteHouse}。
     *
     * <p>这里显式指定禁用态的配色，而不依赖外观库的默认处理，
     * 以确保「灰底灰字」与正常的「红字红边」对比足够鲜明，用户一眼能看出是权限不足。
     */
    public void applyPermissions() {
        boolean allowed = houseController.canDelete();
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

    private void editSelectedHouse() {
        House selected = getSelectedHouse("编辑");
        if (selected == null) {
            return;
        }
        showHouseDialog(selected);
    }

    private void deleteSelectedHouse() {
        House selected = getSelectedHouse("删除");
        if (selected == null) {
            return;
        }

        // 删除会连带清掉两样别的东西，都必须提前讲清楚——不能悄悄删：
        //   1) 该房屋的带看记录（数据库外键级联，G-008）
        //   2) 该房屋的房东（若其名下再无其它房屋，G-018）
        String extra = "";
        try {
            int viewings = houseController.countViewings(selected.getId());
            if (viewings > 0) {
                extra += "\n\n注意：该房屋有 " + viewings + " 条带看记录，将一并删除。";
            }
        } catch (DataAccessException e) {
            // 查不出条数就不提这一句；真正删除时若失败会有自己的提示
            System.err.println("查询房屋带看记录条数失败: " + e.getMessage());
        }
        try {
            Landlord landlord = selected.getLandlord();
            if (houseController.countHousesByLandlord(landlord.getId()) <= 1) {
                extra += "\n\n注意：房东「" + landlord.getName()
                        + "」名下没有其它房屋，删除后其房东记录将一并清除。";
            }
        } catch (DataAccessException e) {
            System.err.println("查询房东名下房屋数失败: " + e.getMessage());
        }

        Object[] options = {"取消", "确认删除"};
        int choice = JOptionPane.showOptionDialog(this,
                "确定要删除房屋 " + selected.getId() + "（" + selected.getAddress()
                        + "）吗？此操作不可撤销。" + extra,
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]); // 默认焦点在「取消」，避免习惯性回车误删

        if (choice != 1) {
            return;
        }

        Result result = houseController.deleteHouse(selected.getId());
        if (result.isSuccess()) {
            Toast.success(this, result.getMessage());
            refresh();
        } else {
            warn(this, result.getMessage());
        }
    }

    /**
     * 取当前选中的房屋。
     *
     * <p>用 {@code convertRowIndexToModel} 换算行号是必须的：开启表头排序（G-005）后
     * 视图行号与模型行号不再一致，直接用 {@code getSelectedRow()} 去索引
     * {@code visibleHouses} 会取到另一条记录——「删除」尤其危险，会删错行。
     */
    private House getSelectedHouse(String action) {
        int viewRow = houseTable.getSelectedRow();
        if (viewRow == -1) {
            warn(this, "请先选择要" + action + "的房屋");
            return null;
        }

        int modelRow = houseTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= visibleHouses.size()) {
            warn(this, "数据已发生变化，请重新选择");
            refresh();
            return null;
        }
        return visibleHouses.get(modelRow);
    }

    // -------------------------------------------------------------- 新增 / 编辑对话框

    /**
     * 新增与编辑共用一个对话框。
     *
     * @param existing 为 null 表示新增；否则为编辑，此时房屋ID 只读
     */
    private void showHouseDialog(House existing) {
        final boolean editing = existing != null;

        // 房东下拉的数据来源。读不出来就不必往下走了——给一个只剩「新建房东」的
        // 残缺表单，用户填完照样存不进去。对应需求报告 G-012。
        List<Landlord> landlords;
        try {
            landlords = houseController.getAllLandlords();
        } catch (DataAccessException e) {
            showError(this, "读取失败", e.userMessage());
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                editing ? "编辑房屋" : "添加新房屋", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JLabel title = new JLabel(editing ? "编辑房屋" : "添加新房屋");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_HEADING);
        title.setBorder(new EmptyBorder(18, 20, 0, 20));
        dialog.add(title, BorderLayout.NORTH);

        JTextField houseId = new JTextField(editing ? existing.getId() : "");
        JTextField type = new JTextField(editing ? existing.getType() : "");
        JTextField area = new JTextField(editing ? Formats.area(existing.getArea()) : "");
        JTextField address = new JTextField(editing ? existing.getAddress() : "");

        // R-003：状态是房屋自己的属性，随保存一起写。
        // 它是下拉而不是文本框，所以独占一行放在「房屋信息」组末尾（与下面「房东」行同一写法），
        // 挤进两列网格会与其他字段对不齐。
        JComboBox<String> statusBox = new JComboBox<>(House.STATUSES);
        statusBox.setFont(Theme.FONT_BODY);
        statusBox.setSelectedItem(editing ? existing.getStatus() : House.STATUS_VACANT);
        statusBox.setToolTipText("房子租出去了改成「已租出」；租客退租再改回「空置」");

        // 房东三个字段的内容由下拉框决定：选中已有房东 → 填入并置只读；选「新建房东」→ 可填写
        JTextField landlordId = new JTextField();
        JTextField landlordName = new JTextField();
        JTextField landlordContact = new JTextField();

        if (editing) {
            // 主键不可改：改主键等于换一条记录，语义上应是「删旧增新」
            houseId.setEditable(false);
            houseId.setBackground(Theme.DISABLED_BG);
            houseId.setToolTipText("房屋ID 是主键，编辑时不可修改");
        }

        JComboBox<Object> landlordBox = createLandlordBox(existing, landlords,
                landlordId, landlordName, landlordContact);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 20, 0, 20));

        body.add(groupLabel("房屋信息"));
        body.add(twoColumnForm(
                new String[]{"房屋ID", "户型", "面积(m²)", "地址"},
                new JTextField[]{houseId, type, area, address}));
        body.add(labeledRow("状态", statusBox));
        body.add(Box.createVerticalStrut(18));
        body.add(groupLabel("房东信息"));
        body.add(labeledRow("房东", landlordBox));
        body.add(twoColumnForm(
                new String[]{"房东ID", "姓名", "电话"},
                new JTextField[]{landlordId, landlordName, landlordContact}));

        dialog.add(body, BorderLayout.CENTER);

        JButton cancel = secondaryOutlineButton("取消");
        cancel.addActionListener(e -> dialog.dispose());

        JButton submit = primaryButton(editing ? "保存" : "提交");
        submit.addActionListener(e -> {
            double areaValue;
            try {
                areaValue = Double.parseDouble(area.getText().trim());
            } catch (NumberFormatException ex) {
                warn(dialog, "面积必须是数字");
                area.requestFocusInWindow();
                return;
            }

            String statusValue = (String) statusBox.getSelectedItem();

            Result result = editing
                    ? houseController.updateHouse(
                            houseId.getText(), type.getText(), areaValue, address.getText(),
                            landlordId.getText(), landlordName.getText(), landlordContact.getText(),
                            statusValue)
                    : houseController.addHouse(
                            houseId.getText(), type.getText(), areaValue, address.getText(),
                            landlordId.getText(), landlordName.getText(), landlordContact.getText(),
                            statusValue);

            if (result.isSuccess()) {
                Toast.success(this, result.getMessage());
                refresh();
                dialog.dispose();
            } else {
                warn(dialog, result.getMessage());
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

    /**
     * 房东下拉框（G-007）。
     *
     * <p>改造前：房东 ID / 姓名 / 电话 三个自由输入框，同一个房东有两套房就得重复填两遍，
     * 而且很容易填出「同一个房东 ID、两个不同姓名」的脏数据。
     *
     * <p>改造后：从已有房东里选，选中即自动填入其资料并置为只读。之所以只读，是因为
     * 一个房东可能关联多套房屋——凭一次表单提交改写房东资料，会连带改变其它房屋
     * 显示出来的房东信息。要改房东资料，应当有独立的房东管理（见缺口 G-007 的后续）。
     */
    private JComboBox<Object> createLandlordBox(House existing, List<Landlord> landlords,
                                                JTextField id, JTextField name, JTextField contact) {
        JComboBox<Object> box = new JComboBox<>();
        box.setFont(Theme.FONT_BODY);
        box.setMaximumRowCount(12);
        box.setRenderer(new LandlordRenderer());
        box.setToolTipText("选择已有房东，或选「新建房东」录入新房东");

        box.addItem(NEW_LANDLORD_ITEM);
        for (Landlord landlord : landlords) {
            box.addItem(landlord);
        }

        box.addActionListener(e -> applyLandlordSelection(box, id, name, contact));

        if (existing != null) {
            selectLandlord(box, existing.getLandlord());
        }
        // 初始同步一次：新增时下拉停在「新建房东」，字段应清空且可编辑；
        // 编辑时上面已选中对应房东，此处把其资料填好并置只读
        applyLandlordSelection(box, id, name, contact);
        return box;
    }

    /** 在房东下拉中选中指定房东；列表中不存在时（理论上不会发生）临时补入，避免显示为空 */
    private void selectLandlord(JComboBox<Object> box, Landlord landlord) {
        if (landlord == null) {
            return;
        }
        for (int i = 0; i < box.getItemCount(); i++) {
            Object item = box.getItemAt(i);
            if (item instanceof Landlord && ((Landlord) item).getId().equals(landlord.getId())) {
                box.setSelectedIndex(i);
                return;
            }
        }
        box.addItem(landlord);
        box.setSelectedItem(landlord);
    }

    /** 下拉选择变化时同步下方字段 */
    private void applyLandlordSelection(JComboBox<Object> box, JTextField id,
                                        JTextField name, JTextField contact) {
        Object selected = box.getSelectedItem();
        boolean existing = selected instanceof Landlord;

        if (existing) {
            Landlord landlord = (Landlord) selected;
            id.setText(landlord.getId());
            name.setText(landlord.getName());
            contact.setText(landlord.getContact());
        } else {
            id.setText("");
            name.setText("");
            contact.setText("");
        }

        setReadOnly(id, existing);
        setReadOnly(name, existing);
        setReadOnly(contact, existing);
    }

    private void setReadOnly(JTextField field, boolean readOnly) {
        field.setEditable(!readOnly);
        field.setBackground(readOnly ? Theme.DISABLED_BG : Theme.SURFACE);
        field.setToolTipText(readOnly ? "已有房东的资料不可在此修改" : null);
    }

    // ---------------------------------------------------------------- 小工具

    /** 统一的失败提示（校验不通过、ID 冲突、保存失败等） */
    private void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "无法保存", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * 读取类失败的提示。标题与「无法保存」刻意区分开——用户看标题就能判断是
     * 自己填错了，还是系统读不到数据。对应需求报告 G-012。
     */
    private void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 导出当前列表为 CSV（G-014）。
     *
     * <p>导出的是<b>当前筛选后的结果</b>，不是全量——用户在搜索框里筛出 3 条再点导出，
     * 期待拿到的就是这 3 条。因此提示语里带上条数，避免误解。
     */
    private void exportCsv() {
        if (visibleHouses.isEmpty()) {
            warn(this, "当前列表没有可导出的数据");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出房屋列表");
        chooser.setSelectedFile(new File("房屋列表_" + CsvExporter.today() + ".csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(COLUMNS.clone());
        for (House house : visibleHouses) {
            rows.add(new String[]{
                    house.getId(), house.getType(),
                    Formats.area(house.getArea()), house.getAddress(),
                    house.getStatus(),
                    house.getLandlord().getId(), house.getLandlord().getName(),
                    house.getLandlord().getContact()});
        }

        Path file = chooser.getSelectedFile().toPath();
        try {
            CsvExporter.write(file, rows);
            houseController.recordExport(visibleHouses.size(), file.getFileName().toString());
            Toast.success(this, "已导出 " + visibleHouses.size() + " 条到 " + file.getFileName());
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

    /** 两列排布的表单：每行两组「标签 + 输入框」 */
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

    /** 单行「标签 + 整宽控件」，用于放不进两列布局的控件（如下拉框）。列位置与 twoColumnForm 对齐 */
    private JPanel labeledRow(String label, JComponent field) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel caption = new JLabel(label);
        caption.setFont(Theme.FONT_CAPTION);
        caption.setForeground(Theme.TEXT_SECONDARY);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 10, 10);
        row.add(caption, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 10, 18);
        row.add(field, gbc);
        return row;
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
        return button;
    }

    private void styleAsSecondary(JButton button) {
        button.setBackground(Theme.SURFACE);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setBorder(outlineBorder(Theme.BORDER_INPUT));
    }

    /** 面积在模型里是 Double（为了排序），显示时去掉多余的 .0 */
    private static final class AreaCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText(value instanceof Number ? Formats.area(((Number) value).doubleValue()) : "");
            return this;
        }
    }

    /**
     * 状态列渲染为圆角标签（R-003）。
     *
     * <p>「已租出」用主色实底 + 白字，「空置」用浅灰底 + 次级文字色：两者的视觉重量
     * 刻意不同，扫一眼就能分出哪些房子还在手上。
     *
     * <p><b>底色必须由渲染器自己画。</b>表格不会替渲染器补选中底色——把组件设成
     * 不透明、指望「露出」下面那一层的选中色，实际露出来的是表格的白底，而文字用的是
     * 选中前景色（白），于是成了白字白底，整格什么都看不见。实测像素：未选中格
     * 33 种颜色（有标签有文字），选中格只剩 2 种（白底 + 一条网格线）。
     *
     * <p>选中行整行是主色底，标签若还用主色实底就会糊成一片，因此选中时标签反白
     * （白底 + 状态色文字）。底部留 1 像素不填，与其他列的网格线保持一致。
     */
    private static final class StatusCellRenderer extends DefaultTableCellRenderer {

        private static final int PILL_HEIGHT = 20;
        private static final int PILL_PADDING = 20;

        /** 本次绘制要用的标签底色，由 {@link #getTableCellRendererComponent} 按状态与选中与否算好 */
        private Color pillColor = Theme.DISABLED_BG;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setText(value == null ? "" : value.toString());
            setHorizontalAlignment(SwingConstants.CENTER);
            // 保持不透明，底色由 paintComponent 自己填（见类注释）
            setOpaque(false);

            boolean rented = House.STATUS_RENTED.equals(getText());
            if (isSelected) {
                pillColor = Theme.SURFACE;
                setForeground(rented ? Theme.ACCENT : Theme.TEXT_SECONDARY);
            } else {
                pillColor = rented ? Theme.ACCENT : Theme.DISABLED_BG;
                setForeground(rented ? Theme.TEXT_ON_ACCENT : Theme.TEXT_SECONDARY);
            }

            // 选中底色必须自己填，否则这一格会露出表格白底
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }

        @Override
        public void paintComponent(Graphics g) {
            // 顺序不能反：底色 → 标签 → 文字。
            // 父类在 opaque=false 时只画文字，不会把标签盖掉。
            // 高度减 1：把最后一行留给网格线，与其它列的画法一致。
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), Math.max(0, getHeight() - 1));

            if (!getText().isEmpty()) {
                paintPill(g);
            }
            super.paintComponent(g);
        }

        private void paintPill(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(pillColor);

            int textWidth = getFontMetrics(getFont()).stringWidth(getText());
            int width = Math.min(getWidth() - 8, textWidth + PILL_PADDING);
            int x = Math.max(4, (getWidth() - width) / 2);
            int y = Math.max(0, (getHeight() - PILL_HEIGHT) / 2);

            g2.fill(new RoundRectangle2D.Double(x, y, width, PILL_HEIGHT,
                    PILL_HEIGHT, PILL_HEIGHT));
            g2.dispose();
        }
    }

    /** 房东下拉的渲染：显示「ID · 姓名」，而不是 Landlord 的默认 toString */
    private static final class LandlordRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Object display = value;
            if (value instanceof Landlord) {
                Landlord landlord = (Landlord) value;
                display = landlord.getId() + " · " + landlord.getName();
            }
            Component component = super.getListCellRendererComponent(
                    list, display, index, isSelected, cellHasFocus);
            component.setFont(Theme.FONT_BODY);
            return component;
        }
    }
}
