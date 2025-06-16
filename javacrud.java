import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class javacrud {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:oracle:thin:@localhost:1521:xe", "system", "9642220646")) {

            while (true) {
                System.out.println("\nMenu:");
                System.out.println("1. Create Table");
                System.out.println("2. Insert Data (Manual)");
                System.out.println("3. Insert Data (From File)");
                System.out.println("4. Update Data (Manual)");
                System.out.println("5. Update Data (From File)");
                System.out.println("6. Delete Record (Manual)");
                System.out.println("7. Delete Record (From File)");
                System.out.println("8. Clear All Data (Truncate)");
                System.out.println("9. Delete Entire Table");
                System.out.println("10. View Records (Paginated)");
                System.out.println("11. Exit");
                System.out.println("12. Create EMP Salary Check Trigger");
                System.out.print("Enter your choice: ");
                String choice = scanner.nextLine();

                try {
                    switch (choice) {
                        case "1": createTable(conn); break;
                        case "2": insertManual(conn); break;
                        case "3": insertFromFile(conn); break;
                        case "4": updateManual(conn); break;
                        case "5": updateFromFile(conn); break;
                        case "6": deleteManual(conn); break;
                        case "7": deleteFromFile(conn); break;
                        case "8": truncateTable(conn); break;
                        case "9": deleteTable(conn); break;
                        case "10": selectRecords(conn); break;
                        case "11": System.out.println("Exiting..."); return;
                        case "12": createEmpSalaryCheckTrigger(conn); break;
                        default: System.out.println("Invalid choice.");
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    private static void insertManual(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            List<ColumnInfo> columns = getTableColumnsWithPrecision(conn, table);
            if (columns.isEmpty()) {
                System.out.println("Invalid table or no columns found.");
                return;
            }

            StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
            for (ColumnInfo col : columns) sql.append(col.name).append(", ");
            sql.setLength(sql.length() - 2);
            sql.append(") VALUES (").append("?, ".repeat(columns.size()));
            sql.setLength(sql.length() - 2);
            sql.append(")");

            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < columns.size(); i++) {
                    ColumnInfo col = columns.get(i);
                    while (true) {
                        System.out.print("Enter value for " + col.name + " (" + col.type + "): ");
                        String input = scanner.nextLine().trim();
                        try {
                            if (input.equalsIgnoreCase("null") || input.isEmpty()) {
                                pstmt.setNull(i + 1, java.sql.Types.NULL);
                            } else if (col.type.toUpperCase().contains("NUMBER")) {
                                pstmt.setBigDecimal(i + 1, new BigDecimal(input));
                            } else if (col.type.toUpperCase().contains("CHAR")) {
                                pstmt.setString(i + 1, input);
                            } else if (col.type.equalsIgnoreCase("DATE")) {
                                pstmt.setDate(i + 1, java.sql.Date.valueOf(input));
                            } else {
                                pstmt.setString(i + 1, input);
                            }
                            break;
                        } catch (Exception e) {
                            System.out.println("Invalid input. Try again.");
                        }
                    }
                }
                pstmt.executeUpdate();
                System.out.println("Record inserted.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Insert manual error: " + e.getMessage());
        }
    }

    private static void insertFromFile(Connection conn) {
        try {
            System.out.print("Enter file path (SQL file with INSERT statements): ");
            String filePath = scanner.nextLine().replace("\"", "").trim();

            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                int count = 0;
                try (Statement stmt = conn.createStatement()) {
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            stmt.executeUpdate(line);
                            count++;
                        }
                    }
                }
                System.out.println(count + " SQL statement(s) executed from file.");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Insert from file: " + e.getMessage());
        }
    }

    private static void updateManual(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            System.out.print("Enter column to update: ");
            String col = scanner.nextLine();
            System.out.print("Enter new value: ");
            String newVal = scanner.nextLine();
            System.out.print("Enter condition (e.g., empno=1): ");
            String cond = scanner.nextLine();

            String sql = "UPDATE " + table + " SET " + col + " = ? WHERE " + cond;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newVal);
                int updated = pstmt.executeUpdate();
                System.out.println(updated + " record(s) updated.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Update manual: " + e.getMessage());
        }
    }

    private static void updateFromFile(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            System.out.print("Enter CSV file path: ");
            String filePath = scanner.nextLine();
            System.out.print("Enter condition column name (e.g., empno): ");
            String conditionCol = scanner.nextLine();

            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String[] headers = br.readLine().split(",");
                List<String> updateCols = new ArrayList<>(Arrays.asList(headers));
                updateCols.remove(conditionCol);

                StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
                for (String col : updateCols) sql.append(col).append(" = ?, ");
                sql.setLength(sql.length() - 2);
                sql.append(" WHERE ").append(conditionCol).append(" = ?");

                try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] values = line.split(",");
                        for (int i = 0; i < updateCols.size(); i++)
                            pstmt.setString(i + 1, values[i + 1].trim());
                        pstmt.setString(updateCols.size() + 1, values[0].trim());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    System.out.println("Records updated from file.");
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Update from file: " + e.getMessage());
        }
    }

    private static void deleteManual(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            System.out.print("Enter condition (e.g., empno=1): ");
            String cond = scanner.nextLine();
            String sql = "DELETE FROM " + table + " WHERE " + cond;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int deleted = pstmt.executeUpdate();
                System.out.println(deleted + " record(s) deleted.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Delete manual: " + e.getMessage());
        }
    }

    private static void deleteFromFile(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            System.out.print("Enter file path with keys to delete: ");
            String filePath = scanner.nextLine();
            System.out.print("Enter condition column name (e.g., empno): ");
            String condCol = scanner.nextLine();

            String sql = "DELETE FROM " + table + " WHERE " + condCol + " = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    pstmt.setString(1, line.trim());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                System.out.println("Records deleted from file.");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Delete from file: " + e.getMessage());
        }
    }

    private static void truncateTable(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("TRUNCATE TABLE " + table);
                System.out.println("Table truncated.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Truncate: " + e.getMessage());
        }
    }

    private static void deleteTable(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE " + table);
                System.out.println("Table deleted.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Delete table: " + e.getMessage());
        }
    }

    private static void selectRecords(Connection conn) {
        try {
            System.out.print("Enter table name: ");
            String table = scanner.nextLine();
            System.out.print("Enter number of rows per page: ");
            int pageSize = Integer.parseInt(scanner.nextLine());

            int offset = 0;
            while (true) {
                String sql = "SELECT * FROM (SELECT a.*, ROWNUM rnum FROM (SELECT * FROM " + table +
                        ") a WHERE ROWNUM <= ?) WHERE rnum > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, offset + pageSize);
                    pstmt.setInt(2, offset);
                    ResultSet rs = pstmt.executeQuery();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    List<String[]> rows = new ArrayList<>();
                    int[] colWidths = new int[colCount];
                    for (int i = 0; i < colCount; i++)
                        colWidths[i] = meta.getColumnName(i + 1).length();

                    while (rs.next()) {
                        String[] row = new String[colCount];
                        for (int i = 0; i < colCount; i++) {
                            String val = rs.getString(i + 1);
                            row[i] = (val == null) ? "null" : val;
                            colWidths[i] = Math.max(colWidths[i], row[i].length());
                        }
                        rows.add(row);
                    }

                    if (rows.isEmpty()) {
                        System.out.println("No more records.");
                        break;
                    }

                    for (int i = 0; i < colCount; i++)
                        System.out.printf("%-" + (colWidths[i] + 2) + "s", meta.getColumnName(i + 1));
                    System.out.println();
                    for (int i = 0; i < Arrays.stream(colWidths).sum() + (2 * colCount); i++)
                        System.out.print("-");
                    System.out.println();

                    for (String[] row : rows) {
                        for (int i = 0; i < colCount; i++)
                            System.out.printf("%-" + (colWidths[i] + 2) + "s", row[i]);
                        System.out.println();
                    }

                    System.out.print("Next page? (y/n): ");
                    if (!scanner.nextLine().equalsIgnoreCase("y")) break;
                    offset += pageSize;
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Select records: " + e.getMessage());
        }
    }

    private static void createTable(Connection conn) throws SQLException {
        System.out.print("Enter table name: ");
        String table = scanner.nextLine();
        System.out.print("Enter number of columns: ");
        int colCount = Integer.parseInt(scanner.nextLine());

        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(table).append(" (");
        for (int i = 0; i < colCount; i++) {
            System.out.print("Column " + (i + 1) + " name: ");
            String name = scanner.nextLine();
            System.out.print("Type (e.g., VARCHAR2(100), NUMBER(7,2), DATE): ");
            String type = scanner.nextLine();
            sb.append(name).append(" ").append(type);
            if (i < colCount - 1) sb.append(", ");
        }
        sb.append(")");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sb.toString());
            System.out.println("Table created successfully.");
        }
    }

    private static List<ColumnInfo> getTableColumnsWithPrecision(Connection conn, String table) {
        List<ColumnInfo> list = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?")) {
            pstmt.setString(1, table.toUpperCase());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("DATA_TYPE");
                int precision = rs.getInt("DATA_PRECISION");
                int scale = rs.getInt("DATA_SCALE");
                list.add(new ColumnInfo(name, type, precision, scale));
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Getting table metadata: " + e.getMessage());
        }
        return list;
    }

    static class ColumnInfo {
        String name, type;
        int precision, scale;

        ColumnInfo(String name, String type, int precision, int scale) {
            this.name = name;
            this.type = type;
            this.precision = precision;
            this.scale = scale;
        }
    }

    private static void createEmpSalaryCheckTrigger(Connection conn) {
        try {
            String triggerSQL =
                "CREATE OR REPLACE TRIGGER TRG_EMP_SALARY_CHECK\n" +
                "BEFORE INSERT OR UPDATE ON EMP\n" +
                "FOR EACH ROW\n" +
                "BEGIN\n" +
                "  IF :NEW.SAL IS NULL OR :NEW.SAL = 0 THEN\n" +
                "    RAISE_APPLICATION_ERROR(-20001, 'Salary must not be zero or null');\n" +
                "  END IF;\n" +
                "END;";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(triggerSQL);
                System.out.println("Trigger 'TRG_EMP_SALARY_CHECK' created successfully on EMP.");
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Creating trigger: " + e.getMessage());
        }
    }
}
