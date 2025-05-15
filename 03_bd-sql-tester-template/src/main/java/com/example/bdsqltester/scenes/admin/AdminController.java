package com.example.bdsqltester.scenes.admin;

import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    @FXML
    private ListView<Assignment> assignmentList;

    @FXML
    private TextField idField;

    @FXML
    private TextField nameField;

    @FXML
    private TextArea instructionsField;

    @FXML
    private TextArea answerKeyField;

    @FXML
    private TableView<GradeEntry> gradesTableView;

    @FXML
    private TableColumn<GradeEntry, String> usernameColumn;

    @FXML
    private TableColumn<GradeEntry, Double> gradeColumn;

    private ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private ObservableList<GradeEntry> grades = FXCollections.observableArrayList();
    private Assignment selectedAssignment;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        loadAssignments();
        assignmentList.setItems(assignments);
        assignmentList.setOnMouseClicked(this::handleAssignmentSelection);

        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        gradesTableView.setItems(grades);

        clearAssignmentDetails();
    }

    private void loadAssignments() {
        try (Connection connection = MainDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, name, instructions, answer_key FROM assignments")) {
            assignments.clear();
            while (resultSet.next()) {
                assignments.add(new Assignment(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("instructions"),
                        resultSet.getString("answer_key")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Gagal memuat daftar tugas: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadGrades(long assignmentId) {
        grades.clear();
        try (Connection connection = MainDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u.username, g.grade FROM grades g JOIN users u ON g.user_id = u.id WHERE g.assignment_id = ?")) {
            statement.setLong(1, assignmentId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                grades.add(new GradeEntry(
                        resultSet.getString("username"),
                        resultSet.getDouble("grade")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Gagal memuat nilai: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleAssignmentSelection(MouseEvent event) {
        selectedAssignment = assignmentList.getSelectionModel().getSelectedItem();
        if (selectedAssignment != null) {
            idField.setText(String.valueOf(selectedAssignment.getId()));
            nameField.setText(selectedAssignment.getName());
            instructionsField.setText(selectedAssignment.getInstructions());
            answerKeyField.setText(selectedAssignment.getAnswerKey());
            loadGrades(selectedAssignment.getId());
        }
    }

    @FXML
    void onNewAssignmentClick(ActionEvent event) {
        clearAssignmentDetails();
        selectedAssignment = null;
    }

    @FXML
    void onSaveClick(ActionEvent event) {
        if (nameField.getText().isEmpty() || instructionsField.getText().isEmpty() || answerKeyField.getText().isEmpty()) {
            showAlert("Peringatan", "Semua kolom harus diisi.", Alert.AlertType.WARNING);
            return;
        }

        try (Connection connection = MainDataSource.getConnection()) {
            String sql;
            if (selectedAssignment != null) {
                sql = "UPDATE assignments SET name = ?, instructions = ?, answer_key = ? WHERE id = ?";
            } else {
                sql = "INSERT INTO assignments (name, instructions, answer_key) VALUES (?, ?, ?)";
            }
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, nameField.getText());
            statement.setString(2, instructionsField.getText());
            statement.setString(3, answerKeyField.getText());
            if (selectedAssignment != null) {
                statement.setLong(4, selectedAssignment.getId());
            }
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                loadAssignments();
                clearAssignmentDetails();
                showAlert("Sukses", "Data tugas berhasil disimpan.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Gagal menyimpan data tugas.", Alert.AlertType.ERROR);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Terjadi kesalahan saat menyimpan: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            selectedAssignment = null; // Reset setelah menyimpan
        }
    }

    @FXML
    void onDeleteAssignmentClick(ActionEvent event) {
        if (selectedAssignment == null) {
            showAlert("Peringatan", "Pilih tugas yang ingin dihapus.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Apakah Anda yakin ingin menghapus tugas ini?", ButtonType.YES, ButtonType.NO);
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try (Connection connection = MainDataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM assignments WHERE id = ?")) {
                    statement.setLong(1, selectedAssignment.getId());
                    int rowsAffected = statement.executeUpdate();
                    if (rowsAffected > 0) {
                        loadAssignments();
                        clearAssignmentDetails();
                        showAlert("Sukses", "Tugas berhasil dihapus.", Alert.AlertType.INFORMATION);
                    } else {
                        showAlert("Error", "Gagal menghapus tugas.", Alert.AlertType.ERROR);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert("Database Error", "Terjadi kesalahan saat menghapus: " + e.getMessage(), Alert.AlertType.ERROR);
                } finally {
                    selectedAssignment = null; // Reset setelah menghapus
                }
            }
        });
    }

    @FXML
    public void onTestButtonClick(ActionEvent event) {
        if (selectedAssignment == null) {
            showAlert("Peringatan", "Pilih tugas terlebih dahulu untuk diuji.", Alert.AlertType.WARNING);
            return;
        }

        String testQuery = answerKeyField.getText();
        try (Connection testConnection = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/oracle_hr", // Ganti dengan URL koneksi Oracle HR Anda yang sebenarnya
                "postgres", // Ganti dengan username Oracle HR Anda
                "postgres" // Ganti dengan password Oracle HR Anda
        );
             Statement stmt = testConnection.createStatement();
             ResultSet rs = stmt.executeQuery(testQuery)) {

            TextArea resultTextArea = new TextArea();
            resultTextArea.setEditable(false);
            StringBuilder resultBuilder = new StringBuilder();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Tambahkan header kolom
            for (int i = 1; i <= columnCount; i++) {
                resultBuilder.append(metaData.getColumnName(i)).append("\t\t");
            }
            resultBuilder.append("\n");
            for (int i = 1; i <= columnCount; i++) {
                resultBuilder.append("----------\t\t");
            }
            resultBuilder.append("\n");

            // Tambahkan data baris
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    resultBuilder.append(rs.getString(i)).append("\t\t");
                }
                resultBuilder.append("\n");
            }

            resultTextArea.setText(resultBuilder.toString());

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Hasil Test");
            alert.setHeaderText("Output dari Query:");
            alert.getDialogPane().setContent(resultTextArea);
            alert.setResizable(true);
            alert.showAndWait();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Terjadi kesalahan saat menjalankan query: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    void onShowGradesClick(ActionEvent event) {
        if (selectedAssignment == null) {
            showAlert("Peringatan", "Pilih tugas untuk melihat nilainya.", Alert.AlertType.WARNING);
            return;
        }
        loadGrades(selectedAssignment.getId());
    }

    private void clearAssignmentDetails() {
        idField.clear();
        nameField.clear();
        instructionsField.clear();
        answerKeyField.clear();
        grades.clear();
    }

    private void showAlert(String title, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class Assignment {
        private long id;
        private String name;
        private String instructions;
        private String answerKey;

        public Assignment(long id, String name, String instructions, String answerKey) {
            this.id = id;
            this.name = name;
            this.instructions = instructions;
            this.answerKey = answerKey;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getInstructions() {
            return instructions;
        }

        public String getAnswerKey() {
            return answerKey;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class GradeEntry {
        private String username;
        private double grade;

        public GradeEntry(String username, double grade) {
            this.username = username;
            this.grade = grade;
        }

        public String getUsername() {
            return username;
        }

        public double getGrade() {
            return grade;
        }
    }
}