// LoginDialog.java
package org.dialogs.access;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.dialogs.AbstractDialog;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class LoginDialog extends AbstractDialog {

    private JTextField userNameField;
    private final Vertx vertx;

    public LoginDialog(JFrame parent, Vertx vertx) {
        super(parent, "Login");
        this.vertx = vertx;
        setupDialog();
    }

    private void setupDialog() {
        userNameField = new JTextField();
        addField("Username:", userNameField);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (e.getSource() == confirmButton) {
            String username = userNameField.getText();
            if (!username.isEmpty()) {
                vertx.eventBus().request("user.login", username, reply -> {
                    if (reply.succeeded()) {
                        JsonObject result = (JsonObject) reply.result().body();
                        String role = result.getString("role");

                        SwingUtilities.invokeLater(() -> {
                            if ("admin".equals(role)) {
                                JOptionPane.showMessageDialog(this, "Admin login successful");
                                // SwingUtilities.invokeLater(() -> new AdminView(vertx).display());
                            } else if ("user".equals(role)) {
                                JOptionPane.showMessageDialog(this, "User login successful");
                                // SwingUtilities.invokeLater(() -> new UserView(vertx).display());
                            }
                            dispose();
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Login failed: " + reply.cause().getMessage());
                        });
                    }
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Please enter a username");
                });
            }
        }
    }
}