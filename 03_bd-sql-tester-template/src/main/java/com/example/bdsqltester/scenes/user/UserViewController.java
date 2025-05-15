package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.MainDataSource;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;

public class UserViewController implements Initializable {

    @FXML
    private ListView<Assignment> assignmentList;

    @FXML
    private TextField idField;

    @FXML
    private TextField nameField;

    @FXML
    private TextArea instructionsField;

    @FXML
    private TextArea answerField;

    @FXML
    private Label gradeLabel;

    @FXML
    private TextArea queryResultArea;

    private ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private Assignment selectedAssignment;
    private long loggedInUserId;

    public UserViewController(long userId) {
        this.loggedInUserId = userId;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        loadAssignments();
        assignmentList.setItems(assignments);
        assignmentList.setOnMouseClicked(this::handleAssignmentSelection);
        gradeLabel.setText("");
        queryResultArea.setText("");
    }

    private void loadAssignments() {
        try (Connection connection = MainDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name AS title, instructions, answer_key FROM assignments")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                assignments.add(new Assignment(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        resultSet.getString("instructions"),
                        resultSet.getString("answer_key")
                ));
            }
            if (assignments.isEmpty()) {
                showAlert("Info", "Tidak ada daftar assignment yang tersedia.", Alert.AlertType.INFORMATION);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Gagal memuat daftar assignment: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleAssignmentSelection(MouseEvent event) {
        selectedAssignment = assignmentList.getSelectionModel().getSelectedItem();
        if (selectedAssignment != null) {
            idField.setText(String.valueOf(selectedAssignment.getId()));
            nameField.setText(selectedAssignment.getTitle());
            instructionsField.setText(selectedAssignment.getInstructions());
            answerField.clear();
            loadUserGrade(selectedAssignment.getId());
            queryResultArea.clear();
        }
    }

    @FXML
    void onTestClick(javafx.event.ActionEvent event) {
        String userAnswer = answerField.getText();
        if (selectedAssignment != null) {
            try (Connection testConnection = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/oracle_hr", // Ganti dengan URL database tes Anda
                    "postgres", // Ganti dengan username database tes Anda
                    "postgres" // Ganti dengan password database tes Anda
            );
                 Statement stmt = testConnection.createStatement();
                 ResultSet rs = stmt.executeQuery(userAnswer)) {

                StringBuilder resultBuilder = new StringBuilder();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                for (int i = 1; i <= columnCount; i++) {
                    resultBuilder.append(metaData.getColumnName(i)).append("\t");
                }
                resultBuilder.append("\n");

                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        resultBuilder.append(rs.getString(i)).append("\t");
                    }
                    resultBuilder.append("\n");
                }

                if (resultBuilder.length() > 0) {
                    queryResultArea.setText(resultBuilder.toString());
                } else {
                    queryResultArea.setText("Tidak ada hasil.");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                queryResultArea.setText("Terjadi kesalahan saat menjalankan query: " + e.getMessage());
            }
        } else {
            showAlert("Peringatan", "Pilih assignment terlebih dahulu.", Alert.AlertType.WARNING);
        }
    }


    @FXML
    void onSubmitClick(javafx.event.ActionEvent event) {
        String userAnswer = answerField.getText();
        if (selectedAssignment != null) {
            double grade = evaluateAnswer(selectedAssignment, userAnswer);
            saveGradeToDatabase(selectedAssignment.getId(), grade);
            showAlert("Hasil", "Nilai Anda: " + String.format("%.2f", grade), Alert.AlertType.INFORMATION);
        } else {
            showAlert("Peringatan", "Pilih assignment terlebih dahulu.", Alert.AlertType.WARNING);
        }
    }

    private double evaluateAnswer(Assignment assignment, String userAnswer) {
        String correctAnswer = assignment.getAnswerKey().trim().replaceAll("\\s+", " ").toLowerCase();
        String submittedAnswer = userAnswer.trim().replaceAll("\\s+", " ").toLowerCase();
        double grade = 0;

        List<String> correctWords = Arrays.asList(correctAnswer.split("\\s+"));
        List<String> submittedWords = Arrays.asList(submittedAnswer.split("\\s+"));

        HashSet<String> correctWordSet = new HashSet<>(correctWords);
        HashSet<String> submittedWordSet = new HashSet<>(submittedWords);

        if (submittedWordSet.containsAll(correctWordSet)) {
            if (submittedAnswer.equals(correctAnswer)) {
                grade = 100;
            } else {
                grade = 50;
            }
        } else {
            grade = 50;
        }

        return grade;
    }

    private void saveGradeToDatabase(long assignmentId, double grade) {
        try (Connection connection = MainDataSource.getConnection()) {
            String selectQuery = "SELECT grade FROM grades WHERE assignment_id = ? AND user_id = ?";
            PreparedStatement selectStmt = connection.prepareStatement(selectQuery);
            selectStmt.setLong(1, assignmentId);
            selectStmt.setLong(2, loggedInUserId);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                double existingGrade = rs.getDouble("grade");
                if (grade > existingGrade) {
                    String updateQuery = "UPDATE grades SET grade = ? WHERE assignment_id = ? AND user_id = ?";
                    PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
                    updateStmt.setDouble(1, grade);
                    updateStmt.setLong(2, assignmentId);
                    updateStmt.setLong(3, loggedInUserId);
                    updateStmt.executeUpdate();
                }
            } else {
                String insertQuery = "INSERT INTO grades (assignment_id, user_id, grade) VALUES (?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setLong(1, assignmentId);
                insertStmt.setLong(2, loggedInUserId);
                insertStmt.setDouble(3, grade);
                insertStmt.executeUpdate();
            }
            loadUserGrade(assignmentId);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Gagal menyimpan nilai: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadUserGrade(long assignmentId) {
        try (Connection connection = MainDataSource.getConnection()) {
            String query = "SELECT grade FROM grades WHERE assignment_id = ? AND user_id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setLong(1, assignmentId);
            statement.setLong(2, loggedInUserId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                gradeLabel.setText("Nilai Anda: " + resultSet.getDouble("grade"));
            } else {
                gradeLabel.setText("Nilai Anda: Belum ada");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            gradeLabel.setText("Gagal memuat nilai");
        }
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
        private String title;
        private String instructions;
        private String answerKey;

        public Assignment(long id, String title, String instructions, String answerKey) {
            this.id = id;
            this.title = title;
            this.instructions = instructions;
            this.answerKey = answerKey;
        }

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getInstructions() {
            return instructions;
        }

        public String getAnswerKey() {
            return answerKey;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}