package com.childnutrition.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** Relaxes legacy catalog nutrient constraints so unavailable values can remain NULL. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FoodNutritionSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public FoodNutritionSchemaMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String product; Set<String> indexes = new HashSet<>();
        try (var connection = jdbc.getDataSource().getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
            if (product != null && product.toLowerCase().contains("mysql")) {
                try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "food_nutrition", false, false)) {
                    while (rows.next()) if (rows.getString("INDEX_NAME") != null) indexes.add(rows.getString("INDEX_NAME"));
                }
            }
        }
        if (product != null && product.toLowerCase().contains("mysql")) {
            jdbc.execute("ALTER TABLE food_nutrition MODIFY calories DECIMAL(10,2) NULL, MODIFY carbohydrates DECIMAL(10,2) NULL, MODIFY protein DECIMAL(10,2) NULL, MODIFY fat DECIMAL(10,2) NULL");
            if (indexes.stream().anyMatch(name -> name.equalsIgnoreCase("uk_food_nutrition_name")))
                jdbc.execute("ALTER TABLE food_nutrition DROP INDEX uk_food_nutrition_name");
            if (indexes.stream().noneMatch(name -> name.equalsIgnoreCase("uk_food_name_owner")))
                jdbc.execute("ALTER TABLE food_nutrition ADD CONSTRAINT uk_food_name_owner UNIQUE (food_name, custom_owner_id)");
        }
    }
}
