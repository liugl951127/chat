import java.sql.*;
import java.nio.file.*;

public class SqlValidate {
    public static void main(String[] args) throws Exception {
        // 1. 加载 H2 (MySQL 兼容模式)
        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection(
            "jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        Statement st = conn.createStatement();

        // 2. 读 SQL
        String sql = Files.readString(Path.of(args[0]));

        // 3. 拆成 CREATE TABLE 块, 逐个执行
        String[] parts = sql.split("(?=\\bCREATE TABLE\\b)");
        int pass = 0, fail = 0;

        for (String part : parts) {
            if (!part.trim().toUpperCase().startsWith("CREATE TABLE")) continue;
            try {
                st.execute(part);
                
                String name = part.replaceAll("(?si).*?CREATE TABLE (?:IF NOT EXISTS )?(\\w+).*", "$1");
                System.out.println("✅ " + name);
                pass++;
            } catch (Exception e) {
                String name = part.replaceAll("(?si).*?CREATE TABLE (?:IF NOT EXISTS )?(\\w+).*", "$1");
                System.out.println("❌ " + name + ": " + e.getMessage().split("\n")[0]);
                fail++;
            }
        }
        System.out.println("\n=========================");
        System.out.println("通过: " + pass + ", 失败: " + fail);
        conn.close();
        System.exit(fail == 0 ? 0 : 1);
    }
}
