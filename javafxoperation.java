import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class javafxoperation extends Application {

    private final String url = "jdbc:oracle:thin:@localhost:1521:xe";
    private final String user = "system";
    private final String password = "9642220646"; // Replace with your Oracle password

    public static void main(String[] args) {
        launch(args);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }

    public static class RowData {
        SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        List<SimpleStringProperty> values;

        RowData(List<SimpleStringProperty> values) {
            this.values = values;
        }
    }

    private List<String> getPrimaryKeyColumns(Connection conn, String tableName) throws SQLException {
        List<String> pkCols = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        String schema = conn.getSchema();
        if (schema == null || schema.isEmpty()) {
            schema = user.toUpperCase();
        }

        try (ResultSet pkRs = metaData.getPrimaryKeys(null, schema, tableName.toUpperCase())) {
            while (pkRs.next()) {
                pkCols.add(pkRs.getString("COLUMN_NAME"));
            }
        }
        return pkCols;
    }

    @Override
    public void start(Stage primaryStage) {
        Label message = new Label("SQL CRUD Operations (Oracle DB)");
        Button createBtn = new Button("Create Table");
        Button insertBtn = new Button("Insert Values");
        Button updateBtn = new Button("Update Table");
        Button deleteBtn = new Button("Delete Table");
        Button truncateBtn = new Button("Truncate Table");
        Button dropBtn = new Button("Drop Table");
        Button selectBtn = new Button("Select Records");

        createBtn.setOnAction(e -> showCreateTableWindow());
        insertBtn.setOnAction(e -> showInsertWindow());
        updateBtn.setOnAction(e -> showUpdateWindow());
        deleteBtn.setOnAction(e -> showSimpleTableActionWindow("Delete", "DELETE FROM "));
        truncateBtn.setOnAction(e -> showSimpleTableActionWindow("Truncate", "TRUNCATE TABLE "));
        dropBtn.setOnAction(e -> showSimpleTableActionWindow("Drop", "DROP TABLE "));
        selectBtn.setOnAction(e -> showSelectWindow());

        VBox root = new VBox(15, message, createBtn, insertBtn, updateBtn, deleteBtn, truncateBtn, dropBtn, selectBtn);
        root.setStyle("-fx-padding: 30; -fx-alignment: center;");
        Scene scene = new Scene(root, 400, 550);
        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX Oracle DB Manager");
        primaryStage.show();
    }

    private void showSelectWindow() {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button loadBtn = new Button("Load Table");

        VBox layout = new VBox(10, new Label("Enter Table Name:"), tableField, loadBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 200));
        stage.setTitle("Select Records");
        stage.show();

        loadBtn.setOnAction(ev -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            Stage selectStage = new Stage();
            VBox contentLayout = new VBox(10);
            contentLayout.setStyle("-fx-padding: 10;");

            TableView<RowData> tableView = new TableView<>();
            tableView.setEditable(true);
            Button deleteSelectedBtn = new Button("Delete Selected Rows");
            contentLayout.getChildren().addAll(new ScrollPane(tableView), deleteSelectedBtn);

            List<String> columnNames = new ArrayList<>();
            final int[] columnCount = {0}; // <-- Changed to array for lambda access

            ObservableList<RowData> data = FXCollections.observableArrayList();

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

                ResultSetMetaData meta = rs.getMetaData();
                columnCount[0] = meta.getColumnCount();

                TableColumn<RowData, Boolean> selectCol = new TableColumn<>("Select");
                selectCol.setCellValueFactory(param -> param.getValue().selected);
                selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
                tableView.getColumns().add(selectCol);

                for (int i = 1; i <= columnCount[0]; i++) {
                    String colName = meta.getColumnName(i);
                    columnNames.add(colName);
                    final int colIndex = i - 1;
                    TableColumn<RowData, String> col = new TableColumn<>(colName);
                    col.setCellValueFactory(cellData -> cellData.getValue().values.get(colIndex));
                    tableView.getColumns().add(col);
                }

                while (rs.next()) {
                    List<SimpleStringProperty> rowValues = new ArrayList<>();
                    for (int i = 1; i <= columnCount[0]; i++) {
                        rowValues.add(new SimpleStringProperty(rs.getString(i)));
                    }
                    data.add(new RowData(rowValues));
                }

                tableView.setItems(data);
                List<String> pkColumns = getPrimaryKeyColumns(conn, tableName);

                deleteSelectedBtn.setOnAction(e -> {
                    List<RowData> selectedRows = new ArrayList<>();
                    for (RowData row : data) {
                        if (row.selected.get()) selectedRows.add(row);
                    }

                    if (selectedRows.isEmpty()) {
                        showAlert(Alert.AlertType.WARNING, "No rows selected.");
                        return;
                    }

                    if (pkColumns.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "No primary key found for the table.");
                        return;
                    }

                    int deletedCount = 0;
                    try (Connection delConn = getConnection();
                         Statement delStmt = delConn.createStatement()) {

                        for (RowData row : selectedRows) {
                            StringBuilder where = new StringBuilder();
                            for (int i = 0; i < columnCount[0]; i++) {
                                String column = columnNames.get(i);
                                if (!pkColumns.contains(column)) continue;

                                String value = row.values.get(i).get();

                                if (where.length() > 0) where.append(" AND ");
                                if (value == null || value.equalsIgnoreCase("null") || value.isEmpty()) {
                                    where.append(column).append(" IS NULL");
                                } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                                    where.append(column).append("=").append(value);
                                } else {
                                    where.append(column).append("='").append(value.replace("'", "''")).append("'");
                                }
                            }

                            if (where.length() == 0) continue;
                            String delQuery = "DELETE FROM " + tableName + " WHERE " + where;
                            int affected = delStmt.executeUpdate(delQuery);
                            if (affected > 0) deletedCount++;
                        }

                        showAlert(Alert.AlertType.INFORMATION, deletedCount + " row(s) deleted.");
                        selectStage.close();

                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, ex.getMessage());
                    }
                });

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }

            selectStage.setScene(new Scene(contentLayout, 800, 600));
            selectStage.setTitle("Table: " + tableName);
            selectStage.show();
        });
    }

    private void showCreateTableWindow() {
        Stage stage = new Stage();
        TextField tableNameField = new TextField();
        TextField fieldCountField = new TextField();
        Button nextBtn = new Button("Next");

        VBox layout = new VBox(10, new Label("Table Name:"), tableNameField,
                new Label("Number of Fields:"), fieldCountField, nextBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 250));
        stage.setTitle("Create Table");
        stage.show();

        nextBtn.setOnAction(e -> {
            String tableName = tableNameField.getText().trim();
            int count;
            try {
                count = Integer.parseInt(fieldCountField.getText().trim());
                if (count <= 0) {
                    showAlert(Alert.AlertType.ERROR, "Field count must be positive.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid field count.");
                return;
            }
            showFieldCountWindow(stage, tableName, count);
        });
    }

    private void showFieldCountWindow(Stage parent, String tableName, int count) {
        Stage stage = new Stage();
        VBox fieldsBox = new VBox(10);
        List<TextField> names = new ArrayList<>();
        List<TextField> types = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            TextField name = new TextField();
            TextField type = new TextField();
            names.add(name);
            types.add(type);
            HBox fieldLine = new HBox(10, new Label("Field " + (i + 1) + ":"), name, type);
            fieldsBox.getChildren().add(fieldLine);
        }

        Button createBtn = new Button("Create Table");
        fieldsBox.getChildren().add(createBtn);
        fieldsBox.setStyle("-fx-padding: 20;");
        stage.setScene(new Scene(fieldsBox, 450, 300));
        stage.setTitle("Field Details");
        stage.show();

        createBtn.setOnAction(e -> {
            if (tableName == null || tableName.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name cannot be empty.");
                return;
            }
            // Validate field names and types
            for (int i = 0; i < count; i++) {
                if (names.get(i).getText().trim().isEmpty() || types.get(i).getText().trim().isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "Field names and types cannot be empty.");
                    return;
                }
            }
            StringBuilder sb = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
            for (int i = 0; i < count; i++) {
                sb.append(names.get(i).getText().trim()).append(" ").append(types.get(i).getText().trim());
                if (i < count - 1) sb.append(", ");
            }
            sb.append(")");
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sb.toString());
                showAlert(Alert.AlertType.INFORMATION, "Table created successfully.");
                stage.close();
                if (parent != null) parent.close();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }

    private void showInsertWindow() {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button loadBtn = new Button("Load Table Structure");

        VBox layout = new VBox(10, new Label("Table Name:"), tableField, loadBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 400, 150));
        stage.setTitle("Insert Values");
        stage.show();

        loadBtn.setOnAction(ev -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            List<String> columnNames = new ArrayList<>();
            List<String> columnTypes = new ArrayList<>();
            final int[] columnCount = {0}; // <-- Changed to array for lambda access

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE 1=0")) {

                ResultSetMetaData meta = rs.getMetaData();
                columnCount[0] = meta.getColumnCount();

                for (int i = 1; i <= columnCount[0]; i++) {
                    columnNames.add(meta.getColumnName(i));
                    columnTypes.add(meta.getColumnTypeName(i));
                }

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
                return;
            }

            Stage insertStage = new Stage();
            VBox insertLayout = new VBox(10);
            List<TextField> inputs = new ArrayList<>();

            for (int i = 0; i < columnCount[0]; i++) {
                TextField tf = new TextField();
                inputs.add(tf);
                HBox row = new HBox(10, new Label(columnNames.get(i) + " (" + columnTypes.get(i) + "):"), tf);
                insertLayout.getChildren().add(row);
            }

            Button insertBtn = new Button("Insert Record");
            insertLayout.getChildren().add(insertBtn);
            insertLayout.setStyle("-fx-padding: 20;");
            insertStage.setScene(new Scene(insertLayout, 450, 400));
            insertStage.setTitle("Insert into " + tableName);
            insertStage.show();

            insertBtn.setOnAction(e -> {
                StringBuilder columnsPart = new StringBuilder();
                StringBuilder valuesPart = new StringBuilder();

                for (int i = 0; i < columnCount[0]; i++) {
                    String value = inputs.get(i).getText().trim();

                    if (columnsPart.length() > 0) {
                        columnsPart.append(", ");
                        valuesPart.append(", ");
                    }
                    columnsPart.append(columnNames.get(i));
                    if (value.isEmpty()) {
                        valuesPart.append("NULL");
                    } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                        valuesPart.append(value);
                    } else {
                        valuesPart.append("'").append(value.replace("'", "''")).append("'");
                    }
                }

                String insertSQL = "INSERT INTO " + tableName + " (" + columnsPart + ") VALUES (" + valuesPart + ")";
                try (Connection conn = getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(insertSQL);
                    showAlert(Alert.AlertType.INFORMATION, "Record inserted successfully.");
                    insertStage.close();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, ex.getMessage());
                }
            });
        });
    }

    private void showUpdateWindow() {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button loadBtn = new Button("Load Table Data");

        VBox layout = new VBox(10, new Label("Enter Table Name:"), tableField, loadBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 180));
        stage.setTitle("Update Table");
        stage.show();

        loadBtn.setOnAction(ev -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            List<String> columnNames = new ArrayList<>();
            final int[] columnCount = {0}; // <-- Changed to array for lambda access

            TableView<RowData> tableView = new TableView<>();
            tableView.setEditable(true);

            ObservableList<RowData> data = FXCollections.observableArrayList();

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

                ResultSetMetaData meta = rs.getMetaData();
                columnCount[0] = meta.getColumnCount();

                // Get primary key columns
                List<String> pkColumns = getPrimaryKeyColumns(conn, tableName);
                if (pkColumns.isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "No primary key defined for table '" + tableName + "'. Update operation not supported.");
                    return;
                }

                TableColumn<RowData, Boolean> selectCol = new TableColumn<>("Select");
                selectCol.setCellValueFactory(param -> param.getValue().selected);
                selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
                tableView.getColumns().add(selectCol);

                for (int i = 1; i <= columnCount[0]; i++) {
                    String colName = meta.getColumnName(i);
                    columnNames.add(colName);
                    final int colIndex = i - 1;
                    TableColumn<RowData, String> col = new TableColumn<>(colName);
                    col.setCellValueFactory(cellData -> cellData.getValue().values.get(colIndex));
                    col.setCellFactory(TextFieldTableCell.forTableColumn());
                    col.setEditable(true);
                    col.setOnEditCommit(event -> {
                        RowData row = event.getRowValue();
                        row.values.get(colIndex).set(event.getNewValue());
                    });
                    tableView.getColumns().add(col);
                }

                while (rs.next()) {
                    List<SimpleStringProperty> rowValues = new ArrayList<>();
                    for (int i = 1; i <= columnCount[0]; i++) {
                        rowValues.add(new SimpleStringProperty(rs.getString(i)));
                    }
                    data.add(new RowData(rowValues));
                }

                tableView.setItems(data);

                Button updateSelectedBtn = new Button("Update Selected Rows");

                updateSelectedBtn.setOnAction(event -> {
                    List<RowData> selectedRows = new ArrayList<>();
                    for (RowData row : data) {
                        if (row.selected.get()) selectedRows.add(row);
                    }

                    if (selectedRows.isEmpty()) {
                        showAlert(Alert.AlertType.WARNING, "No rows selected.");
                        return;
                    }

                    int updatedCount = 0;
                    try (Connection updateConn = getConnection();
                         Statement updateStmt = updateConn.createStatement()) {

                        for (RowData row : selectedRows) {
                            StringBuilder setClause = new StringBuilder();
                            StringBuilder whereClause = new StringBuilder();

                            // Build SET clause with all columns except PK
                            for (int i = 0; i < columnCount[0]; i++) {
                                String col = columnNames.get(i);
                                String val = row.values.get(i).get();

                                // Skip PK columns for SET clause
                                if (pkColumns.contains(col)) continue;

                                if (setClause.length() > 0) setClause.append(", ");
                                if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
                                    setClause.append(col).append("=NULL");
                                } else if (val.matches("-?\\d+(\\.\\d+)?")) {
                                    setClause.append(col).append("=").append(val);
                                } else {
                                    setClause.append(col).append("='").append(val.replace("'", "''")).append("'");
                                }
                            }

                            // Build WHERE clause with primary keys only
                            for (int i = 0; i < columnCount[0]; i++) {
                                String col = columnNames.get(i);
                                if (!pkColumns.contains(col)) continue;

                                String val = row.values.get(i).get();

                                if (whereClause.length() > 0) whereClause.append(" AND ");
                                if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
                                    whereClause.append(col).append(" IS NULL");
                                } else if (val.matches("-?\\d+(\\.\\d+)?")) {
                                    whereClause.append(col).append("=").append(val);
                                } else {
                                    whereClause.append(col).append("='").append(val.replace("'", "''")).append("'");
                                }
                            }

                            if (whereClause.length() == 0) {
                                showAlert(Alert.AlertType.ERROR, "Cannot update row because primary key value(s) missing.");
                                return;
                            }

                            // If setClause is empty (all PK columns), skip update
                            if (setClause.length() == 0) {
                                showAlert(Alert.AlertType.WARNING, "No non-primary key columns to update.");
                                return;
                            }

                            String updateSQL = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;

                            System.out.println("Update SQL: " + updateSQL); // Debug output

                            int affected = updateStmt.executeUpdate(updateSQL);
                            if (affected > 0) updatedCount++;
                        }

                        showAlert(Alert.AlertType.INFORMATION, updatedCount + " row(s) updated.");
                        stage.close();

                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, ex.getMessage());
                    }
                });

                VBox root = new VBox(10, new Label("Table: " + tableName), new ScrollPane(tableView), updateSelectedBtn);
                root.setStyle("-fx-padding: 20;");
                Stage updateStage = new Stage();
                updateStage.setScene(new Scene(root, 800, 600));
                updateStage.setTitle("Update Table: " + tableName);
                updateStage.show();

                // Close the loading window
                stage.close();

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }

    private void showSimpleTableActionWindow(String actionName, String sqlPrefix) {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button executeBtn = new Button(actionName);

        VBox layout = new VBox(10, new Label("Enter Table Name:"), tableField, executeBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 180));
        stage.setTitle(actionName + " Table");
        stage.show();

        executeBtn.setOnAction(e -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlPrefix + tableName);
                showAlert(Alert.AlertType.INFORMATION, actionName + " operation completed.");
                stage.close();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }
}

