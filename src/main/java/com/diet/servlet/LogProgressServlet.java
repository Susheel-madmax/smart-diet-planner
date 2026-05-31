package com.diet.servlet;

import com.diet.util.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/LogProgressServlet")
public class LogProgressServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            res.sendRedirect("pages/login.html");
            return;
        }

        int    userId  = (int) session.getAttribute("userId");
        double weight  = Double.parseDouble(req.getParameter("weight"));
        String calStr  = req.getParameter("calories_eaten");
        double calEaten = (calStr != null && !calStr.isEmpty()) ? Double.parseDouble(calStr) : 0;
        String notes   = req.getParameter("notes");
        if (notes == null) notes = "";

        try (Connection con = DBConnection.getConnection()) {

            // ── Insert progress log ───────────────────────────────────────────
            // Avoid duplicate log for same day
            String checkSql = "SELECT COUNT(*) FROM progress " +
                               "WHERE user_id = ? AND TRUNC(log_date) = TRUNC(SYSDATE)";
            try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                if (rs.getInt(1) > 0) {
                    // Update today's log instead
                    try (PreparedStatement upd = con.prepareStatement(
                            "UPDATE progress SET weight_kg=?, calories_eaten=?, notes=? " +
                            "WHERE user_id=? AND TRUNC(log_date)=TRUNC(SYSDATE)")) {
                        upd.setDouble(1, weight);
                        upd.setDouble(2, calEaten);
                        upd.setString(3, notes);
                        upd.setInt   (4, userId);
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = con.prepareStatement(
                            "INSERT INTO progress (user_id, weight_kg, calories_eaten, notes) " +
                            "VALUES (?,?,?,?)")) {
                        ins.setInt   (1, userId);
                        ins.setDouble(2, weight);
                        ins.setDouble(3, calEaten);
                        ins.setString(4, notes);
                        ins.executeUpdate();
                    }
                }
            }

            // ── Update session weight ─────────────────────────────────────────
            session.setAttribute("weight", weight);

            // ── Update streak ─────────────────────────────────────────────────
            updateStreak(con, userId);

            // ── Adaptive calorie adjustment (based on 7-day trend) ────────────
            adaptCalories(con, userId, session);

            res.sendRedirect("pages/dashboard.html?logged=true");

        } catch (SQLException | NumberFormatException e) {
            e.printStackTrace();
            res.sendRedirect("pages/dashboard.html?error=log_failed");
        }
    }

    private void updateStreak(Connection con, int userId) throws SQLException {
        String sql = "SELECT current_streak, longest_streak, last_log_date " +
                     "FROM streaks WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Date lastLog = rs.getDate("last_log_date");
                int  current = rs.getInt("current_streak");
                int  longest = rs.getInt("longest_streak");

                boolean isConsecutive = (lastLog != null) &&
                    (new java.util.Date().getTime() - lastLog.getTime() < 1000L * 60 * 60 * 48);

                if (isConsecutive) {
                    current++;
                } else {
                    current = 1; // reset
                }
                if (current > longest) longest = current;

                String badge7  = current >= 7  ? "Y" : "N";
                String badge14 = current >= 14 ? "Y" : "N";
                String badge30 = current >= 30 ? "Y" : "N";

                try (PreparedStatement upd = con.prepareStatement(
                        "UPDATE streaks SET current_streak=?, longest_streak=?, " +
                        "last_log_date=SYSDATE, badge_7=?, badge_14=?, badge_30=? " +
                        "WHERE user_id=?")) {
                    upd.setInt   (1, current);
                    upd.setInt   (2, longest);
                    upd.setString(3, badge7);
                    upd.setString(4, badge14);
                    upd.setString(5, badge30);
                    upd.setInt   (6, userId);
                    upd.executeUpdate();
                }
            }
        }
    }

    private void adaptCalories(Connection con, int userId, HttpSession session)
            throws SQLException {
        // Get average weight change over 7 days
        String sql = "SELECT MIN(weight_kg) min_w, MAX(weight_kg) max_w, COUNT(*) cnt " +
                     "FROM progress WHERE user_id=? AND log_date >= SYSDATE-7";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt("cnt") >= 3) {
                double change  = rs.getDouble("max_w") - rs.getDouble("min_w");
                String goal    = (String) session.getAttribute("goal");
                double current = (double) session.getAttribute("targetCal");
                double newCal  = current;

                if ("loss".equals(goal) && change < 0.2) {
                    newCal = current - 100; // reduce 100 kcal if not losing
                } else if ("gain".equals(goal) && change < 0.2) {
                    newCal = current + 100; // increase 100 kcal if not gaining
                }

                newCal = Math.max(1200, Math.min(4000, newCal));
                if (newCal != current) {
                    try (PreparedStatement upd = con.prepareStatement(
                            "UPDATE users SET target_cal=? WHERE user_id=?")) {
                        upd.setDouble(1, newCal);
                        upd.setInt   (2, userId);
                        upd.executeUpdate();
                    }
                    session.setAttribute("targetCal", newCal);
                }
            }
        }
    }
}
