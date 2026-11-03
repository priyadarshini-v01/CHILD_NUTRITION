# Child Nutrition Tracking Platform

Spring Boot 3 and Java 17 backend using Maven, MySQL, JPA, Spring Security, and JWT.

## Run locally

Create the MySQL database named `child_nutrition_db`, then set `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` in the environment. `JWT_SECRET` must be a base64-encoded key containing at least 32 bytes. The application uses `jdbc:mysql://localhost:3309/child_nutrition_db` by default and applies schema updates with Hibernate. Set `DB_URL` to override the connection URL.

Broad growth input limits can be configured with `GROWTH_MIN_HEIGHT_CM`, `GROWTH_MAX_HEIGHT_CM`, `GROWTH_MIN_WEIGHT_KG`, and `GROWTH_MAX_WEIGHT_KG`. Daily sugar, sodium, and saturated-fat alert guidance can be configured with `NUTRITION_SUGAR_MAX_GRAMS`, `NUTRITION_SODIUM_MAX_MG`, and `NUTRITION_SATURATED_FAT_MAX_GRAMS`.

```powershell
mvn clean compile
mvn test
mvn spring-boot:run
```

Tests use an isolated in-memory H2 database. Production credentials and signing keys are read from environment variables.

## Optional Nutrition Assistant AI

The assistant works without an external model using the food catalogue and built-in education. To enable broader model answers, explicitly configure `NUTRITION_AI_PROVIDER=openai` and provide `OPENAI_API_KEY` as a backend environment secret. Optionally set `NUTRITION_AI_MODEL` (default `gpt-4.1-mini`), `NUTRITION_AI_WEB_SEARCH=true` to allow trusted-domain web retrieval, `NUTRITION_AI_TIMEOUT_SECONDS` (default 20), and `NUTRITION_AI_RATE_LIMIT_PER_MINUTE` (default 20). Web search is off by default. Search is restricted to WHO, ICMR-NIN, USDA FoodData Central, NHS, CDC, and government domains. Provider credentials are never returned to the frontend.

When enabled, the current question and up to ten recent chat turns are sent to the configured provider. A selected child's age is included only when the question clearly asks about that child; daily report totals are included only when a report explanation is requested. The child's name and date of birth are not sent. Do not include identifying details in chat messages. The model is for education, not diagnosis or treatment. Exact food amounts are grounded in matching app records or retrieved trusted web sources; unavailable quantities remain unverified. Provider failures are identified in the response and do not masquerade as successful AI answers.

## API overview

Public endpoints: `POST /api/auth/register`, `POST /api/auth/login`. Send the returned JWT as `Authorization: Bearer <token>` to protected endpoints.

- Children: `GET|POST /api/children`, `GET|PUT|DELETE /api/children/{id}`
- Meals and daily nutrition: `/api/children/{id}/meals`, `/api/children/{id}/nutrition/daily`
- Growth and BMI history: `/api/children/{id}/growth`, `/api/children/{id}/growth/summary`
- Nutrition targets: `/api/children/{id}/nutrition-targets`
- Recommendations, alerts, dashboard: `/api/children/{id}/recommendations`, `/api/children/{id}/alerts`, `/api/children/{id}/dashboard`
- Reports: `/api/children/{id}/reports/weekly`, `/api/children/{id}/reports/monthly?month=YYYY-MM`

Parent accounts can access only their own children and related records. Registration creates `PARENT` accounts; `ADMIN` is a supported role for provisioned accounts.
