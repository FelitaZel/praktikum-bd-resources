package com.example.bdsqltester.scenes;

import com.example.bdsqltester.HelloApplication;
import com.example.bdsqltester.datasources.MainDataSource;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class LoginController {

    @FXML
    private TextField passwordField;

    @FXML
    private ChoiceBox<String> selectRole;

    @FXML
    private TextField usernameField;

    @FXML
    void initialize() {
        selectRole.getItems().addAll("Admin", "User");
        selectRole.setValue("User");
    }

    @FXML
    void onLoginClick(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = selectRole.getValue();

        try {
            try (Connection c = MainDataSource.getConnection()) {
                PreparedStatement stmt = c.prepareStatement("SELECT id, password FROM users WHERE username = ? AND role = ?");
                stmt.setString(1, username);
                stmt.setString(2, role.toLowerCase());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String dbPassword = rs.getString("password");
                    if (dbPassword.equals(password)) {
                        Long currentUserId = rs.getLong("id");

                        HelloApplication app = HelloApplication.getApplicationInstance();
                        Stage primaryStage = app.getPrimaryStage();

                        if (role.equals("Admin")) {
                            primaryStage.setTitle("Admin View");
                            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("admin-view.fxml"));
                            Scene scene = new Scene(loader.load());
                            primaryStage.setScene(scene);
                        } else {
                            primaryStage.setTitle("User View");
                            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("user-view.fxml"));
                            loader.setControllerFactory(param -> new com.example.bdsqltester.scenes.user.UserViewController(currentUserId));
                            Scene scene = new Scene(loader.load());
                            primaryStage.setScene(scene);
                        }
                    } else {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Login Failed");
                        alert.setHeaderText("Invalid Credentials");
                        alert.setContentText("Please check your username and password.");
                        alert.showAndWait();
                    }
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Login Failed");
                    alert.setHeaderText("Invalid Credentials");
                    alert.setContentText("Please check your username and password.");
                    alert.showAndWait();
                }
            }
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Database Connection Failed");
            alert.setContentText("Could not connect to the database. Please try again later.");
            alert.showAndWait();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}