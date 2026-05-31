package com.diet.servlet;

import com.diet.util.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

/**
 * Returns all dashboard data as JSON for the frontend fetch() call.
 * FIX: Weight now always shows — uses registration weight as fallback
 * when no progress logs exist yet (e.g. right after registration).
 */
@WebServlet("/DashboardServlet")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        String userName = (String) session.getAttribute("userName");
        String goal = (String) session.getAttribute("goal");
        double targetCal = (double) session.getAttribute("targetCal");

        // FIX: Always have a weight to show — pulled from session which
        // already has registration weight as fallback (set in LoginServlet)
        double sessionWeight = session.getAttribute("weight") != null
                ? (double) session.getAttribute("weight")
                : 0.0;

        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        PrintWriter out = res.getWriter();

        try (Connection con = DBConnection.getConnection()) {

            // ── Today's diet plan ─────────────────────────────────────────────
            String planSql = "SELECT breakfast, lunch, dinner, total_cal, water_ml " +
                    "FROM diet_plan WHERE user_id = ? " +
                    "AND TRUNC(plan_date) = TRUNC(SYSDATE) " +
                    "AND ROWNUM = 1";

            String breakfast = "Not generated yet";
            String lunch = "Not generated yet";
            String dinner = "Not generated yet";
            double planCal = 0;
            int waterMl = 2500;

            try (PreparedStatement ps = con.prepareStatement(planSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    breakfast = rs.getString("breakfast");
                    lunch = rs.getString("lunch");
                    dinner = rs.getString("dinner");
                    planCal = rs.getDouble("total_cal");
                    waterMl = rs.getInt("water_ml");
                }
            }

            // ── FIX: Get current weight ───────────────────────────────────────
            // Priority 1: Latest progress log weight
            // Priority 2: Registration weight from users table
            // This ensures weight is ALWAYS shown, even on day 1
            double currentWeight = 0;

            String latestWeightSql = "SELECT weight_kg FROM progress " +
                    "WHERE user_id = ? " +
                    "AND log_date = (SELECT MAX(log_date) FROM progress WHERE user_id = ?) " +
                    "AND ROWNUM = 1";
            try (PreparedStatement ps = con.prepareStatement(latestWeightSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    currentWeight = rs.getDouble("weight_kg");
                }
            }

            // Fallback to registration weight if no progress logged yet
            if (currentWeight <= 0) {
                String regWeightSql = "SELECT weight_kg FROM users WHERE user_id = ?";
                try (PreparedStatement ps = con.prepareStatement(regWeightSql)) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        currentWeight = rs.getDouble("weight_kg");
                    }
                }
            }

            // Update session with latest weight
            session.setAttribute("weight", currentWeight);

            // ── Last 7 days progress chart data ───────────────────────────────
            // FIX: If no progress exists, seed chart with registration weight
            // so the chart is never empty on a new account
            String progSql = "SELECT TO_CHAR(log_date, 'DD-Mon') AS day, weight_kg " +
                    "FROM progress WHERE user_id = ? " +
                    "AND log_date >= SYSDATE - 7 " +
                    "ORDER BY log_date ASC";

            StringBuilder labels = new StringBuilder();
            StringBuilder weights = new StringBuilder();

            try (PreparedStatement ps = con.prepareStatement(progSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    if (labels.length() > 0) {
                        labels.append(",");
                        weights.append(",");
                    }
                    labels.append("\"").append(rs.getString("day")).append("\"");
                    weights.append(rs.getDouble("weight_kg"));
                }
            }

            // If no progress data at all, show today with registration weight
            if (labels.length() == 0) {
                labels.append("\"Today\"");
                weights.append(currentWeight);
            }

            // ── Streak & badges ───────────────────────────────────────────────
            int streak = 0;
            int longest = 0;
            String badge7 = "N", badge14 = "N", badge30 = "N";

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT current_streak, longest_streak, badge_7, badge_14, badge_30 " +
                            "FROM streaks WHERE user_id = ?")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    streak = rs.getInt("current_streak");
                    longest = rs.getInt("longest_streak");
                    badge7 = rs.getString("badge_7");
                    badge14 = rs.getString("badge_14");
                    badge30 = rs.getString("badge_30");
                }
            }

            // ── Feedback message ──────────────────────────────────────────────
            String feedback = generateFeedback(con, userId, goal, currentWeight);

            // ── Build JSON response ───────────────────────────────────────────
            out.println("{");
            out.println("  \"userName\": \"" + esc(userName) + "\",");
            out.println("  \"goal\": \"" + esc(goal) + "\",");
            out.println("  \"targetCal\": " + targetCal + ",");
            out.println("  \"currentWeight\": " + currentWeight + ",");
            out.println("  \"breakfast\": \"" + esc(breakfast) + "\",");
            out.println("  \"lunch\": \"" + esc(lunch) + "\",");
            out.println("  \"dinner\": \"" + esc(dinner) + "\",");
            out.println("  \"planCal\": " + planCal + ",");
            out.println("  \"waterMl\": " + waterMl + ",");
            out.println("  \"labels\": [" + labels + "],");
            out.println("  \"weights\": [" + weights + "],");
            out.println("  \"streak\": " + streak + ",");
            out.println("  \"longest\": " + longest + ",");
            out.println("  \"badge7\": \"" + badge7 + "\",");
            out.println("  \"badge14\": \"" + badge14 + "\",");
            out.println("  \"badge30\": \"" + badge30 + "\",");
            out.println("  \"feedback\": \"" + esc(feedback) + "\"");
            out.println("}");

        } catch (SQLException e) {
            System.err.println("[DashboardServlet] SQLException: " + e.getMessage());
            e.printStackTrace();
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    // ── Feedback generator ────────────────────────────────────────────────────
    private String generateFeedback(Connection con, int userId, String goal, double currentWeight)
            throws SQLException {

        // Count how many progress logs exist
        int logCount = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM progress WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                logCount = rs.getInt(1);
        }

        // Not enough data yet — encourage first log
        if (logCount == 0) {
            return "Welcome! Log your weight daily to get personalized feedback and track your progress!";
        }
        if (logCount < 3) {
            return "Good start! Keep logging daily. Feedback improves after 3+ days of data.";
        }

        // Get first and latest weight from last 7 days
        double first = -1, last = -1;
        String sql = "SELECT weight_kg FROM progress WHERE user_id = ? " +
                "AND log_date >= SYSDATE - 7 ORDER BY log_date ASC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double w = rs.getDouble("weight_kg");
                if (first < 0)
                    first = w;
                last = w;
            }
        }

        if (first < 0)
            return "Log your weight daily to get personalized feedback!";

        double diff = last - first;
        if ("loss".equals(goal)) {
            if (diff < -0.5)
                return "Great progress! You are losing weight steadily. Keep it up!";
            if (diff < 0)
                return "Slow but steady. Try to reduce portion sizes slightly.";
            return "No loss yet. Review your meal portions and increase daily movement.";
        } else if ("gain".equals(goal)) {
            if (diff > 0.3)
                return "Excellent! You are gaining muscle mass well!";
            if (diff >= 0)
                return "Slight gain. Add a healthy snack between meals.";
            return "Weight dropped. Increase meal portions and ensure 8hrs sleep.";
        }
        if (Math.abs(diff) < 0.3)
            return "Perfect! You are maintaining your weight well!";
        return "Small fluctuations are normal. Stay consistent with your plan.";
    }

    // ── JSON string escape helper ─────────────────────────────────────────────
    private String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }
}