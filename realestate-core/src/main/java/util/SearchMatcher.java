package util;

/**
 * 关键字匹配。对应需求报告 G-004（查询筛选）。
 *
 * <p>规则：
 * <ul>
 *   <li>关键字为空（或全空白）时一律视为匹配——即「没有筛选条件」</li>
 *   <li>多个关键字用空白分隔，<b>全部命中才算匹配</b>（AND 语义）</li>
 *   <li>大小写不敏感</li>
 *   <li>命中的含义是「包含」，不是「相等」——搜「1010」能匹配到 1010 与 10101</li>
 * </ul>
 *
 * <p>刻意做成不依赖 Swing、不依赖数据库的纯函数，这样筛选逻辑可以脱离界面
 * 单独验证（本项目的界面验证需要真实的 MySQL 与窗口，做不到每次改动都跑）。
 */
public final class SearchMatcher {

    private SearchMatcher() {
    }

    /**
     * 判断若干字段中是否都命中了关键字中的每一项。
     *
     * @param keyword 用户输入的关键字，可为 null
     * @param fields  参与匹配的字段，null 字段按「不含」处理
     * @return 匹配返回 true
     */
    public static boolean matches(String keyword, String... fields) {
        if (isBlank(keyword)) {
            return true;
        }

        String[] terms = keyword.trim().toLowerCase().split("\\s+");
        for (String term : terms) {
            if (!contains(fields, term)) {
                return false;
            }
        }
        return true;
    }

    /** 任意一个字段包含该关键字即为命中 */
    private static boolean contains(String[] fields, String term) {
        if (fields == null) {
            return false;
        }
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(term)) {
                return true;
            }
        }
        return false;
    }

    /** 供界面层判断「当前是否处于筛选状态」 */
    public static boolean isBlank(String keyword) {
        return keyword == null || keyword.trim().isEmpty();
    }
}
