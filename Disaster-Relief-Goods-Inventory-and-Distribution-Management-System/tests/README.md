# Test programs

These are plain `main` programs (no test framework needed). They write real rows, so run them **only against an empty
practice database**, never against the shared one.

| Program | What it checks | Needs |
|---|---|---|
| `FreshInstallTest` | empty database -> tables from `database/schema.sql` -> first Admin -> demo data -> sign in, change password, reset password, deactivate, "keep one Admin" rule | empty database |
| `BackendSmokeTest` | donations (submit/approve), households, releases (household and center), duplicate + stock blocking, audit trail | empty database that already has the tables |
| `ReportTest` | every report type, with and without filters (run after `FreshInstallTest`) | demo data |
| `ExportTest` | writes `/tmp/test_report.pdf` and `/tmp/test_report.xlsx` | nothing |
| `GUI/Flow`, `GUI/Flow2` | drive the real screens: sign in, add item, adjust stock, new disaster, register household, donate, release; then encoder-submits / official-approves | demo data + a display (or `xvfb-run`) |

Run (Linux/macOS shown; use `;` instead of `:` on Windows):

```
mvn -q compile
export DB_URL=jdbc:mysql://localhost:3306/practice_db DB_USER=root DB_PASSWORD=...
javac -d target/tests -cp target/classes:<path-to-mysql-connector-j.jar> tests/FreshInstallTest.java
java  -cp target/tests:target/classes:<path-to-mysql-connector-j.jar> FreshInstallTest
```

`GUI/Flow*.java` are in package `GUI` because they use the screens' package-private helpers; compile them with the same
class path and run `java ... GUI.Flow`.
