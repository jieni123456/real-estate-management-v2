package test;

import util.Validators;

/**
 * 输入校验规则的测试。对应需求报告 G-003 / G-015。
 *
 * <p>约定：校验通过返回 null，不通过返回给用户看的原因。这里除了验证「该拦的拦住」，
 * 也验证「不该拦的别拦」——校验写得太严会让正常数据也存不进去，同样是 bug。
 */
public final class ValidatorsTest {

    private ValidatorsTest() {
    }

    public static void run(TestRunner t) {
        t.suite("Validators · 必填文本");

        t.isNull("正常文本通过", Validators.requiredText("房屋ID", "1010", 50));
        t.check("空字符串被拒", Validators.requiredText("房屋ID", "", 50) != null);
        t.check("null 被拒", Validators.requiredText("房屋ID", null, 50) != null);
        t.check("纯空白被拒", Validators.requiredText("房屋ID", "   ", 50) != null);
        t.equals("超长被拒并说明原因",
                "房屋ID不能超过 3 个字符", Validators.requiredText("房屋ID", "1234", 3));
        t.isNull("刚好到上限通过", Validators.requiredText("房屋ID", "123", 3));

        t.suite("Validators · 选填文本");

        t.isNull("null 通过", Validators.optionalText("需求描述", null, 10));
        t.isNull("空串通过", Validators.optionalText("需求描述", "", 10));
        t.check("超长被拒", Validators.optionalText("需求描述", "12345678901", 10) != null);

        t.suite("Validators · 电话");

        t.isNull("手机号通过", Validators.phone("电话", "13800138000"));
        t.isNull("座机通过", Validators.phone("电话", "010-12345678"));
        t.isNull("带括号与加号通过", Validators.phone("电话", "+86 (010) 1234"));
        t.isNull("5 位短号通过", Validators.phone("电话", "12345"));
        t.check("空电话被拒", Validators.phone("电话", "") != null);
        t.check("含字母被拒", Validators.phone("电话", "1380013800a") != null);
        t.check("过短被拒", Validators.phone("电话", "1234") != null);
        t.check("过长被拒", Validators.phone("电话", "123456789012345678901") != null);

        t.suite("Validators · 带看时间不得晚于今天（G-008）");

        java.time.LocalDateTime yesterday =
                java.time.LocalDate.now().minusDays(1).atTime(10, 0);
        java.time.LocalDateTime todayNoon = java.time.LocalDate.now().atTime(12, 0);
        java.time.LocalDateTime tomorrow =
                java.time.LocalDate.now().plusDays(1).atTime(9, 0);

        t.isNull("昨天通过", Validators.notFutureDate("带看时间", yesterday));
        t.isNull("今天（即便是 23:59）也通过——按日期判断而非时刻，"
                + "否则「今天下午 3 点」会被误拦",
                Validators.notFutureDate("带看时间", java.time.LocalDate.now().atTime(23, 59)));
        t.isNull("今天中午通过", Validators.notFutureDate("带看时间", todayNoon));
        t.check("明天被拒", Validators.notFutureDate("带看时间", tomorrow) != null);
        t.equals("明天被拒时给出原因",
                "带看时间不能晚于今天", Validators.notFutureDate("带看时间", tomorrow));
        t.equals("null 被拒", "带看时间不能为空", Validators.notFutureDate("带看时间", null));

        t.suite("Validators · 数量与密码");

        t.isNull("面积 0.1 通过", Validators.positiveNumber("面积", 0.1));
        t.check("面积为 0 被拒", Validators.positiveNumber("面积", 0) != null);
        t.check("面积为负被拒", Validators.positiveNumber("面积", -5) != null);

        t.isNull("预设初始口令通过（不能比它更严，否则用户改完就登不进来）",
                Validators.password("新密码", "admin123"));
        t.isNull("6 位刚好通过", Validators.password("新密码", "123456"));
        t.isNull("20 位刚好通过", Validators.password("新密码", "12345678901234567890"));
        t.check("5 位被拒", Validators.password("新密码", "12345") != null);
        t.check("21 位被拒", Validators.password("新密码", "123456789012345678901") != null);
        t.check("含空格被拒", Validators.password("新密码", "abc 123") != null);
        t.check("空密码被拒", Validators.password("新密码", "") != null);
    }
}
