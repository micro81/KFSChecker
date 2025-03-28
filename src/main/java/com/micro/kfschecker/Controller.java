package com.micro.kfschecker;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Date;

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

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                line = line.replaceAll("Ústav molekulární genetiky AV ČR, v.v.i.", "IMG"); //nahrada dlouheho nazvu instituce
                line = line.replaceAll("\"", ""); // odstraneni uvozovek
                String[] values = line.split(","); //oddelovaci znak je carka

                // Kontrola, zda řádek obsahuje alespoň jednu neprázdnou hodnotu
                boolean isEmptyRow = true;
                for (String value : values) {
                    if (!value.trim().isEmpty()) {
                        isEmptyRow = false;
                        break;
                    }
                }

                if (isHeader) {
                    for (int i = 0; i < values.length; i++) {
                        TableColumn<ObservableList<String>, String> column = new TableColumn<>(values[i]);
                        final int colIndex = i;
                        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(colIndex)));
                        tableView.getColumns().add(column);
                    }
                    isHeader = false;
                } else {
                    // Přidáme řádek do tabulky pouze pokud není prázdný
                    if (!isEmptyRow) {
                        tableView.getItems().add(FXCollections.observableArrayList(Arrays.asList(values)));
                    }
                }
            }
            System.out.println("OK");
        } catch (IOException e) {
            System.err.println("Error: Cannot load csv file");
            e.printStackTrace();
        }
        tablesContainer.getChildren().addAll(fileLabel, tableView); // Přidání názvu souboru a tabulky do VBoxu
        System.out.println("there are  oncreate " + tablesContainer.getChildren().size());
        saveToPDFButtton.setDisable(false); // Aktivace tlačítka Export to PDF
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
        if (tablesContainer.getChildren().size() < 6) {
            System.out.println("Error: Tables are missing.");
            return;
        }

        TableView<ObservableList<String>> resultTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(5);
        TableView<ObservableList<String>> myqTable = (TableView<ObservableList<String>>) tablesContainer.getChildren().get(7);

        List<String> warnings = new ArrayList<>(); // Seznam pro uchování varování

        for (ObservableList<String> resultRow : resultTable.getItems()) {
            String keyResult = resultRow.get(2); // Klíč ve 3. sloupci

            for (ObservableList<String> myqRow : myqTable.getItems()) {
                String keyMyq = myqRow.get(2); // Klíč ve 3. sloupci

                if (keyResult.equals(keyMyq)) {
                    String resultValue1 = resultRow.get(8); // 9. sloupec
                    String myqValue1 = myqRow.get(6); // 7. sloupec

                    String resultValue2 = resultRow.get(9); // 10. sloupec
                    String myqValue2 = myqRow.get(7); // 8. sloupec

                    if (!Objects.equals(resultValue1, myqValue1)) {
                        String warning = "Mismatch in column 9/7 for key " + keyResult + ": " + resultValue1 + " != " + myqValue1;
                        warnings.add(warning);
                        System.out.println(warning);
                    }

                    if (!Objects.equals(resultValue2, myqValue2)) {
                        String warning = "Mismatch in column 10/8 for key " + keyResult + ": " + resultValue2 + " != " + myqValue2;
                        warnings.add(warning);
                        System.out.println(warning);
                    }
                }
            }
        }

        // Deklarace proměnné alert
        Alert alert;

        // Pokud jsou varování, zobrazíme je v jednom okně
        if (!warnings.isEmpty()) {
            alert = new Alert(AlertType.WARNING);
            alert.setTitle("Mismatches Found");
            alert.setHeaderText(null);
            alert.setContentText(String.join("\n", warnings)); // Spojení všech varování do jednoho textu
            alert.showAndWait();
        } else {
            alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Check Completed");
            alert.setHeaderText(null);
            alert.setContentText("No mismatches found.");
            System.out.println("No mismatches found.");
            alert.showAndWait();
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
            URL imageUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
            System.out.println("IMG logo is in path: " + imageUrl);
            Image logo = Image.getInstance(imageUrl+"../../src/main/resources/com/micro/kfschecker/img/imglogo-basic-color-nobg-rgb.png");
            logo.scaleToFit(80, 31);
            PdfPCell logoCell = new PdfPCell(logo, false);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);

            //Vlozeni nadpisu do PDF
            com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new BaseColor(49, 143, 216)); // Barva textu
            PdfPCell textCell = new PdfPCell(new Phrase("KFS Checker", headerFont));
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            textCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            textCell.setBackgroundColor(new BaseColor(255, 255, 255)); // Barva pozadí

            headerTable.addCell(logoCell);
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

                    for (TableColumn<ObservableList<String>, ?> column : tableView.getColumns()) {
                        PdfPCell headerCell = new PdfPCell(new Phrase(column.getText(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE))); // Nastavení bílé barvy fontu
                        headerCell.setBackgroundColor(new BaseColor(242, 79, 19)); // Nastavení barvy záhlaví #f24f13
                        pdfTable.addCell(headerCell);
                    }

                    for (ObservableList<String> row : tableView.getItems()) {
                        for (String cell : row) {
                            pdfTable.addCell(new PdfPCell(new Phrase(cell, FontFactory.getFont(FontFactory.HELVETICA, 8))));
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
