package com.diet.servlet;

import com.diet.model.User;
import com.diet.util.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // ── Read and sanitize form parameters ────────────────────────────────
        String name = req.getParameter("name") != null ? req.getParameter("name").trim() : "";
        String email = req.getParameter("email") != null ? req.getParameter("email").trim().toLowerCase() : "";
        String password = req.getParameter("password") != null ? req.getParameter("password") : "";
        String ageStr = req.getParameter("age");
        String heightStr = req.getParameter("height");
        String weightStr = req.getParameter("weight");
        String gender = req.getParameter("gender");
        String goal = req.getParameter("goal");

        // ── Basic null / empty guard ──────────────────────────────────────────
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()
                || ageStr == null || heightStr == null || weightStr == null
                || gender == null || goal == null) {
            res.sendRedirect("pages/register.html?error=missing_fields");
            return;
        }

        int age;
        double height, weight;
        try {
            age = Integer.parseInt(ageStr.trim());
            height = Double.parseDouble(heightStr.trim());
            weight = Double.parseDouble(weightStr.trim());
        } catch (NumberFormatException e) {
            res.sendRedirect("pages/register.html?error=invalid_input");
            return;
        }

        // ── Build User model (calculates BMR + target calories) ───────────────
        User user = new User(name, email, password, age, height, weight, gender, goal);

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false); // start transaction

            // ── STEP 1: Insert user row ───────────────────────────────────────
            // FIX: Use an OracleCallableStatement with RETURNING INTO to get the
            // generated PK reliably from a trigger-based sequence in Oracle 11g.
            // getGeneratedKeys() is unreliable with Oracle JDBC + triggers.
            String insertUser = "BEGIN " +
                    "  INSERT INTO users (name, email, password, age, height_cm, weight_kg, " +
                    "                     gender, goal, bmr, target_cal) " +
                    "  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "  RETURNING user_id INTO ?; " +
                    "END;";

            int newUserId = -1;
            try (CallableStatement cs = con.prepareCall(insertUser)) {
                cs.setString(1, name);
                cs.setString(2, email);
                cs.setString(3, password); // TODO: hash with BCrypt in production
                cs.setInt(4, age);
                cs.setDouble(5, height);
                cs.setDouble(6, weight);
                cs.setString(7, gender);
                cs.setString(8, goal);
                cs.setDouble(9, user.getBmr());
                cs.setDouble(10, user.getTargetCal());

                // Register OUT parameter to capture RETURNING INTO value
                cs.registerOutParameter(11, Types.NUMERIC);
                cs.execute();

                newUserId = cs.getInt(11);
            }

            if (newUserId <= 0) {
                throw new SQLException("Failed to retrieve new user_id after insert.");
            }

            // ── STEP 2: Insert default streak row for this user ───────────────
            String insertStreak = "INSERT INTO streaks (user_id, current_streak, longest_streak, " +
                    "                     badge_7, badge_14, badge_30) " +
                    "VALUES (?, 0, 0, 'N', 'N', 'N')";
            try (PreparedStatement ps2 = con.prepareStatement(insertStreak)) {
                ps2.setInt(1, newUserId);
                ps2.executeUpdate();
            }

            // ── STEP 3: Auto-generate the first diet plan ─────────────────────
            generateDietPlan(con, newUserId, goal, user.getTargetCal());

            // ── Commit all three operations together ──────────────────────────
            con.commit();

            res.sendRedirect("pages/login.html?registered=true");

        } catch (SQLException e) {
            // Roll back on any failure
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }

            // ── Detect duplicate email (ORA-00001: unique constraint violated) ─
            String msg = e.getMessage();
            if (msg != null && (msg.contains("ORA-00001") || msg.contains("UQ_USERS_EMAIL"))) {
                res.sendRedirect("pages/register.html?error=email_exists");
            } else {
                // Log the real error to Tomcat console for debugging
                System.err.println("[RegisterServlet] SQLException: " + e.getMessage());
                e.printStackTrace();
                res.sendRedirect("pages/register.html?error=db_error");
            }

        } finally {
            // Always close the connection back to the pool / DriverManager
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // ── generateDietPlan ─────────────────────────────────────────────────────
    /**
     * Picks one random meal per meal type (breakfast / lunch / dinner) whose
     * goal_type matches the user's goal, then inserts a diet_plan row.
     *
     * FIX: Replaced "FETCH FIRST 1 ROWS ONLY" (Oracle 12c+ syntax) with
     * the Oracle 11g compatible "WHERE ROWNUM = 1" inside a subquery,
     * so that ORDER BY DBMS_RANDOM.VALUE is applied BEFORE the row limit.
     */
    private void generateDietPlan(Connection con, int userId, String goal, double targetCal)
            throws SQLException {

        // Oracle 11g compatible random single-row fetch:
        // Inner query: full table filtered + randomly ordered
        // Outer query: ROWNUM = 1 picks the first (random) row
        String mealSql = "SELECT name, calories FROM (" +
                "  SELECT name, calories FROM meals " +
                "  WHERE meal_type = ? " +
                "  AND (goal_type = ? OR goal_type = 'all') " +
                "  ORDER BY DBMS_RANDOM.VALUE" +
                ") WHERE ROWNUM = 1";

        String[] types = { "breakfast", "lunch", "dinner" };
        String[] results = new String[3];
        double[] cals = new double[3];
        double totalCal = 0;

        for (int i = 0; i < types.length; i++) {
            try (PreparedStatement ps = con.prepareStatement(mealSql)) {
                ps.setString(1, types[i]);
                ps.setString(2, goal);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    results[i] = rs.getString("name");
                    cals[i] = rs.getDouble("calories");
                } else {
                    // Fallback: no meal found for this type+goal combination
                    results[i] = "Balanced " + types[i] + " meal";
                    cals[i] = targetCal / 3.0;
                }
                totalCal += cals[i];
            }
        }

        // Water goal: scale with calorie target
        int waterMl = (targetCal < 1800) ? 2000 : (targetCal < 2500) ? 2500 : 3000;

        String insertPlan = "INSERT INTO diet_plan (user_id, breakfast, lunch, dinner, total_cal, water_ml) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(insertPlan)) {
            ps.setInt(1, userId);
            ps.setString(2, results[0]);
            ps.setString(3, results[1]);
            ps.setString(4, results[2]);
            ps.setDouble(5, totalCal);
            ps.setInt(6, waterMl);
            ps.executeUpdate();
        }
    }
}