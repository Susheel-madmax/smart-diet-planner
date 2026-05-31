package com.diet.model;

public class User {
    private int    userId;
    private String name;
    private String email;
    private String password;
    private int    age;
    private double heightCm;
    private double weightKg;
    private String gender;   // "M" or "F"
    private String goal;     // "loss", "gain", "maintain"
    private double bmr;
    private double targetCal;

    // ── Constructors ─────────────────────────────────────────────────────────
    public User() {}

    public User(String name, String email, String password,
                int age, double heightCm, double weightKg,
                String gender, String goal) {
        this.name     = name;
        this.email    = email;
        this.password = password;
        this.age      = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.gender   = gender;
        this.goal     = goal;
        calculateBMR();
        calculateTargetCalories();
    }

    // ── BMR: Mifflin-St Jeor → BMR = 10W + 6.25H − 5A + S ──────────────────
    public void calculateBMR() {
        double s = gender.equalsIgnoreCase("M") ? 5 : -161;
        this.bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + s;
    }

    // ── Adjust for goal ──────────────────────────────────────────────────────
    public void calculateTargetCalories() {
        // Using sedentary TDEE multiplier (1.2) as baseline
        double tdee = bmr * 1.2;
        switch (goal.toLowerCase()) {
            case "loss":     this.targetCal = tdee - 500; break;
            case "gain":     this.targetCal = tdee + 300; break;
            default:         this.targetCal = tdee;       break;
        }
        // Safety floor
        this.targetCal = Math.max(1200, this.targetCal);
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public int    getUserId()    { return userId; }
    public void   setUserId(int userId) { this.userId = userId; }

    public String getName()      { return name; }
    public void   setName(String name) { this.name = name; }

    public String getEmail()     { return email; }
    public void   setEmail(String email) { this.email = email; }

    public String getPassword()  { return password; }
    public void   setPassword(String password) { this.password = password; }

    public int    getAge()       { return age; }
    public void   setAge(int age) { this.age = age; }

    public double getHeightCm()  { return heightCm; }
    public void   setHeightCm(double heightCm) { this.heightCm = heightCm; }

    public double getWeightKg()  { return weightKg; }
    public void   setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public String getGender()    { return gender; }
    public void   setGender(String gender) { this.gender = gender; }

    public String getGoal()      { return goal; }
    public void   setGoal(String goal) { this.goal = goal; }

    public double getBmr()       { return bmr; }
    public void   setBmr(double bmr) { this.bmr = bmr; }

    public double getTargetCal() { return targetCal; }
    public void   setTargetCal(double targetCal) { this.targetCal = targetCal; }
}
