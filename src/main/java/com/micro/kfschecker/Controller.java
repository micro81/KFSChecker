package com.micro.kfschecker;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
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
    private TextArea logOutput;

    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String myqQuery;

    @FXML
    private void initialize() {
        compareButton.setDisable(true);
        loadDatabaseConfig();
        loadQuery();
        redirectSystemOutToTextArea();

        // Nastavení tlačítka "CHECK" jako neaktivního při spuštění
        checkButton.setDisable(true);
        // Nastavení tlačítka "LOAD MyQ DATA" jako neaktivního při spuštění
        loadMyQDataButton.setDisable(true);
        // Nastavení tlačítka "EXPORT TO PDF" jako neaktivního při spuštění
        saveToPDFButtton.setDisable(true);
    }

    private void redirectSystemOutToTextArea() {
        PrintStream printStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                logOutput.appendText(String.valueOf((char) b));
            }
        }, true);
        System.setOut(printStream);
        System.setErr(printStream);
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
        System.out.println("Loading csv file...");
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
            System.out.println("OK");

        } catch (IOException e) {
            System.err.println("Error: Cannot load csv file");
            e.printStackTrace();
        }
        tablesContainer.getChildren().addAll(fileLabel, tableView);
        System.out.println("there are  oncreate " + tablesContainer.getChildren().size());
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
        System.out.println("there are  oncreate " + tablesContainer.getChildren().size());
    }

    @FXML
    private void handleLoadMyQData() {
        if (tablesContainer.getChildren().size() < 4) {
            return;
        }

        System.out.println("Loading MyQ Data...");

        TableView<ObservableList<String>> firstTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(1);
        TableView<ObservableList<String>> secondTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(3);

        Label myqLabel = new Label("MyQ DATA");
        myqLabel.setFont(Font.font("Arial", 14));
        myqLabel.setTextFill(Color.web("#f24f13"));
        myqLabel.setStyle("-fx-font-weight: bold;");

        TableView<ObservableList<String>> myqTable = new TableView<>();

        try {
            // Attempt to load the Firebird JDBC driver
            Class.forName("org.firebirdsql.jdbc.FBDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("Error: Firebird JDBC driver not found. Please make sure the driver library is included in your classpath.");
            e.printStackTrace();
            return; // Exit the method early due to the missing driver
        }


        Connection conn = null;
        int attempts = 0;
        while (attempts < 3) {
            try {
                conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                System.out.println("Connection established successfully!");
                break;
            } catch (SQLException e) {
                attempts++;
                System.err.println("Connection attempt " + attempts + " failed. Retrying in 3 seconds...");
                if (attempts == 3) {
                    System.err.println("Error: Unable to connect to the database after 3 attempts.");
                    e.printStackTrace();
                    return;
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        try {
            for (int i = 0; i < firstTable.getItems().size(); i++) {
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

                System.out.println("Executing query for serial number: " + serialNumber);

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
            }
        } catch (SQLException e) {
            System.err.println("Error: Unable to execute query.");
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        tablesContainer.getChildren().addAll(myqLabel, myqTable);
        checkButton.setDisable(false); //aktivace tlacitka CHECK
        System.out.println("there are oncreate " + tablesContainer.getChildren().size());
    }


    @FXML
    private void handleCheck() {
        if (tablesContainer.getChildren().size() < 8) { // Upravená kontrola, index 7 znamená minimálně 8 prvků
            System.out.println("Error: Tables are missing.");
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
                                    System.out.println(warning);
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
                                    System.out.println(warning);
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
            System.out.println("No mismatches found.");
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
            saveTablesToPDF(file);
        }
    }

    private void saveTablesToPDF(File file) {
        try {
            Document document = new Document(new Rectangle(PageSize.A4.rotate().getWidth(), PageSize.A4.rotate().getHeight()), 20, 20, 30, 30); // Nastavení formátu na šířku a zmenšení okrajů
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            //Pridani hlavicky do PDF
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 3}); // Nastavení poměru šířky sloupců

            //Vlozeni loga do PDF
            InputStream imageStream = getClass().getResourceAsStream("/com/micro/kfschecker/img/imglogo-basic-color-nobg-rgb.png");
            if (imageStream != null) {
                try {
                    Image logo = Image.getInstance(toByteArray(imageStream)); // Používám vaši vlastní metodu pro jistotu
                    logo.scaleToFit(80, 31);
                    PdfPCell logoCell = new PdfPCell(logo, false);
                    logoCell.setBorder(Rectangle.NO_BORDER);
                    logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                    headerTable.addCell(logoCell); // Přesunuto sem
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle the case where the image could not be loaded
                } finally {
                    try {
                        imageStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                System.err.println("Could not load logo image from classpath: /com/micro/kfschecker/img/imglogo-basic-color-nobg-rgb.png");
                // Pokud se obrázek nepodaří načíst, logoCell se nevytvoří a nepřidá se do tabulky
            }

            //Vlozeni nadpisu do PDF
            com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new BaseColor(49, 143, 216)); // Barva textu
            PdfPCell textCell = new PdfPCell(new Phrase("KFS Checker", headerFont));
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            textCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            textCell.setBackgroundColor(new BaseColor(255, 255, 255)); // Barva pozadí

            headerTable.addCell(textCell);
            document.add(headerTable);
            document.add(new Paragraph("\n"));

            for (int i = 0; i < tablesContainer.getChildren().size(); i++) {
                if (tablesContainer.getChildren().get(i) instanceof Label) {
                    Label label = (Label) tablesContainer.getChildren().get(i);
                    document.add(new Paragraph(label.getText(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                    document.add(new Paragraph("\n")); // Přidání mezery mezi label a tabulkou
                }
                if (tablesContainer.getChildren().get(i) instanceof TableView) {
                    TableView<ObservableList<String>> tableView = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(i);
                    PdfPTable pdfTable = new PdfPTable(tableView.getColumns().size());
                    pdfTable.setWidthPercentage(100); // Nastavení tabulky na celou šířku stránky

                    // Přidání hlaviček
                    for (TableColumn<ObservableList<String>, ?> column : tableView.getColumns()) {
                        PdfPCell headerCell = new PdfPCell(new Phrase(column.getText(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE))); // Nastavení bílé barvy fontu
                        headerCell.setBackgroundColor(new BaseColor(242, 79, 19)); // Nastavení barvy záhlaví #f24f13
                        pdfTable.addCell(headerCell);
                    }

                    // Přidání datových řádků
                    for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {
                        ObservableList<String> row = tableView.getItems().get(rowIndex);
                        for (int columnIndex = 0; columnIndex < tableView.getColumns().size(); columnIndex++) {
                            String cellValue = row.get(columnIndex);
                            TableColumn<ObservableList<String>, ?> column = tableView.getColumns().get(columnIndex);

                            // Pokusíme se získat barvu pozadí buňky
                            javafx.scene.control.TableCell<ObservableList<String>, ?> tableCell = getTableCell(tableView, rowIndex, columnIndex);
                            javafx.scene.paint.Color fxBackgroundColor = null;
                            if (tableCell != null && tableCell.getBackground() != null) {
                                fxBackgroundColor = (javafx.scene.paint.Color) tableCell.getBackground().getFills().get(0).getFill();
                            }

                            PdfPCell pdfCell = new PdfPCell(new Phrase(cellValue, FontFactory.getFont(FontFactory.HELVETICA, 8)));

                            // Nastavení barvy pozadí PDF buňky, pokud je k dispozici
                            if (fxBackgroundColor != null) {
                                int red = (int) (fxBackgroundColor.getRed() * 255);
                                int green = (int) (fxBackgroundColor.getGreen() * 255);
                                int blue = (int) (fxBackgroundColor.getBlue() * 255);
                                pdfCell.setBackgroundColor(new BaseColor(red, green, blue));
                            }
                            pdfTable.addCell(pdfCell);
                        }
                    }
                    document.add(pdfTable);
                    document.add(new Paragraph("\n"));
                }
            }
            document.close();

            System.out.println("PDF file saved successfully: " + file.getAbsolutePath());
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("PDF Saved");
            alert.setHeaderText(null);
            alert.setContentText("PDF file saved successfully: " + file.getAbsolutePath());
            alert.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
