package test;

import util.CsvExporter;
import util.DataAccessException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CSV 导出与数据库异常归类的测试。对应需求报告 G-014 / G-012。
 *
 * <p>CSV 部分重点验证转义：字段里出现逗号、引号、换行时若不加引号包裹，
 * 数据会被 Excel 拆成好几列——这是最典型也最难被肉眼发现的导出 bug。
 */
public final class ExportTest {

    private ExportTest() {
    }

    public static void run(TestRunner t) {
        t.suite("CsvExporter · 字段转义（G-014）");

        t.equals("普通字段不加引号", "abc", CsvExporter.escape("abc"));
        t.equals("含逗号加引号", "\"a,b\"", CsvExporter.escape("a,b"));
        t.equals("含引号时引号翻倍", "\"a\"\"b\"", CsvExporter.escape("a\"b"));
        t.equals("含换行加引号", "\"a\nb\"", CsvExporter.escape("a\nb"));
        t.equals("含回车加引号", "\"a\rb\"", CsvExporter.escape("a\rb"));
        t.equals("null 转为空串", "", CsvExporter.escape(null));
        t.equals("中文不需转义", "阳光路 8 号", CsvExporter.escape("阳光路 8 号"));

        t.suite("CsvExporter · 行与整体拼装");

        t.equals("一行多字段用逗号分隔",
                "1010,两室一厅,89.5",
                CsvExporter.buildLine(new String[]{"1010", "两室一厅", "89.5"}));
        t.equals("行内含逗号的地址被正确包起来",
                "1010,\"阳光路 8 号, 2 单元\"",
                CsvExporter.buildLine(new String[]{"1010", "阳光路 8 号, 2 单元"}));
        t.equals("null 行返回空串", "", CsvExporter.buildLine(null));

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "地址"});
        rows.add(new String[]{"1010", "阳光路 8 号"});
        String csv = CsvExporter.build(rows);
        t.check("多行以 CRLF 分隔（Excel 在 Windows 上兼容最好）", csv.contains("\r\n"));
        t.check("表头出现在结果中", csv.startsWith("ID,地址"));
        t.check("结尾也有换行", csv.endsWith("\r\n"));
        t.equals("null 行集返回空串", "", CsvExporter.build(null));
        t.check("日期后缀为 8 位数字", CsvExporter.today().matches("\\d{8}"));

        t.suite("DataAccessException · 异常归类（G-012）");

        t.equals("MySQL 1062 归为重复键",
                DataAccessException.Kind.DUPLICATE_KEY,
                DataAccessException.from(new SQLException("Duplicate entry", "23000", 1062)).getKind());
        t.equals("MySQL 1452 归为约束不满足",
                DataAccessException.Kind.CONSTRAINT,
                DataAccessException.from(new SQLException("child row", "23000", 1452)).getKind());
        t.equals("MySQL 1451 归为约束不满足",
                DataAccessException.Kind.CONSTRAINT,
                DataAccessException.from(new SQLException("referenced", "23000", 1451)).getKind());
        t.equals("SQLState 08 开头归为连接失败",
                DataAccessException.Kind.CONNECTION,
                DataAccessException.from(new SQLException("link failure", "08S01", 0)).getKind());
        t.equals("其它错误归为 UNKNOWN",
                DataAccessException.Kind.UNKNOWN,
                DataAccessException.from(new SQLException("table missing", "42S02", 1146)).getKind());

        t.check("连接失败的用户提示里提到 MySQL",
                DataAccessException.from(new SQLException("x", "08S01", 0))
                        .userMessage().contains("MySQL"));
        t.check("UNKNOWN 的用户提示带上原始信息，便于排查",
                DataAccessException.from(new SQLException("table missing", "42S02", 1146))
                        .userMessage().contains("table missing"));
        t.check("是运行时异常，上层不必逐层声明",
                RuntimeException.class.isAssignableFrom(DataAccessException.class));

        t.suite("CsvExporter · 与真实数据形态的组合");

        List<String[]> real = new ArrayList<>(Arrays.asList(
                new String[]{"101", "大", "111", "大王发放", "111", "是法务", "42451"},
                new String[]{"1231", "大", "232", "带我打法师", "11", "嘻嘻嘻", "1453224"}));
        String out = CsvExporter.build(real);
        t.equals("两行数据两行输出", 2, out.split("\r\n", -1).length - 1);
        t.check("内容未被截断", out.contains("带我打法师"));
    }
}
