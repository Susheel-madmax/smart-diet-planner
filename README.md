# 🥗 Smart Diet Planner
### Java Servlets + Oracle SQL + HTML/CSS/JS on Apache Tomcat

---

## 📁 Project Structure

```
SmartDietPlanner/
├── pom.xml
├── sql/
│   └── schema.sql                        ← Run this first in Oracle SQL
└── src/main/
    ├── java/com/diet/
    │   ├── model/
    │   │   └── User.java                 ← User entity + BMR logic
    │   ├── servlet/
    │   │   ├── RegisterServlet.java      ← POST /RegisterServlet
    │   │   ├── LoginServlet.java         ← POST /LoginServlet
    │   │   ├── DashboardServlet.java     ← GET  /DashboardServlet (JSON)
    │   │   ├── LogProgressServlet.java   ← POST /LogProgressServlet
    │   │   └── LogoutServlet.java        ← GET  /LogoutServlet
    │   └── util/
    │       └── DBConnection.java         ← Oracle JDBC connection
    └── webapp/
        ├── WEB-INF/
        │   └── web.xml
        ├── css/
        │   └── style.css
        └── pages/
            ├── login.html
            ├── register.html
            └── dashboard.html
```

---

## ⚙️ Prerequisites

| Tool             | Version  | Download |
|------------------|----------|----------|
| Java JDK         | 17+      | https://adoptium.net |
| Apache Maven     | 3.8+     | https://maven.apache.org |
| Apache Tomcat    | 10.1+    | https://tomcat.apache.org |
| Oracle Database  | XE / 21c | https://oracle.com/database/technologies/xe-downloads.html |
| Oracle JDBC Jar  | ojdbc11  | https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html |
| VS Code          | Latest   | + Extension Pack for Java + Tomcat for Java |

---

## 🚀 Step-by-Step: Run on Apache Tomcat (VS Code)

### Step 1 — Install VS Code Extensions
Open VS Code → Extensions panel → Install:
- **Extension Pack for Java** (Microsoft)
- **Tomcat for Java** (Wei Shen)

---

### Step 2 — Set Up Oracle Database

1. Install Oracle XE and start the service
2. Open SQL*Plus or SQL Developer
3. Run the schema:

```sql
-- Connect as your user, then:
@/path/to/SmartDietPlanner/sql/schema.sql
```

---

### Step 3 — Configure DB Credentials

Open `src/main/java/com/diet/util/DBConnection.java` and edit:

```java
private static final String URL      = "jdbc:oracle:thin:@localhost:1521:XE";
private static final String USERNAME = "your_username";
private static final String PASSWORD = "your_password";
```

---

### Step 4 — Install Oracle JDBC Driver

Oracle JDBC is not on Maven Central. Download `ojdbc11.jar` from Oracle, then:

```bash
mvn install:install-file \
  -Dfile=/path/to/ojdbc11.jar \
  -DgroupId=com.oracle.database.jdbc \
  -DartifactId=ojdbc11 \
  -Dversion=21.9.0 \
  -Dpackaging=jar
```

---

### Step 5 — Build the WAR

```bash
cd SmartDietPlanner
mvn clean package
```

Output: `target/SmartDietPlanner.war`

---

### Step 6 — Deploy to Tomcat (VS Code)

1. Open VS Code → **TOMCAT SERVERS** panel (left sidebar)
2. Click `+` → Browse to your Tomcat 10.1 folder
3. Right-click your Tomcat server → **Add Deployment** → Select `SmartDietPlanner.war`
4. Right-click → **Start**
5. Open browser:

```
http://localhost:8080/SmartDietPlanner/pages/login.html
```

---

## 🧪 Quick Test Flow

1. Open `http://localhost:8080/SmartDietPlanner/pages/register.html`
2. Fill in your details — watch the **live BMR preview**
3. Submit → auto-redirected to login
4. Login → Dashboard loads with:
   - Your personalized meal plan
   - BMR-based calorie target
   - Weight chart, calorie doughnut chart
   - Streak counter & badges
   - Smart reminders
   - Embedded fitness videos

---

## 🔑 Key Features Explained

### BMR Formula (Mifflin-St Jeor)
```
BMR = 10×Weight + 6.25×Height − 5×Age + S
S = +5 (Male), −161 (Female)
```

### Calorie Targets
| Goal     | Adjustment      |
|----------|-----------------|
| Loss     | TDEE − 500 kcal |
| Gain     | TDEE + 300 kcal |
| Maintain | TDEE (×1.2)     |

### Adaptive Logic
Every time you log weight, `LogProgressServlet` checks your 7-day trend:
- Not losing on loss goal → reduces calories by 100
- Not gaining on gain goal → increases calories by 100
- New value saved to DB & session

### Streak System
- Logs each day → streak increments
- Miss a day → streak resets to 1
- Badges: 🥉 7 days, 🥈 14 days, 🥇 30 days

### Reminders
Pure JavaScript `setInterval` checks every minute against a schedule of water (every 2h) and meal times (8am, 1pm, 7:30pm). Browser Notification API used for desktop alerts.

---

## 🗄️ Database Tables

| Table      | Purpose                              |
|------------|--------------------------------------|
| `users`    | Account, BMR, target calories        |
| `diet_plan`| Daily breakfast / lunch / dinner     |
| `progress` | Daily weight & calorie logs          |
| `streaks`  | Streak counter + badge flags         |
| `meals`    | Meal library (reference data)        |

---

## 🔒 Production Notes

- **Hash passwords** with BCrypt before storing (replace plain-text comparison)
- Use **connection pooling** (HikariCP / DBCP) instead of `DriverManager`
- Add **CSRF tokens** to all POST forms
- Move credentials to environment variables or `context.xml`

---

## 🆘 Troubleshooting

| Problem | Fix |
|---|---|
| `ClassNotFoundException: oracle.jdbc.driver.OracleDriver` | ojdbc11.jar not in Maven local repo — re-run install:install-file |
| 404 on servlet URLs | Check `@WebServlet` annotations match the URL, ensure `web.xml` is correct |
| 401 on dashboard | Session expired — log in again |
| Charts blank | No progress data yet — log weight first |
| Tomcat won't start | Check port 8080 is free: `netstat -ano \| findstr 8080` |
