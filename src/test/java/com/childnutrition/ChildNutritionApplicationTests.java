package com.childnutrition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.childnutrition.service.FoodNutritionImportService;

import java.util.UUID;
import java.util.List;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChildNutritionApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired FoodNutritionImportService foodImporter;

    @Test
    void publicFrontendResourcesLoadWithoutAuthentication() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/register.html")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/foods")).andExpect(status().isForbidden());
    }

    @BeforeEach
    void clearDatabase() {
        jdbc.update("delete from alerts");
        jdbc.update("delete from growth_records");
        jdbc.update("delete from meals");
        jdbc.update("delete from food_nutrition where custom_owner_id is not null");
        jdbc.update("delete from nutrition_targets");
        jdbc.update("delete from children");
        jdbc.update("delete from app_users");
    }

    @Test
    void contextLoadsAndFoodImportIsIdempotent() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(281, jdbc.queryForObject("select count(*) from food_nutrition where active = true", Integer.class));
        org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForList("select category from food_nutrition where active = true group by category", String.class).size() >= 10);
        foodImporter.run(null);
        org.junit.jupiter.api.Assertions.assertEquals(281, jdbc.queryForObject("select count(*) from food_nutrition where active = true", Integer.class));
    }

    @Test
    void parentsCanOnlyAccessTheirOwnChildren() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstEmail = "first-" + suffix + "@example.test";
        String firstToken = register(firstEmail);
        String secondToken = register("second-" + suffix + "@example.test");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + firstEmail + "\",\"password\":\"safe-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());

        String childJson = mvc.perform(post("/api/children").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha","dateOfBirth":"2021-06-01","gender":"female",
                                 "allergies":"peanuts","dietaryPreferences":"vegetarian"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long childId = objectMapper.readTree(childJson).get("id").asLong();

        mvc.perform(get("/api/children").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Asha"));
        mvc.perform(get("/api/children").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/children/{id}", childId).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNotFound());

        String today=LocalDate.now().toString();
        var food = jdbc.queryForMap("select id, calories, protein, carbohydrates, fat from food_nutrition where food_name = ?", "Hot tea (Garam Chai)");
        mvc.perform(get("/api/foods").param("query", "Garam Chai").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].foodName").value("Hot tea (Garam Chai)"))
                .andExpect(jsonPath("$[0].category").value("Beverages"))
                .andExpect(jsonPath("$[0].servingSize").value(100))
                .andExpect(jsonPath("$[0].servingBasis").value("per 100 g"));
        mvc.perform(get("/api/foods/search").param("q", "idli").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].foodName").value("Idli"));
        mvc.perform(get("/api/foods").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(281));
        mvc.perform(get("/api/foods/{id}", food.get("id")).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceName").value("Indian_Food_Nutrition_Processed.csv"));
        String mealJson = mvc.perform(post("/api/children/{id}/meals", childId).header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealDate":"%s","mealTime":"08:30:00","mealType":"BREAKFAST","foodId":%d,
                                 "quantity":100,"vitamins":"B1, B6","minerals":"Iron","calories":999}
                                """.formatted(today, ((Number)food.get("id")).longValue())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.calories").value(((Number)food.get("calories")).doubleValue()))
                .andExpect(jsonPath("$.protein").value(((Number)food.get("protein")).doubleValue()))
                .andExpect(jsonPath("$.carbohydrates").value(((Number)food.get("carbohydrates")).doubleValue()))
                .andExpect(jsonPath("$.fat").value(((Number)food.get("fat")).doubleValue()))
                .andReturn().getResponse().getContentAsString();
        long mealId = objectMapper.readTree(mealJson).get("id").asLong();
        mvc.perform(put("/api/children/{id}/meals/{mealId}", childId, mealId).header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealDate":"%s","mealTime":"08:30:00","mealType":"BREAKFAST","foodId":%d,"quantity":200,"vitamins":"B1, B6","minerals":"Iron"}
                                """.formatted(today, ((Number)food.get("id")).longValue())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calories").value(((Number)food.get("calories")).doubleValue() * 2))
                .andExpect(jsonPath("$.carbohydrates").value(((Number)food.get("carbohydrates")).doubleValue() * 2))
                .andExpect(jsonPath("$.fat").value(((Number)food.get("fat")).doubleValue() * 2));
        mvc.perform(post("/api/children/{id}/growth", childId).header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"%s","heightCm":100,"weightKg":15}
                                """.formatted(today)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.bmi").value(15.00));
        mvc.perform(get("/api/children/{id}/nutrition/daily", childId).header("Authorization", "Bearer " + firstToken)
                        .param("date", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calories").value(((Number)food.get("calories")).doubleValue() * 2))
                .andExpect(jsonPath("$.vitamins[0]").value("B1"));
        mvc.perform(post("/api/children/{id}/nutrition-targets", childId).header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageGroup":"4-8","calorieTarget":1000,"proteinTarget":20,"carbohydrateTarget":100,"fatTarget":30}
                                """))
                .andExpect(status().isCreated());
        String alerts=mvc.perform(get("/api/children/{id}/alerts", childId).header("Authorization", "Bearer " + firstToken)
                        .param("date", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        long alertId=objectMapper.readTree(alerts).get(0).get("id").asLong();
        mvc.perform(patch("/api/children/{id}/alerts/{alertId}/read", childId, alertId)
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/children/{id}/dashboard", childId).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.growth.latest.bmi").value(15.00))
                .andExpect(jsonPath("$.dailyNutrition.calories").value(((Number)food.get("calories")).doubleValue() * 2));
        mvc.perform(get("/api/children/{id}/reports/weekly", childId).header("Authorization", "Bearer " + firstToken)
                        .param("date", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[6].nutrition.calories").value(((Number)food.get("calories")).doubleValue() * 2));
        mvc.perform(get("/api/children/{id}/reports/monthly", childId).header("Authorization", "Bearer " + firstToken)
                        .param("month", today.substring(0, 7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").isNumber());
    }

    @Test
    void nutritiousAndSnackFoodsPersistAndFlowIntoAllNutritionViews() throws Exception {
        String token = register("catalog-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        String childJson = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mira\",\"dateOfBirth\":\"2021-01-01\",\"gender\":\"female\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long childId = objectMapper.readTree(childJson).get("id").asLong();
        String day = LocalDate.now().toString();
        List<String> names = List.of("Boiled rice (Uble chawal)", "Mixed dal", "Baked vegetables",
                "Potato chips", "Cream biscuits", "Milk chocolate", "Cola soft drink", "Hard candy");
        for (String name : names) {
            String searchJson = mvc.perform(get("/api/foods/search").param("q", name)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var food = objectMapper.readTree(searchJson).findValuesAsText("foodName").stream()
                    .filter(name::equals).findFirst().orElseThrow(() -> new AssertionError("Food missing from search: " + name));
            long foodId = jdbc.queryForObject("select id from food_nutrition where food_name = ? and active = true", Long.class, food);
            var values = jdbc.queryForMap("select calories, protein, carbohydrates, fat, free_sugar, fiber, sodium, saturated_fat from food_nutrition where id = ?", foodId);
            String response = mvc.perform(post("/api/children/{id}/meals", childId).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mealDate\":\"" + day + "\",\"mealType\":\"Snack\",\"foodId\":" + foodId + ",\"quantity\":50}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.foodName").value(name))
                    .andExpect(jsonPath("$.quantity").value(50))
                    .andExpect(jsonPath("$.calories").value(((Number) values.get("calories")).doubleValue() / 2))
                    .andExpect(jsonPath("$.protein").value(((Number) values.get("protein")).doubleValue() / 2))
                    .andExpect(jsonPath("$.carbohydrates").value(((Number) values.get("carbohydrates")).doubleValue() / 2))
                    .andExpect(jsonPath("$.fat").value(((Number) values.get("fat")).doubleValue() / 2))
                    .andReturn().getResponse().getContentAsString();
            long mealId = objectMapper.readTree(response).get("id").asLong();
            var mealNode = objectMapper.readTree(response);
            assertScaledNutrient(mealNode, "sugar", values.get("free_sugar"));
            assertScaledNutrient(mealNode, "fiber", values.get("fiber"));
            assertScaledNutrient(mealNode, "sodium", values.get("sodium"));
            assertScaledNutrient(mealNode, "saturatedFat", values.get("saturated_fat"));
            org.junit.jupiter.api.Assertions.assertNotNull(jdbc.queryForObject("select id from meals where id = ?", Long.class, mealId));
        }
        mvc.perform(get("/api/children/{id}/nutrition/daily", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mealCount").value(names.size()))
                .andExpect(jsonPath("$.sugar").isNumber()).andExpect(jsonPath("$.fiber").isNumber()).andExpect(jsonPath("$.sodium").isNumber());
        mvc.perform(get("/api/children/{id}/dashboard", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyNutrition.mealCount").value(names.size()));
        mvc.perform(get("/api/children/{id}/reports/weekly", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[6].nutrition.mealCount").value(names.size()))
                .andExpect(jsonPath("$.days[6].nutrition.sodium").isNumber())
                .andExpect(jsonPath("$.days[6].nutrition.sugar").isNumber())
                .andExpect(jsonPath("$.days[6].nutrition.fiber").isNumber())
                .andExpect(jsonPath("$.days[6].nutrition.saturatedFat").doesNotExist());
        mvc.perform(get("/api/foods/search").param("q", "Potato chips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dataQuality").value("DATABASE_REFERENCE"))
                .andExpect(jsonPath("$[0].sourceReference").value(org.hamcrest.Matchers.containsString("FDC 169677")))
                .andExpect(jsonPath("$[0].sourceRetrievedAt").value("2026-10-23"))
                .andExpect(jsonPath("$[0].saturatedFat").value(3.4));
        mvc.perform(get("/api/foods/search").param("q", "Instant noodles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dataQuality").value("ESTIMATED"))
                .andExpect(jsonPath("$[0].sourceRetrievedAt").doesNotExist())
                .andExpect(jsonPath("$[0].saturatedFat").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/children/{id}/nutrition-targets", childId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ageGroup\":\"4-8\",\"calorieTarget\":1500,\"proteinTarget\":30,\"carbohydrateTarget\":130,\"fatTarget\":50}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/children/{id}/alerts", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.alertType == 'SUGAR_ABOVE_GUIDANCE')]").isNotEmpty());
    }

    @Test
    void customFoodsValidatePersistAndScaleByLabelServingAndUnit() throws Exception {
        String token = register("custom-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        String otherToken = register("other-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        String customJson = """
                {"foodName":"Family smoothie test","servingSize":250,"servingUnit":"ml",
                 "calories":120,"protein":0,"carbohydrates":30,"fat":0,"saturatedFat":0,
                 "sugar":25,"sourceReference":"Test label"}
                """;
        String foodJson = mvc.perform(post("/api/foods/custom").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(customJson))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nutritionBasis").value("per 100 ml"))
                .andExpect(jsonPath("$.dataQuality").value("USER_PROVIDED"))
                .andExpect(jsonPath("$.sourceReference").value("Test label"))
                .andExpect(jsonPath("$.sourceRetrievedAt").isNotEmpty())
                .andExpect(jsonPath("$.calories").value(48.0)).andExpect(jsonPath("$.sodium").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long foodId = objectMapper.readTree(foodJson).get("id").asLong();
        foodImporter.run(null);
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("select active from food_nutrition where id = ?", Boolean.class, foodId) ? 1 : 0);
        mvc.perform(get("/api/foods/{id}", foodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(true));
        mvc.perform(post("/api/foods/custom").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(customJson.replace("\"sugar\":25", "\"sugar\":-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/foods/search").param("q", "Family smoothie test").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/foods/custom").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(customJson))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.foodName").value("Family smoothie test"));
        String childJson = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ari\",\"dateOfBirth\":\"2022-01-01\",\"gender\":\"male\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long childId = objectMapper.readTree(childJson).get("id").asLong(); String day = LocalDate.now().toString();
        mvc.perform(post("/api/children/{id}/meals", childId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealDate\":\"" + day + "\",\"mealType\":\"Snack\",\"foodId\":" + foodId + ",\"quantity\":125}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantityUnit").value("ml"))
                .andExpect(jsonPath("$.calories").value(60.0)).andExpect(jsonPath("$.protein").value(0.0))
                .andExpect(jsonPath("$.sugar").value(12.5)).andExpect(jsonPath("$.fiber").doesNotExist());
        mvc.perform(get("/api/children/{id}/nutrition/daily", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calories").value(60.0))
                .andExpect(jsonPath("$.sugar").value(12.5)).andExpect(jsonPath("$.fiber").doesNotExist())
                .andExpect(jsonPath("$.sodium").doesNotExist());
        mvc.perform(get("/api/children/{id}/reports/weekly", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[6].nutrition.calories").value(60.0))
                .andExpect(jsonPath("$.days[6].nutrition.sodium").doesNotExist());
        mvc.perform(post("/api/children/{id}/nutrition-targets", childId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ageGroup\":\"4-8\",\"calorieTarget\":1500,\"proteinTarget\":30,\"carbohydrateTarget\":130,\"fatTarget\":50}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/children/{id}/alerts", childId).param("date", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.alertType == 'SODIUM_ABOVE_GUIDANCE')]").isEmpty())
                .andExpect(jsonPath("$[?(@.alertType == 'SATURATED_FAT_ABOVE_GUIDANCE')]").isEmpty());
    }

    private String register(String email) throws Exception {
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Parent\",\"email\":\"" + email + "\",\"password\":\"safe-password-123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private void assertScaledNutrient(com.fasterxml.jackson.databind.JsonNode meal, String field, Object per100g) {
        var actual = meal.get(field);
        if (per100g == null) org.junit.jupiter.api.Assertions.assertTrue(actual == null || actual.isNull(), field);
        else org.junit.jupiter.api.Assertions.assertEquals(((Number) per100g).doubleValue() / 2,
                actual.asDouble(), 0.01, field + " should scale from 100 g to the 50 g serving");
    }
}



