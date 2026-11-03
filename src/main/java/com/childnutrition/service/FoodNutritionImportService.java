package com.childnutrition.service;

import com.childnutrition.entity.FoodNutrition;
import com.childnutrition.repository.FoodNutritionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component
public class FoodNutritionImportService implements ApplicationRunner {
    private static final String SOURCE = "Indian_Food_Nutrition_Processed.csv";
    private final FoodNutritionRepository foods;
    public FoodNutritionImportService(FoodNutritionRepository foods) { this.foods = foods; }

    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        SourceData source = readSource();
        Map<String, List<String>> sourceRows = source.rows();
        Map<String, List<String>> extraRows = readSupplement();
        List<List<String>> catalogueRows = readCsv("data/food-catalogue.csv");
        if (catalogueRows.isEmpty()) throw new IllegalStateException("Curated food catalogue is empty");
        List<String> catalogueHeader = catalogueRows.remove(0);
        int nameIndex = catalogueHeader.indexOf("Dish Name"), categoryIndex = catalogueHeader.indexOf("Category"), mealTypeIndex = catalogueHeader.indexOf("Meal Type");
        if (nameIndex < 0 || categoryIndex < 0 || mealTypeIndex < 0) throw new IllegalStateException("Curated food catalogue has invalid columns");
        for (List<String> row : catalogueRows) {
            if (row.size() != catalogueHeader.size()) throw new IllegalStateException("Curated food catalogue contains a malformed row");
            if (row.get(nameIndex).isBlank() || row.get(categoryIndex).isBlank() || row.get(mealTypeIndex).isBlank())
                throw new IllegalStateException("Curated food catalogue contains a missing name, category or meal type");
            if (row.get(nameIndex).length() > 180 || row.get(categoryIndex).length() > 60 || row.get(mealTypeIndex).length() > 40)
                throw new IllegalStateException("Curated food catalogue contains a value longer than the database field limit");
        }

        foods.deactivateAll();
        Set<String> imported = new HashSet<>();
        for (List<String> curated : catalogueRows) {
            String name = curated.get(nameIndex).strip();
            String key = name.toLowerCase(Locale.ROOT);
            if (!imported.add(key)) throw new IllegalStateException("Duplicate food in curated catalogue: " + name);
            List<String> row = sourceRows.get(key);
            List<String> extra = extraRows.get(key);
            if (row == null && extra == null) throw new IllegalStateException("Curated food does not exist in nutrition datasets: " + name);
            FoodNutrition food = foods.findCatalogByName(name).orElseGet(FoodNutrition::new);
            food.setFoodName(name);
            if (extra != null) {
                List<String> eh = supplementHeader;
                food.setCalories(decimal(eh, extra, "Calories (kcal)")); food.setCarbohydrates(decimal(eh, extra, "Carbohydrates (g)"));
                food.setProtein(decimal(eh, extra, "Protein (g)")); food.setFat(decimal(eh, extra, "Fat (g)"));
                food.setSugar(optionalDecimal(eh, extra, "Sugar (g)")); food.setFiber(optionalDecimal(eh, extra, "Fiber (g)"));
                food.setSodium(optionalDecimal(eh, extra, "Sodium (mg)")); food.setSaturatedFat(optionalDecimal(eh, extra, "Saturated fat (g)"));
                applySupplementSource(food, key);
            } else {
            food.setCalories(decimal(source.header(), row, "Calories (kcal)"));
            food.setCarbohydrates(decimal(source.header(), row, "Carbohydrates (g)"));
            food.setProtein(decimal(source.header(), row, "Protein (g)"));
            food.setFat(decimal(source.header(), row, "Fats (g)"));
            food.setSugar(optionalDecimal(source.header(), row, "Free Sugar (g)"));
            food.setFiber(optionalDecimal(source.header(), row, "Fibre (g)"));
            food.setSodium(optionalDecimal(source.header(), row, "Sodium (mg)"));
            food.setCalcium(optionalDecimal(source.header(), row, "Calcium (mg)"));
            food.setIron(optionalDecimal(source.header(), row, "Iron (mg)"));
            food.setVitaminC(optionalDecimal(source.header(), row, "Vitamin C (mg)"));
            food.setFolate(optionalDecimal(source.header(), row, "Folate"));
            food.setSaturatedFat(null);
            food.setSourceName(SOURCE);
            food.setDataQuality("SOURCE_UNVERIFIED");
            food.setSourceReference("Imported dataset row; primary source is not provided in the source file.");
            food.setSourceRetrievedAt(null);
            }
            food.setCategory(curated.get(categoryIndex).strip());
            food.setDefaultMealType(curated.get(mealTypeIndex).strip());
            food.setServingSize(new BigDecimal("100")); food.setServingUnit("g"); food.setServingBasis("per 100 g"); food.setNutritionBasis("per 100 g");
            food.setActive(true);
            foods.save(food);
        }
    }

    private void applySupplementSource(FoodNutrition food, String key) {
        switch (key) {
            case "potato chips", "plain potato crisps" -> {
                food.setSourceName("USDA FoodData Central, SR Legacy"); food.setDataQuality("DATABASE_REFERENCE");
                food.setSourceReference("Generic salted potato chips, FDC 169677: https://fdc.nal.usda.gov/food-details/169677/nutrients"); food.setSourceRetrievedAt(LocalDate.of(2026, 10, 23));
            }
            case "milk chocolate" -> {
                food.setSourceName("USDA FoodData Central, SR Legacy"); food.setDataQuality("DATABASE_REFERENCE");
                food.setSourceReference("Candies, milk chocolate, FDC 167587: https://fdc.nal.usda.gov/food-details/167587/nutrients"); food.setSourceRetrievedAt(LocalDate.of(2026, 10, 23));
            }
            case "cream biscuits" -> {
                food.setSourceName("Philippine Food Composition Table, FNRI"); food.setDataQuality("DATABASE_REFERENCE");
                food.setSourceReference("Cream-filled sandwich cookie reference, FNRI report A100: https://i.fnri.dost.gov.ph/fct/library/report/3062"); food.setSourceRetrievedAt(LocalDate.of(2026, 10, 23));
            }
            case "cola soft drink" -> {
                food.setSourceName("USDA FoodData Central, FNDDS"); food.setDataQuality("DATABASE_REFERENCE");
                food.setSourceReference("Generic cola soft drink, FDC 2710561; values per 100 g: https://fdc.nal.usda.gov/food-details/2710561/nutrients"); food.setSourceRetrievedAt(LocalDate.of(2026, 10, 23));
            }
            default -> {
                food.setSourceName("food-supplement-per-100g.csv"); food.setDataQuality("ESTIMATED");
                food.setSourceReference("Generic estimate; not verified against a specific product label. Use the package label for branded foods.");
                food.setSourceRetrievedAt(null);
            }
        }
    }

    private List<String> supplementHeader;
    private Map<String, List<String>> readSupplement() throws Exception {
        List<List<String>> records = readCsv("data/food-supplement-per-100g.csv"); supplementHeader = records.remove(0);
        Map<String,List<String>> rows = new HashMap<>(); int ix = supplementHeader.indexOf("Food name");
        if (ix < 0) throw new IllegalStateException("Supplement nutrition dataset is missing its food name column");
        for (List<String> r : records) {
            if (r.size() != supplementHeader.size()) throw new IllegalStateException("Supplement nutrition dataset contains a malformed row");
            if (!r.get(ix).isBlank() && rows.putIfAbsent(r.get(ix).strip().toLowerCase(Locale.ROOT), r) != null)
                throw new IllegalStateException("Supplement nutrition dataset contains duplicate food names");
        }
        return rows;
    }

    private SourceData readSource() throws Exception {
        List<List<String>> records = readCsv("data/" + SOURCE);
        List<String> header = records.remove(0);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) columns.put(header.get(i).strip(), i);
        for (String required : List.of("Dish Name", "Calories (kcal)", "Carbohydrates (g)", "Protein (g)", "Fats (g)"))
            if (!columns.containsKey(required)) throw new IllegalStateException("Nutrition source CSV is missing column: " + required);
        Map<String, List<String>> rows = new HashMap<>();
        int name = columns.get("Dish Name");
        for (List<String> row : records) {
            if (row.size() != header.size()) throw new IllegalStateException("Nutrition source contains a malformed row");
            if (!row.get(name).isBlank() && rows.putIfAbsent(row.get(name).strip().toLowerCase(Locale.ROOT), row) != null)
                throw new IllegalStateException("Nutrition source contains duplicate food names");
        }
        return new SourceData(header, rows);
    }

    private static BigDecimal decimal(List<String> header, List<String> row, String column) {
        BigDecimal value = optionalDecimal(header, row, column);
        if (value == null) throw new IllegalStateException("Nutrition source has an empty value in " + column);
        return value;
    }
    private static BigDecimal optionalDecimal(List<String> header, List<String> row, String column) {
        int index = -1;
        for (int i = 0; i < header.size(); i++) if (header.get(i).strip().startsWith(column)) { index = i; break; }
        if (index < 0 || index >= row.size() || row.get(index).isBlank()) return null;
        BigDecimal value = new BigDecimal(row.get(index).strip());
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100000")) > 0)
            throw new IllegalStateException("Nutrition source contains an implausible value in " + column);
        return value;
    }

    private record SourceData(List<String> header, Map<String, List<String>> rows) { }

    private List<List<String>> readCsv(String path) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line; boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                first = false;
                if (!line.isBlank()) rows.add(parseCsv(line));
            }
        }
        return rows;
    }

    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; } else quoted = !quoted; }
            else if (c == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IllegalStateException("Nutrition CSV contains an unterminated quoted field");
        fields.add(field.toString()); return fields;
    }
}
