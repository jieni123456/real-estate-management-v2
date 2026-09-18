package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 应用配置读取。
 *
 * <p>取值优先级（从高到低）：
 * <ol>
 *   <li>同名的<b>系统属性</b>（如 {@code -Dcontact.key=...}）</li>
 *   <li><b>环境变量</b></li>
 *   <li>{@code db.properties} 配置文件</li>
 *   <li>调用方传入的兜底值</li>
 * </ol>
 *
 * <p>{@code db.properties} 的查找顺序：{@code -Ddb.config=<路径>} 指定的文件 →
 * 运行目录 → 上一级目录 → {@code config/} 子目录 → classpath 根目录。
 *
 * <p>该文件已被 {@code .gitignore} 排除，不会进版本库；模板见 {@code db.properties.example}。
 * 数据库连接（{@link dao.DatabaseUtil}）与联系方式加密密钥（{@link SecurityUtil}）
 * 共用本类，避免两处各写一套「去哪找配置文件」的逻辑。
 */
public final class AppConfig {

    private static volatile Properties fileProperties;

    private AppConfig() {
    }

    /**
     * 按优先级取配置值。
     *
     * @param sysPropName 系统属性名，可为 null 表示跳过这一层
     * @param envName     环境变量名，可为 null 表示跳过这一层
     * @param fileKey     db.properties 中的键名
     * @return 取到的值；三层都没有则返回 null
     */
    public static String get(String sysPropName, String envName, String fileKey) {
        String value = firstNonBlank(
                sysPropName == null ? null : System.getProperty(sysPropName),
                envName == null ? null : System.getenv(envName),
                fileProperties().getProperty(fileKey));
        return value;
    }

    /** 同上，但三层都取不到时返回 {@code fallback} */
    public static String get(String sysPropName, String envName, String fileKey, String fallback) {
        String value = get(sysPropName, envName, fileKey);
        return value == null ? fallback : value;
    }

    /** 配置文件是否真的被找到了（用于给出「你还没建 db.properties」这类指引） */
    public static boolean hasConfigFile() {
        return !fileProperties().isEmpty();
    }

    /** db.properties 只读一次，之后复用 */
    private static Properties fileProperties() {
        Properties cached = fileProperties;
        if (cached == null) {
            synchronized (AppConfig.class) {
                if (fileProperties == null) {
                    fileProperties = load();
                }
                cached = fileProperties;
            }
        }
        return cached;
    }

    private static Properties load() {
        Properties props = new Properties();

        Path[] candidates = {
                pathOrNull(System.getProperty("db.config")),
                Paths.get("db.properties"),
                Paths.get("..", "db.properties"),
                Paths.get("config", "db.properties")
        };

        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    props.load(reader);
                    System.out.println("已加载配置文件: " + candidate.toAbsolutePath());
                    return props;
                } catch (IOException e) {
                    System.err.println("读取配置文件失败: " + candidate + " -> " + e.getMessage());
                }
            }
        }

        // 兜底：classpath 下的 db.properties（打成 jar 后仍然可用）
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                System.out.println("已加载 classpath 下的配置文件");
            }
        } catch (IOException e) {
            System.err.println("读取 classpath 配置失败: " + e.getMessage());
        }

        return props;
    }

    private static Path pathOrNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : Paths.get(value.trim());
    }

    /** 返回第一个非空白值；全为空白时返回 null */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
