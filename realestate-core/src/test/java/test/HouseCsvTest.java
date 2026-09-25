package test;

import model.HouseImportReport;
import util.CsvExporter;
import util.HouseCsv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 房屋 CSV 解析、表头校验与编码判定的测试。对应需求报告 R-006。
 *
 * <p>最要紧的是「导出再导入」这条回路：{@link CsvExporter} 写出去的引号、
 * 换行与 BOM，{@link HouseCsv#parse} 必须原样读回来。只把两侧分开测是不够的
 * —— 各自都通过、合起来却对不上，是这种成对实现最常见的失败方式，
 * 而且从单边测试里根本看不出来。
 */
public final class HouseCsvTest {

    private HouseCsvTest() {
    }

    public static void run(TestRunner t) {
        t.suite("HouseCsv · 基本切分");

        List<String[]> simple = HouseCsv.parse("A,B,C\n1,2,3");
        t.equals("两行", 2, simple.size());
        t.equals("首行列数为 3", 3, simple.get(0).length);
        t.equals("首行第一格", "A", simple.get(0)[0]);
        t.equals("末行末格", "3", simple.get(1)[2]);

        t.equals("空文本返回空列表", 0, HouseCsv.parse("").size());
        t.equals("null 返回空列表", 0, HouseCsv.parse(null).size());
        t.equals("只有空白也当空文件", 0, HouseCsv.parse("   \n").size());

        t.suite("HouseCsv · 行尾与 BOM");

        t.equals("CRLF 切行", 2, HouseCsv.parse("A\r\nB\r\n").size());
        t.equals("LF 切行", 2, HouseCsv.parse("A\nB\n").size());
        t.equals("孤立 CR 也当行尾", 2, HouseCsv.parse("A\rB\r").size());
        t.equals("两种行尾混用仍只切出两行", 2, HouseCsv.parse("A\r\nB\n").size());
        t.equals("末尾换行不多出空行", 2, HouseCsv.parse("A,B\n1,2\n").size());
        t.equals("中间的空行被跳过", 2, HouseCsv.parse("A,B\n\n1,2").size());

        // Excel 另存的 CSV 开头有 BOM。不去掉的话第一个表头会变成 "\uFEFFID"，
        // 表头校验必然失败，而用户在编辑器里看不到这个字符
        t.equals("BOM 不进入第一个字段", "ID", HouseCsv.parse("\uFEFFID,户型\n").get(0)[0]);

        t.suite("HouseCsv · 引号转义");

        List<String[]> comma = HouseCsv.parse("1,\"阳光路 8 号, 2 单元\",3\n");
        t.equals("引号内的逗号不切成两列", 3, comma.get(0).length);
        t.equals("引号内的逗号原样保留", "阳光路 8 号, 2 单元", comma.get(0)[1]);

        List<String[]> quote = HouseCsv.parse("1,\"他说\"\"你好\"\"\",3\n");
        t.equals("两个连续引号还原成一个", "他说\"你好\"", quote.get(0)[1]);

        List<String[]> multiLine = HouseCsv.parse("1,\"第一行\n第二行\",3\n");
        t.equals("引号内的换行不切行", 1, multiLine.size());
        t.equals("引号内的换行原样保留", "第一行\n第二行", multiLine.get(0)[1]);
        t.equals("引号段结束后字段数仍为 3", 3, multiLine.get(0).length);

        t.suite("HouseCsv · 表头校验");

        t.check("本系统导出的表头能认出来",
                HouseCsv.isExpectedHeader(HouseCsv.parse(String.join(",", HouseCsv.HEADER) + "\n")));
        t.check("列序不同不通过（不按位置猜）",
                !HouseCsv.isExpectedHeader(HouseCsv.parse(
                        "户型,ID,面积(m²),地址,状态,房东ID,房东姓名,房东电话\n")));
        t.check("列数不足不通过",
                !HouseCsv.isExpectedHeader(HouseCsv.parse("ID,户型,面积(m²)\n")));
        t.check("多出一列不通过",
                !HouseCsv.isExpectedHeader(HouseCsv.parse(
                        String.join(",", HouseCsv.HEADER) + ",备注\n")));
        t.check("表头两端的空白容错（在 Excel 里改过的文件很常见）",
                HouseCsv.isExpectedHeader(HouseCsv.parse(
                        " ID , 户型 , 面积(m²) , 地址 , 状态 , 房东ID , 房东姓名 , 房东电话 \n")));
        t.check("空文件不通过", !HouseCsv.isExpectedHeader(HouseCsv.parse("")));
        t.check("null 不通过", !HouseCsv.isExpectedHeader(null));

        // 不匹配时的说明要把「期望」和「实际」都摆出来，用户才知道是导错表还是表头被改过
        String mismatch = HouseCsv.describeHeaderMismatch(HouseCsv.parse("ID,户型\n"));
        t.check("说明里带上期望的列名", mismatch.contains("房东电话"));
        t.check("说明里带上实际读到的列名", mismatch.contains("实际：ID, 户型"));
        t.check("空文件也有可读的说明",
                HouseCsv.describeHeaderMismatch(HouseCsv.parse("")).contains("文件为空"));

        t.suite("HouseCsv · 导出再导入（回路）");

        List<String[]> original = new ArrayList<>();
        original.add(HouseCsv.HEADER.clone());
        // 这两行是刻意挑的：地址里一个含逗号、一个含引号，
        // 正是导出时必须转义、导入时必须还原的两种字符
        original.add(new String[]{"H-1001", "两室一厅", "89.5",
                "赣州市章贡区阳光路 8 号, 2 单元", "空置", "L-1001", "张伟", "13800138000"});
        original.add(new String[]{"H-1002", "三室两厅", "128",
                "赣州市南康区\"老城\"大道 1 号", "已租出", "L-1002", "李娜", "13900139000"});

        List<String[]> back = HouseCsv.parse(CsvExporter.buildWithBom(original));
        t.equals("行数还原", original.size(), back.size());
        t.equals("列数还原", original.get(0).length, back.get(0).length);
        t.equals("含逗号的地址原样还原", original.get(1)[3], back.get(1)[3]);
        t.equals("含引号的地址原样还原", original.get(2)[3], back.get(2)[3]);
        t.equals("状态列未被邻居串位", original.get(2)[4], back.get(2)[4]);
        t.equals("电话列未被邻居串位", original.get(1)[7], back.get(1)[7]);
        t.check("还原回来的表头仍通过校验", HouseCsv.isExpectedHeader(back));

        t.suite("HouseCsv · 文件编码判定");

        String content = "ID,户型\nH-1,两室一厅\n";

        t.equals("带 BOM 的 UTF-8",
                content,
                HouseCsv.decode(("\uFEFF" + content).getBytes(StandardCharsets.UTF_8)));
        t.equals("不带 BOM 的 UTF-8",
                content,
                HouseCsv.decode(content.getBytes(StandardCharsets.UTF_8)));
        // 中文 Windows 上 Excel「另存为 CSV」默认存 GBK，这是最常见的真实情形
        t.equals("GBK 自动回退",
                content,
                HouseCsv.decode(content.getBytes(Charset.forName("GBK"))));
        t.equals("空字节数组返回空串", "", HouseCsv.decode(new byte[0]));
        t.equals("null 返回空串", "", HouseCsv.decode(null));

        // GBK 的中文按 UTF-8 严格解码一定失败，这正是回退的判据；
        // 反过来说，纯 ASCII 在两种编码下字节相同，判成哪个都不影响
        t.check("GBK 的解析结果里不含替换字符",
                !HouseCsv.decode(content.getBytes(Charset.forName("GBK"))).contains("\uFFFD"));

        t.suite("HouseImportReport · 汇总文案");

        t.equals("全部成功", "共 3 条，全部导入成功",
                HouseImportReport.of(3, 3, List.of()).summary());
        t.equals("部分失败", "共 100 条，成功 96 条，失败 4 条",
                HouseImportReport.of(100, 96, List.of(
                        new HouseImportReport.Failure(2, "H-1", "面积不能为空"),
                        new HouseImportReport.Failure(3, "H-2", "状态取值不合法"),
                        new HouseImportReport.Failure(4, "H-3", "房东电话长度应在 5 到 20 位之间"),
                        new HouseImportReport.Failure(5, "", "房屋ID不能为空"))).summary());
        t.equals("文件里没有数据行", "文件里没有可导入的数据行",
                HouseImportReport.of(0, 0, List.of()).summary());

        HouseImportReport denied = HouseImportReport.denied("权限不足：当前账号（经纪人）没有批量导入房屋的权限");
        t.check("权限不足时 summary 就是那句话本身", denied.summary().contains("权限不足"));
        t.check("权限不足时 isPermitted 为假", !denied.isPermitted());

        HouseImportReport partial = HouseImportReport.of(100, 96,
                List.of(new HouseImportReport.Failure(2, "H-1", "x")));
        t.check("部分是成功时 isNothingImported 为假", !partial.isNothingImported());
        t.equals("失败条数等于明细条数", 1, partial.getFailureCount());
        t.equals("失败明细里的行号原样带出", 2, partial.getFailures().get(0).getLine());
        t.equals("房屋ID 为 null 时记为空白", "",
                new HouseImportReport.Failure(2, null, "x").getHouseId());
    }
}
