package com.micro.kfschecker;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Callback;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;

import static org.apache.commons.io.IOUtils.toByteArray;


public class Controller {

    @FXML
    private VBox tablesContainer;

    @FXML
    private Button loadReportButton;

    @FXML
    private Button addReportButton;

    @FXML
    private Button compareButton;

    @FXML
    private Button loadMyQDataButton;

    @FXML
    private Button checkButton;

    @FXML
    private Button saveToPDFButtton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private TextArea logOutput;

    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String myqQuery;
    private TextAreaAppender TextAreaAppender;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @FXML
    private void initialize() {
        compareButton.setDisable(true);
        loadDatabaseConfig();
        loadQuery();
        // Propojení TextArea s Log4j2 Appenderem
        TextAreaAppender.setTextArea("logOutput", logOutput);

        // Nastavení tlačítka "CHECK" jako neaktivního při spuštění
        checkButton.setDisable(true);
        // Nastavení tlačítka "LOAD MyQ DATA" jako neaktivního při spuštění
        loadMyQDataButton.setDisable(true);
        // Nastavení tlačítka "EXPORT TO PDF" jako neaktivního při spuštění
        saveToPDFButtton.setDisable(true);
    }

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdown(); // Zakáže přijímání nových úkolů
            try {
                // Čeká na dokončení běžících úkolů s časovým limitem
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService se nepodařilo ukončit včas.");
                    executorService.shutdownNow(); // Násilně ukončí běžící úkoly
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loadDatabaseConfig() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("h:/Coding/Java/Projects/KFSChecker/mnt/data/db-connection.txt"));
            String host = lines.get(0).split(": ")[1];
            String port = lines.get(1).split(": ")[1];
            String path = lines.get(2).split(": ")[1];
            dbUsername = lines.get(3).split(": ")[1];
            dbPassword = lines.get(4).split(": ")[1];
            dbUrl = "jdbc:firebirdsql://" + host + ":" + port + "/" + path;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadQuery() {
        try {
            myqQuery = new String(Files.readAllBytes(Paths.get("h:/Coding/Java/Projects/KFSChecker/mnt/data/myq-query.sql")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String convertDateFormat(String inputDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss");
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return outputFormat.format(inputFormat.parse(inputDate));
        } catch (ParseException e) {
            e.printStackTrace();
            return inputDate;
        }
    }

    private <T> TableCell<ObservableList<String>, T> getTableCell(TableView<ObservableList<String>> tableView, int row, int column) {
        if (row < 0 || row >= tableView.getItems().size() || column < 0 || column >= tableView.getColumns().size()) {
            return null;
        }
        TableColumn<ObservableList<String>, T> tableColumn = (TableColumn<ObservableList<String>, T>) tableView.getColumns().get(column);
        return tableColumn.getCellFactory().call(tableColumn);
    }

    @FXML
    private void handleLoadReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            tablesContainer.getChildren().clear(); // Smazání všech tabulek před načtením nové
            loadCSV(file);
        }
        updateCompareButtonState();
    }

    @FXML
    private void handleAddReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadCSV(file);
        }
        updateCompareButtonState();
    }

    private void loadCSV(File file) {
        //System.out.println("Loading csv file...");
        logger.info("Loading csv file...");
        Label fileLabel = new Label(file.getName());
        fileLabel.setFont(Font.font("Arial", 14));
        fileLabel.setTextFill(Color.web("#333333"));
        fileLabel.setStyle("-fx-font-weight: bold;");

        TableView<ObservableList<String>> tableView = new TableView<>();

        CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setQuote('\"') // Nastavíme klasickou-dvojitou uvozovku jako znak pro řetězce
                .build();

        try (
             Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()));
             //CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT) // Používáme výchozí formát, tj. středník jako oddělovač a dvojité uvozovky pro řetězce
             CSVParser csvParser = new CSVParser(reader, csvFormat)
        )
        {

            boolean isHeader = true;
            for (CSVRecord csvRecord : csvParser) {
                if (isHeader) {
                    for (int i = 0; i < csvRecord.size(); i++) {
                        String headerText = csvRecord.get(i);
                        if (i == 0) {
                            headerText = headerText.replace("\"", ""); // Odstraníme dvojité uvozovky z hlavičky prvního sloupce
                        }
                        TableColumn<ObservableList<String>, String> column = new TableColumn<>(headerText);
                        final int colIndex = i;
                        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(colIndex)));
                        tableView.getColumns().add(column);
                    }
                    isHeader = false;
                } else {
                    ObservableList<String> rowData = FXCollections.observableArrayList();
                    for (String value : csvRecord) {
                        rowData.add(value);
                    }
                    // Kontrola na prázdný řádek (po oříznutí)
                    boolean isEmptyRow = true;
                    for (String value : rowData) {
                        if (!value.trim().isEmpty()) {
                            isEmptyRow = false;
                            break;
                        }
                    }
                    if (!isEmptyRow) {
                        tableView.getItems().add(rowData);
                    }
                }
            }
            //System.out.println("OK");
            logger.info("OK");

        } catch (IOException e) {
            //System.err.println("Error: Cannot load csv file");
            logger.error("Error: Cannot load csv file");
            e.printStackTrace();
        }
        tablesContainer.getChildren().addAll(fileLabel, tableView);
        //System.out.println("there are  oncreate " + tablesContainer.getChildren().size());
        logger.info("there are  oncreate " + tablesContainer.getChildren().size());

        saveToPDFButtton.setDisable(false);
    }

    private void updateCompareButtonState() {
        compareButton.setDisable(tablesContainer.getChildren().size() < 4);
    }

    @FXML
    private void handleCompare() {
        if (tablesContainer.getChildren().size() < 4) {
            return;
        }

        TableView<ObservableList<String>> firstTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(1);
        TableView<ObservableList<String>> secondTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(3);

        Label comparedLabel = new Label("COMPARED");
        comparedLabel.setFont(Font.font("Arial", 14));
        comparedLabel.setTextFill(Color.web("#318fd8"));
        comparedLabel.setStyle("-fx-font-weight: bold;");

        TableView<ObservableList<String>> resultTable = new TableView<>();

        for (int i = 0; i < firstTable.getColumns().size(); i++) {
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(firstTable.getColumns().get(i).getText());
            final int colIndex = i;
            column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(colIndex)));
            resultTable.getColumns().add(column);
        }

        int rowCount = Math.max(firstTable.getItems().size(), secondTable.getItems().size());
        for (int i = 0; i < rowCount; i++) {
            ObservableList<String> firstRow = i < firstTable.getItems().size() ? firstTable.getItems().get(i) : null;
            ObservableList<String> secondRow = i < secondTable.getItems().size() ? secondTable.getItems().get(i) : null;

            ObservableList<String> resultRow = FXCollections.observableArrayList();
            if (firstRow != null && secondRow != null) {
                for (int j = 0; j < Math.max(firstRow.size(), secondRow.size()); j++) {
                    String firstCell = j < firstRow.size() ? firstRow.get(j) : "";
                    String secondCell = j < secondRow.size() ? secondRow.get(j) : "";

                    if (firstCell.matches("\\d+") && secondCell.matches("\\d+")) {
                        int diff = Math.abs(Integer.parseInt(secondCell) - Integer.parseInt(firstCell));
                        resultRow.add(String.valueOf(diff));
                    } else {
                        resultRow.add(firstCell.equals(secondCell) ? firstCell : "");
                    }
                }
            } else if (firstRow != null) {
                resultRow.addAll(firstRow);
            } else if (secondRow != null) {
                resultRow.addAll(secondRow);
            }
            resultTable.getItems().add(resultRow);
        }

        tablesContainer.getChildren().addAll(comparedLabel, resultTable);
        loadMyQDataButton.setDisable(false); // Aktivace tlačítka LOAD MYQ DATA
        //System.out.println("there are  oncreate " + tablesContainer.getChildren().size());
        logger.info("there are  oncreate " + tablesContainer.getChildren().size());
    }

    @FXML
    private void handleLoadMyQData() {
        if (tablesContainer.getChildren().size() < 4) {
            return;
        }

        logger.info("Starting to load MyQ Data in background...");
        progressBar.setVisible(true);
        logger.info("Nastavuji ProgressBar na true");

        // Pouze pro testování
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        checkButton.setDisable(true); // Deaktivace tlačítka CHECK během načítání

        TableView<ObservableList<String>> firstTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(1);
        TableView<ObservableList<String>> secondTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(3);

        Task<TableView<ObservableList<String>>> loadTask = new Task<TableView<ObservableList<String>>>() {
            @Override
            protected TableView<ObservableList<String>> call() throws Exception {
                // Attempt to load the Firebird JDBC driver
                try {
                    Class.forName("org.firebirdsql.jdbc.FBDriver");
                } catch (ClassNotFoundException e) {
                    logger.error("Error: Firebird JDBC driver not found.", e);
                    throw e; // Re-throw the exception to be caught in setOnFailed
                }

                Connection conn = null;
                int attempts = 0;
                while (attempts < 3) {
                    try {
                        conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                        logger.info("Connection to database established successfully!");
                        break;
                    } catch (SQLException e) {
                        attempts++;
                        logger.error("Connection attempt {} failed.", attempts, e);
                        if (attempts == 3) {
                            logger.error("Error: Unable to connect to the database after 3 attempts.", e);
                            throw e; // Re-throw the exception
                        }
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                TableView<ObservableList<String>> myqTable = new TableView<>();
                int totalRows = firstTable.getItems().size();
                for (int i = 0; i < totalRows; i++) {
                    String startDate = convertDateFormat(firstTable.getItems().get(i).get(6));
                    String endDate = convertDateFormat(secondTable.getItems().get(i).get(6));

                    // Ověření a prohození hodnot, pokud je to potřeba
                    if (endDate.compareTo(startDate) < 0) {
                        String temp = startDate;
                        startDate = convertDateFormat(secondTable.getItems().get(i).get(6));
                        endDate = convertDateFormat(firstTable.getItems().get(i).get(6));
                    }

                    String serialNumber = firstTable.getItems().get(i).get(2);

                    String query = myqQuery.replace("@StartDate", "'" + startDate + "'")
                            .replace("@EndDate", "'" + endDate + "'")
                            .replace("@SerialNumber", "'" + serialNumber + "'");

                    logger.info("Executing query for serial number: {}", serialNumber);

                    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        if (myqTable.getColumns().isEmpty()) {
                            for (int j = 1; j <= metaData.getColumnCount(); j++) {
                                TableColumn<ObservableList<String>, String> column = new TableColumn<>(metaData.getColumnLabel(j));
                                final int colIndex = j - 1;
                                column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(colIndex)));
                                myqTable.getColumns().add(column);
                            }
                        }
                        while (rs.next()) {
                            ObservableList<String> row = FXCollections.observableArrayList();
                            for (int j = 1; j <= metaData.getColumnCount(); j++) {
                                row.add(rs.getString(j));
                            }
                            myqTable.getItems().add(row);
                        }
                    }
                    updateProgress(i + 1, totalRows); // Aktualizace průběhu
                }

                if (conn != null) {
                    conn.close();
                }

                return myqTable;
            }
        };

        loadTask.setOnSucceeded(event -> {
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind(); // Zrušíme svazek
            progressBar.setProgress(0);             // Nyní můžeme nastavit hodnotu
            TableView<ObservableList<String>> myqTable = loadTask.getValue();
            Label myqLabel = new Label("MyQ DATA");
            myqLabel.setFont(Font.font("Arial", 14));
            myqLabel.setTextFill(Color.web("#f24f13"));
            myqLabel.setStyle("-fx-font-weight: bold;");
            tablesContainer.getChildren().addAll(myqLabel, myqTable);
            checkButton.setDisable(false); // Aktivace tlačítka CHECK po dokončení
            logger.info("MyQ Data loaded successfully and added to UI.");
        });

        loadTask.setOnFailed(event -> {
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind(); // Zrušíme svazek
            progressBar.setProgress(0);             // Nyní můžeme nastavit hodnotu
            Throwable e = loadTask.getException();
            logger.error("Error during MyQ data loading.", e);
            // Zde můžete zobrazit uživateli chybovou zprávu
        });

        // Svatání ProgressBaru s Taskem (volitelné, pokud chcete zobrazovat průběh)
        progressBar.progressProperty().bind(loadTask.progressProperty());

        executorService.submit(loadTask);
    }

    @FXML
    private void handleCheck() {
        if (tablesContainer.getChildren().size() < 8) { // Upravená kontrola, index 7 znamená minimálně 8 prvků
            //System.out.println("Error: Tables are missing.");
            logger.error("Error: Tables are missing.");
            return;
        }

        TableView<ObservableList<String>> resultTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(5);
        TableView<ObservableList<String>> myqTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(7);

        List<String> warnings = new ArrayList<>();
        Set<TablePosition> resultMismatches = new HashSet<>(); // Pro uložení pozic nesrovnalostí v resultTable
        Set<TablePosition> myqMismatches = new HashSet<>(); // Pro uložení pozic nesrovnalostí v myqTable

        for (int i = 0; i < resultTable.getItems().size(); i++) {
            ObservableList<String> resultRow = resultTable.getItems().get(i);
            if (resultRow.size() > 2) {
                String keyResult = resultRow.get(2);

                for (int j = 0; j < myqTable.getItems().size(); j++) {
                    ObservableList<String> myqRow = myqTable.getItems().get(j);
                    if (myqRow.size() > 2) {
                        String keyMyq = myqRow.get(2);

                        if (keyResult.equals(keyMyq)) {
                            if (resultRow.size() > 8 && myqRow.size() > 6) {
                                String resultValue1 = resultRow.get(8);
                                String myqValue1 = myqRow.get(6);

                                if (!Objects.equals(resultValue1, myqValue1)) {
                                    String warning = "Mismatch in column 9/7 for key " + keyResult + ": " + resultValue1 + " != " + myqValue1;
                                    warnings.add(warning);
                                    //System.out.println(warning);
                                    logger.info(warning);
                                    resultMismatches.add(new TablePosition<>(resultTable, i, resultTable.getColumns().get(8)));
                                    myqMismatches.add(new TablePosition<>(myqTable, j, myqTable.getColumns().get(6)));
                                }
                            }

                            if (resultRow.size() > 9 && myqRow.size() > 7) {
                                String resultValue2 = resultRow.get(9);
                                String myqValue2 = myqRow.get(7);

                                if (!Objects.equals(resultValue2, myqValue2)) {
                                    String warning = "Mismatch in column 10/8 for key " + keyResult + ": " + resultValue2 + " != " + myqValue2;
                                    warnings.add(warning);
                                    //System.out.println(warning);
                                    logger.info(warning);
                                    resultMismatches.add(new TablePosition<>(resultTable, i, resultTable.getColumns().get(9)));
                                    myqMismatches.add(new TablePosition<>(myqTable, j, myqTable.getColumns().get(7)));
                                }
                            }
                        }
                    }
                }
            }
        }

        Alert alert;
        if (!warnings.isEmpty()) {
            alert = new Alert(AlertType.WARNING);
            alert.setTitle("Mismatches Found");
            alert.setHeaderText(null);
            alert.setContentText(String.join("\n", warnings));
            alert.showAndWait();

            // Nastavení CellFactory pro podbarvení buněk
            highlightMismatches(resultTable, resultMismatches);
            highlightMismatches(myqTable, myqMismatches);

        } else {
            alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Check Completed");
            alert.setHeaderText(null);
            alert.setContentText("No mismatches found.");
            //System.out.println("No mismatches found.");
            logger.info("No mismatches found.");
            alert.showAndWait();
        }
    }

    private void highlightMismatches(TableView<ObservableList<String>> tableView, Set<TablePosition> mismatches) {
        for (TableColumn<?, ?> col : tableView.getColumns()) {
            TableColumn<ObservableList<String>, Object> column = (TableColumn<ObservableList<String>, Object>) col;
            column.setCellFactory(c -> new TableCell<ObservableList<String>, Object>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText(null);
                        getStyleClass().remove("mismatch-cell"); // Odstraníme třídu, pokud je buňka prázdná
                    } else {
                        setText(item == null ? "" : item.toString());
                        TablePosition pos = new TablePosition(tableView, getTableRow().getIndex(), column);
                        if (mismatches.contains(pos)) {
                            if (!getStyleClass().contains("mismatch-cell")) {
                                getStyleClass().add("mismatch-cell"); // Přidáme CSS třídu pro mismatch
                            }
                        } else {
                            getStyleClass().remove("mismatch-cell"); // Odstraníme CSS třídu, pokud není mismatch
                        }
                    }
                }
            });
        }
    }


    @FXML
    private void handleSaveToPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        String datePrefix = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fileChooser.setInitialFileName("KFSCheck_" + datePrefix + ".pdf");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            progressBar.setVisible(true);

            Task<Void> saveTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Document document = new Document(new Rectangle(PageSize.A4.rotate().getWidth(), PageSize.A4.rotate().getHeight()), 20, 20, 80, 30);
                    PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
                    writer.setPageEvent(new PageNumberHeader());
                    document.open();

                    // Získání referencí na tabulky
                    TableView<ObservableList<String>> resultTable = null;
                    TableView<ObservableList<String>> myqTable = null;
                    if (tablesContainer.getChildren().size() >= 8) {
                        resultTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(5);
                        myqTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(7);
                    }

                    List<String> warnings = new ArrayList<>();
                    Set<TablePosition> resultMismatches = new HashSet<>();
                    Set<TablePosition> myqMismatches = new HashSet<>();

                    // Provedení kontroly pro zjištění neshod
                    if (resultTable != null && myqTable != null) {
                        for (int i = 0; i < resultTable.getItems().size(); i++) {
                            ObservableList<String> resultRow = resultTable.getItems().get(i);
                            if (resultRow.size() > 2) {
                                String keyResult = resultRow.get(2);

                                for (int j = 0; j < myqTable.getItems().size(); j++) {
                                    ObservableList<String> myqRow = myqTable.getItems().get(j);
                                    if (myqRow.size() > 2) {
                                        String keyMyq = myqRow.get(2);

                                        if (keyResult.equals(keyMyq)) {
                                            if (resultRow.size() > 8 && myqRow.size() > 6) {
                                                String resultValue1 = resultRow.get(8);
                                                String myqValue1 = myqRow.get(6);

                                                if (!Objects.equals(resultValue1, myqValue1)) {
                                                    warnings.add("Mismatch in column 9/7 for key " + keyResult + ": " + resultValue1 + " != " + myqValue1);
                                                    resultMismatches.add(new TablePosition<>(resultTable, i, resultTable.getColumns().get(8)));
                                                    myqMismatches.add(new TablePosition<>(myqTable, j, myqTable.getColumns().get(6)));
                                                }
                                            }

                                            if (resultRow.size() > 9 && myqRow.size() > 7) {
                                                String resultValue2 = resultRow.get(9);
                                                String myqValue2 = myqRow.get(7);

                                                if (!Objects.equals(resultValue2, myqValue2)) {
                                                    warnings.add("Mismatch in column 10/8 for key " + keyResult + ": " + resultValue2 + " != " + myqValue2);
                                                    resultMismatches.add(new TablePosition<>(resultTable, i, resultTable.getColumns().get(9)));
                                                    myqMismatches.add(new TablePosition<>(myqTable, j, myqTable.getColumns().get(7)));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    logger.info("Result Mismatches: " + resultMismatches);
                    logger.info("MyQ Mismatches: " + myqMismatches);

                    int totalElements = tablesContainer.getChildren().size();
                    int totalWork = 0;

                    for (Node node : tablesContainer.getChildren()) {
                        if (node instanceof Label) {
                            totalWork++;
                        } else if (node instanceof TableView) {
                            totalWork += ((TableView<?>) node).getItems().size();
                        }
                    }

                    int completedWork = 0;
                    boolean firstTable = true;
                    boolean firstLabel = true;

                    for (int i = 0; i < totalElements; i++) {
                        if (isCancelled()) {
                            document.close();
                            Platform.runLater(() -> {
                                Alert alert = new Alert(AlertType.WARNING);
                                alert.setTitle("Ukladani zruseno");
                                alert.setHeaderText(null);
                                alert.setContentText("Ukladani do PDF bylo zruseno.");
                                alert.showAndWait();
                            });
                            return null;
                        }

                        Node node = tablesContainer.getChildren().get(i);
                        if (node instanceof Label) {
                            if (!firstLabel) {
                                document.newPage(); // Vytvoří novou stránku pro každý label po prvním
                            }
                            firstLabel = false;

                            Label label = (Label) node;
                            document.add(new Paragraph(label.getText(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                            document.add(new Paragraph("\n"));

                            completedWork++;
                            this.updateProgress((double) completedWork / totalWork);
                        }
                        if (node instanceof TableView) {
                            if (!firstTable) {
                                // Tato podmínka by se nyní neměla spouštět, protože novou stránku by měl zajistit label
                                // Ale pro jistotu zde necháváme, pokud by struktura byla neočekávaná
                                // document.newPage();
                            }
                            firstTable = false;

                            TableView<ObservableList<String>> currentTableView = (TableView<ObservableList<String>>) node;
                            PdfPTable pdfTable = new PdfPTable(currentTableView.getColumns().size());
                            pdfTable.setWidthPercentage(100);

                            // Přidání hlaviček
                            for (TableColumn<ObservableList<String>, ?> column : currentTableView.getColumns()) {
                                PdfPCell headerCell = new PdfPCell(new Phrase(column.getText(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE)));
                                headerCell.setBackgroundColor(new BaseColor(242, 79, 19));
                                pdfTable.addCell(headerCell);
                            }

                            // Přidání datových řádků
                            int totalRows = currentTableView.getItems().size();
                            for (int rowIndex = 0; rowIndex < totalRows; rowIndex++) {
                                ObservableList<String> row = currentTableView.getItems().get(rowIndex);
                                for (int columnIndex = 0; columnIndex < currentTableView.getColumns().size(); columnIndex++) {
                                    String cellValue = row.get(columnIndex);
                                    PdfPCell pdfCell = new PdfPCell(new Phrase(cellValue, FontFactory.getFont(FontFactory.HELVETICA, 8)));

                                    if (currentTableView == resultTable) {
                                        TablePosition pos = new TablePosition(resultTable, rowIndex, resultTable.getColumns().get(columnIndex));
                                        logger.debug("Kontroluji shodu v Result Table na pozici: " + pos);
                                        if (resultMismatches.contains(pos)) {
                                            logger.debug("Neshoda nalezena v Result Table na pozici: " + pos);
                                            pdfCell.setBackgroundColor(new BaseColor(255, 0, 0)); // Změna barvy pro testování
                                        }
                                    } else if (currentTableView == myqTable) {
                                        TablePosition pos = new TablePosition(myqTable, rowIndex, myqTable.getColumns().get(columnIndex));
                                        logger.debug("Kontroluji shodu v MyQ Table na pozici: " + pos);
                                        if (myqMismatches.contains(pos)) {
                                            logger.debug("Neshoda nalezena v MyQ Table na pozici: " + pos);
                                            pdfCell.setBackgroundColor(new BaseColor(255, 0, 0)); // Změna barvy pro testování
                                        }
                                    }
                                    pdfTable.addCell(pdfCell);
                                }
                                completedWork++;
                                this.updateProgress((double) completedWork / totalWork);
                            }
                            document.add(pdfTable);
                            // document.add(new Paragraph("\n"));
                        }
                    }
                    document.close();

                    Platform.runLater(() -> logger.info("PDF file saved successfully: " + file.getAbsolutePath()));
                    return null;
                }

                private void updateProgress(double progress) {
                }
            };

            saveTask.setOnSucceeded(event -> {
                progressBar.setVisible(false);
                progressBar.progressProperty().unbind();
                progressBar.setProgress(0);
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle("PDF Uloženo");
                alert.setHeaderText(null);
                alert.setContentText("PDF soubor byl uspesně ulozen: " + file.getAbsolutePath());
                try {
                    alert.show();
                } catch (Exception e) {
                    logger.error("Chyba pri zobrazovani alert okna: " + e.getMessage());
                }
            });

            saveTask.setOnFailed(event -> {
                progressBar.setVisible(false);
                progressBar.progressProperty().unbind();
                progressBar.setProgress(0);
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Chyba při ukládání");
                alert.setHeaderText(null);
                alert.setContentText("Při ukládání PDF souboru došlo k chybě: " + event.getSource().getException().getMessage());
                alert.showAndWait();
                event.getSource().getException().printStackTrace();
            });

            progressBar.progressProperty().bind(saveTask.progressProperty());

            Thread thread = new Thread(saveTask);
            thread.setDaemon(true);
            thread.start();
        }
    }


}
