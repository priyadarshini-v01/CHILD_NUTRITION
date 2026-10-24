# Child Nutrition Tracking Platform

Spring Boot 3 and Java 17 backend using Maven, MySQL, JPA, Spring Security, and JWT.

## Run locally

Create the MySQL database named `child_nutrition_db`, then set `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` in the environment. `JWT_SECRET` must be a base64-encoded key containing at least 32 bytes. The application uses `jdbc:mysql://localhost:3306/child_nutrition_db` by default and applies schema updates with Hibernate.

```powershell
mvn clean compile
mvn test
mvn spring-boot:run
```

Tests use an isolated in-memory H2 database. Production credentials and signing keys are read from environment variables.

## API overview

Public endpoints: `POST /api/auth/register`, `POST /api/auth/login`. Send the returned JWT as `Authorization: Bearer <token>` to protected endpoints.

- Children: `GET|POST /api/children`, `GET|PUT|DELETE /api/children/{id}`
- Meals and daily nutrition: `/api/children/{id}/meals`, `/api/children/{id}/nutrition/daily`
- Growth and BMI history: `/api/children/{id}/growth`, `/api/children/{id}/growth/summary`
- Nutrition targets: `/api/children/{id}/nutrition-targets`
- Recommendations, alerts, dashboard: `/api/children/{id}/recommendations`, `/api/children/{id}/alerts`, `/api/children/{id}/dashboard`
- Reports: `/api/children/{id}/reports/weekly`, `/api/children/{id}/reports/monthly?month=YYYY-MM`

Parent accounts can access only their own children and related records. Registration creates `PARENT` accounts; `ADMIN` is a supported role for provisioned accounts.
