package com.diet.servlet;

import com.diet.util.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String email = req.getParameter("email") != null ? req.getParameter("email").trim().toLowerCase() : "";
        String password = req.getParameter("password") != null ? req.getParameter("password") : "";

        if (email.isEmpty() || password.isEmpty()) {
            res.sendRedirect("pages/login.html?error=missing_fields");
            return;
        }

        // ── Fetch user + their LATEST logged weight (or registration weight as
        // fallback) ──
        String sql = "SELECT u.user_id, u.name, u.goal, u.target_cal, u.weight_kg, u.height_cm, u.age, u.bmr, " +
                "  (SELECT p.weight_kg FROM progress p " +
                "   WHERE p.user_id = u.user_id " +
                "   AND p.log_date = (SELECT MAX(p2.log_date) FROM progress p2 WHERE p2.user_id = u.user_id)" +
                "   AND ROWNUM = 1) AS latest_weight " +
                "FROM users u " +
                "WHERE u.email = ? AND u.password = ?";

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                HttpSession session = req.getSession(true);

                int userId = rs.getInt("user_id");
                String userName = rs.getString("name");
                String goal = rs.getString("goal");
                double targetCal = rs.getDouble("target_cal");
                double regWeight = rs.getDouble("weight_kg"); // weight at registration
                double latestW = rs.getDouble("latest_weight");// latest progress log weight

                // FIX: Use latest logged weight if available, else fall back to registration
                // weight
                double displayWeight = (latestW > 0) ? latestW : regWeight;

                session.setAttribute("userId", userId);
                session.setAttribute("userName", userName);
                session.setAttribute("goal", goal);
                session.setAttribute("targetCal", targetCal);
                session.setAttribute("weight", displayWeight);
                session.setAttribute("regWeight", regWeight); // keep original for reference
                session.setAttribute("heightCm", rs.getDouble("height_cm"));
                session.setAttribute("age", rs.getInt("age"));
                session.setAttribute("bmr", rs.getDouble("bmr"));
                session.setMaxInactiveInterval(60 * 60); // 1 hour

                res.sendRedirect("pages/dashboard.html");
            } else {
                res.sendRedirect("pages/login.html?error=invalid");
            }

        } catch (SQLException e) {
            System.err.println("[LoginServlet] SQLException: " + e.getMessage());
            e.printStackTrace();
            res.sendRedirect("pages/login.html?error=db_error");
        }
    }
}