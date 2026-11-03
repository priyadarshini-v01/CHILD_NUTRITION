package com.childnutrition.service;

import com.childnutrition.dto.FoodDtos;
import com.childnutrition.dto.NutritionAssistantDtos;
import com.childnutrition.dto.ReportDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.Meal;
import com.childnutrition.repository.MealRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class NutritionAssistantService {
    private static final String DGI = "https://nin.res.in/dietaryguidelines/pdfjs/locale/DGI_2024.pdf";
    private static final String WHO = "https://www.who.int/publications/i/item/9789240081864";
    private static final String USDA = "https://fdc.nal.usda.gov/";
    private final FoodNutritionService foods;
    private final MealRepository meals;
    private final NutritionReportService reports;
    private final ObjectProvider<NutritionLanguageModel> languageModels;
    public NutritionAssistantService(FoodNutritionService foods, MealRepository meals, NutritionReportService reports, ObjectProvider<NutritionLanguageModel> languageModels) { this.foods=foods; this.meals=meals; this.reports=reports; this.languageModels=languageModels; }

    public NutritionAssistantDtos.Response answer(String question, String owner, Child child, List<NutritionAssistantDtos.Turn> history, LocalDate reportDate) {
        String q = question.trim(); String lower = q.toLowerCase(Locale.ROOT);
        List<NutritionAssistantDtos.Reference> refs = new ArrayList<>();
        List<FoodDtos.Response> results = List.of();
        String answer;
        ReportDtos.DailyNutrition dailyReport = child != null && lower.contains("report")
                ? reports.daily(child, reportDate == null ? LocalDate.now() : reportDate,
                    meals.findByChildIdAndMealDate(child.getId(), reportDate == null ? LocalDate.now() : reportDate), owner)
                : null;
        String note = "Values come from the application's food records; source and verification status are shown for each food. Missing values remain unknown.";
        if (lower.contains("missing") && (lower.contains("today") || lower.contains("entry") || lower.contains("food"))) {
            if (child == null) answer = "Choose a child to review today's recorded foods and see which nutrient values are missing.";
            else {
                List<Meal> rows = meals.findByChildIdAndMealDate(child.getId(), LocalDate.now());
                if (rows.isEmpty()) answer = "There are no foods recorded for today yet.";
                else {
                    Set<String> missing = new TreeSet<>();
                    for (Meal row : rows) {
                        var f = row.getFood();
                        if (f == null) { missing.add("nutrition details for " + row.getFoodName()); continue; }
                        if (f.getCalories()==null) missing.add("calories"); if (f.getProtein()==null) missing.add("protein");
                        if (f.getCarbohydrates()==null) missing.add("carbohydrates"); if (f.getFat()==null) missing.add("fat");
                        if (f.getFiber()==null) missing.add("fibre"); if (f.getCalcium()==null) missing.add("calcium"); if (f.getIron()==null) missing.add("iron");
                    }
                    answer = missing.isEmpty() ? "The foods recorded today have values for the nutrients this guide tracks. This does not mean the child's overall diet is complete." : "For today's " + rows.size() + " recorded food " + (rows.size()==1?"entry":"entries") + ", information is missing for: " + String.join(", ", missing) + ". The app does not treat unknown values as zero.";
                }
            }
        } else if (isNutrientExplanation(lower)) {
            answer = explainNutrient(lower); refs.add(ref("ICMR–NIN Dietary Guidelines for Indians 2024", DGI));
        } else if (lower.contains("breakfast") || lower.contains("ideas")) {
            results = foods.all(null, owner).stream().filter(f -> "Breakfast".equalsIgnoreCase(f.defaultMealType())).limit(8).toList();
            if (results.isEmpty()) answer = "I couldn't find breakfast foods in the current catalogue. Try the Food Guide or search a food name.";
            else answer = "Here are breakfast foods in the catalogue: " + results.stream().limit(6).map(FoodDtos.Response::foodName).reduce((a,b)->a+", "+b).orElse("") + ". Choose options that fit your child's age, allergies and family diet. For babies under 6 months, ask a health professional before introducing foods.";
            refs.add(ref("WHO complementary feeding guideline", WHO));
        } else if ((lower.contains("iron") || lower.contains("calcium")) && foodTerm(lower) == null) {
            boolean both = lower.contains("iron") && lower.contains("calcium");
            String nutrient = lower.contains("iron") ? "iron" : "calcium";
            List<FoodDtos.Response> catalog = foods.all(null, owner);
            results = catalog.stream().filter(f -> both ? f.iron()!=null && f.iron().signum()>0 && f.calcium()!=null && f.calcium().signum()>0
                            : "iron".equals(nutrient) ? f.iron()!=null && f.iron().signum()>0 : f.calcium()!=null && f.calcium().signum()>0)
                    .limit(8).toList();
            answer = results.isEmpty() ? "I couldn't verify foods with recorded " + (both ? "iron and calcium" : nutrient) + " values in the current catalogue." : "Foods with recorded non-zero " + (both ? "iron and calcium" : nutrient) + " values include: " + results.stream().map(FoodDtos.Response::foodName).reduce((a,b)->a+", "+b).orElse("") + ". Check each food's source and data status; values describe the listed food basis.";
            results.stream().map(NutritionAssistantService::referenceFor).forEach(refs::add);
        } else {
            String term = foodTerm(lower);
            if (term != null && term.length() >= 2) {
                results = foods.search(term, null, owner);
                if (results.isEmpty()) answer = "I couldn't verify a matching food in the current food database. Try another spelling or browse the Food Guide.";
                else {
                    FoodDtos.Response f = results.get(0); String nutrient = askedNutrient(lower);
                    if (nutrient != null) {
                        var value = nutrientValue(f, nutrient);
                        answer = value == null ? "The " + nutrient + " value for " + f.foodName() + " is unavailable in this record, so I can't provide a verified amount." : f.foodName() + " has " + value.stripTrailingZeros().toPlainString() + " " + unit(nutrient) + " of " + nutrient + " " + Objects.toString(f.nutritionBasis(), "per 100 g") + ". " + (f.servingSize()==null?"": "The listed serving is " + f.servingSize().stripTrailingZeros().toPlainString()+" "+Objects.toString(f.servingUnit(),"g")+".");
                    } else answer = f.foodName() + " is in the food database. Open its Food Guide details to see recorded nutrients, serving basis, source and verification status.";
                    refs.add(referenceFor(f));
                }
            } else if (lower.contains("report") || lower.contains("calorie") || lower.contains("protein") || lower.contains("carbohydrate") || lower.contains("fat")) {
                answer = dailyReport == null
                        ? "Calories measure food energy. Protein helps build and repair body tissues; carbohydrates are a common energy source; fats help provide energy and support normal body functions. Your report totals only foods logged in the app, and missing database values are not counted as zero. A single day is not a diagnosis."
                        : reportSummary(dailyReport);
            } else answer = "I can help look up foods and nutrients, explain nutrition terms, suggest foods from the catalogue, or review missing values in today's entries. Try asking about a food by name.";
        }
        if (refs.isEmpty() && !results.isEmpty()) results.stream().map(NutritionAssistantService::referenceFor).forEach(refs::add);
        List<String> suggestions = List.of("How much protein is in dal?", "Which foods have iron?", "What does fibre do?", "Which nutrients are missing from today's entries?");
        NutritionLanguageModel model = languageModels.getIfAvailable();
        if (model != null) {
            try {
                NutritionLanguageModel.ModelAnswer generated = model.answer(q, history, groundingContext(results, refs, child, lower, dailyReport));
                List<NutritionAssistantDtos.Reference> combined = new ArrayList<>(generated.references());
                for (NutritionAssistantDtos.Reference source : refs)
                    if (combined.stream().noneMatch(r -> Objects.equals(r.url(), source.url()))) combined.add(source);
                return new NutritionAssistantDtos.Response(generated.text(), results.stream().limit(8).toList(), combined,
                        suggestions, "General explanations may use model knowledge. Exact food values come from records and sources shown; preparation and serving sizes can change values.", "ai");
            } catch (NutritionAiUnavailableException ex) {
                boolean grounded = !results.isEmpty() || lower.contains("missing") || lower.contains("report") || isNutrientExplanation(lower);
                String fallback = grounded
                        ? "The AI service is temporarily unavailable. From the information I can verify here: " + answer
                        : "The AI service is temporarily unavailable, and I could not verify an answer in the food database. Please try again later or browse the Food Guide.";
                return new NutritionAssistantDtos.Response(fallback, results.stream().limit(8).toList(), refs,
                        suggestions, "AI answers are unavailable right now. Database values and sources, when shown, remain available.", "unavailable");
            }
        }
        return new NutritionAssistantDtos.Response(answer, results.stream().limit(8).toList(), refs, suggestions,
                "AI answers are not configured. This response uses the food database and built-in educational guidance.", "database");
    }

    private String groundingContext(List<FoodDtos.Response> matches, List<NutritionAssistantDtos.Reference> refs, Child child, String question, ReportDtos.DailyNutrition dailyReport) {
        StringBuilder context = new StringBuilder("Matching application food records; null means unknown, not zero:\n");
        for (FoodDtos.Response f : matches) context.append("Food: ").append(f.foodName()).append("; category: ").append(f.category())
                .append("; all nutrient values use basis: ").append(f.nutritionBasis()).append("; standard serving: ").append(f.servingSize()).append(' ').append(f.servingUnit()).append(" (").append(f.servingBasis()).append(')')
                .append("; calories kcal: ").append(f.calories()).append("; protein g: ").append(f.protein())
                .append("; carbohydrates g: ").append(f.carbohydrates()).append("; fat g: ").append(f.fat())
                .append("; fibre g: ").append(f.fiber()).append("; iron mg: ").append(f.iron()).append("; calcium mg: ").append(f.calcium())
                .append("; source: ").append(f.sourceName()).append("; source URL: ").append(f.sourceReference())
                .append("; verification status: ").append(f.dataQuality()).append('\n');
        context.append("References available to this answer:\n");
        refs.forEach(r -> context.append(r.name()).append(" — ").append(r.url()).append('\n'));
        if (dailyReport != null) {
            context.append("Selected child's actual daily food-log report for ").append(dailyReport.date()).append(": ").append(reportSummary(dailyReport)).append('\n');
            dailyReport.nutrients().stream().map(ReportDtos.NutrientReport::reference).filter(Objects::nonNull)
                    .forEach(r -> context.append("Reference for ").append(r.ageRange()).append(": ").append(r.name()).append(" ").append(r.version()).append(" ").append(r.url()).append('\n'));
        }
        if (child != null && (question.contains("my child") || question.contains("my kid") || question.contains("for my") || question.contains("child's") || question.contains("childs"))) {
            int age = java.time.Period.between(child.getDateOfBirth(), LocalDate.now()).getYears();
            context.append("Selected child context requested by the parent: age ").append(age).append(" years. No name or date of birth is included.\n");
        }
        return context.toString();
    }

    private String reportSummary(ReportDtos.DailyNutrition report) {
        String totals = "Recorded on " + report.date() + ": " + report.nutrients().stream()
                .map(n -> n.name() + " " + (n.actual() == null ? "unknown" : n.actual().stripTrailingZeros().toPlainString()) + " " + n.unit()
                        + (n.reference() == null ? " (reference unavailable)" : "; reference " + n.reference().amount() + " " + n.reference().unit())
                        + (n.possibleGap() ? " (possible gap in this food log)" : ""))
                .reduce((a,b) -> a + "; " + b).orElse("No nutrient totals are available.");
        return totals + (report.incomplete() ? ". Some totals are incomplete because food data is missing or uses an unsupported unit." : ".")
                + " " + report.whatThisMeans();
    }
    private static boolean isNutrientExplanation(String q) { return q.contains("what does") || q.contains("what is") || q.contains("why does") || q.contains("explain"); }
    private static String explainNutrient(String q) {
        if (q.contains("protein")) return "Protein helps the body grow and repair tissues. Dal, beans, peas, milk, eggs and nuts contain protein. Food records report it in grams (g).";
        if (q.contains("iron")) return "Iron helps the body make haemoglobin, which carries oxygen in blood. Lentils, beans, leafy greens and some fortified foods contain iron. Food records report it in milligrams (mg).";
        if (q.contains("calcium")) return "Calcium supports bones and teeth. Milk, curd, some leafy greens and calcium-set foods can provide it. Food records report it in milligrams (mg).";
        if (q.contains("fibre") || q.contains("fiber")) return "Fibre is part of plant foods and supports regular digestion. Whole grains, beans, fruits and vegetables provide fibre. Food records report it in grams (g).";
        if (q.contains("carbohydrate")) return "Carbohydrates are one of the body's main sources of energy. Rice, roti, millets, fruit and pulses contain carbohydrates. Food records report them in grams (g).";
        if (q.contains("fat")) return "Fat provides energy and helps the body use some vitamins. Oils, nuts, seeds, dairy and many other foods contain fat. Food records report it in grams (g).";
        if (q.contains("vitamin")) return "Vitamins are nutrients the body needs in small amounts for many functions. Different fruits, vegetables, grains and animal foods provide different vitamins; this database does not contain complete vitamin data for every food.";
        if (q.contains("mineral")) return "Minerals are nutrients such as iron and calcium that support body functions. Food details show only minerals recorded for that food; a blank value means unknown here, not zero.";
        return "Calories are a measure of energy in food. This app reports kilocalories (kcal) for foods and logged meals.";
    }
    private static String askedNutrient(String q) { for (String n: List.of("calories","protein","carbohydrates","fat","fibre","fiber","iron","calcium")) if(q.contains(n)) return n.equals("fiber")?"fibre":n; return null; }
    private static java.math.BigDecimal nutrientValue(FoodDtos.Response f,String n) { return switch(n) { case "calories"->f.calories(); case "protein"->f.protein(); case "carbohydrates"->f.carbohydrates(); case "fat"->f.fat(); case "fibre","fiber"->f.fiber(); case "iron"->f.iron(); case "calcium"->f.calcium(); default->null; }; }
    private static String unit(String n) { return n.equals("calories")?"kcal":(n.equals("iron")||n.equals("calcium"))?"mg":"g"; }
    private static String foodTerm(String q) {
        Matcher m=Pattern.compile("\\b(?:in|of)\\s+(?:(?:a|an|the)\\s+)?(?:(?:cup|bowl|piece|serving)\\s+(?:of\\s+)?)?([a-z][a-z -]{1,60}?)[?.!]*$").matcher(q);
        if(m.find()) {
            String term = m.group(1).trim().replaceFirst("^(?:\\d+(?:\\.\\d+)?|one|two|three|four|five|a|an|the)\\s+", "")
                    .replaceFirst("^(?:homemade|home-made|cooked|raw|steamed|fried|boiled|medium|large|small)\\s+", "");
            if (term.endsWith("ies") && term.length() > 4) term = term.substring(0, term.length() - 3) + "y";
            else if (term.endsWith("s") && term.length() > 4 && !term.endsWith("ss")) term = term.substring(0, term.length() - 1);
            return term.trim();
        }
        return null;
    }
    private static NutritionAssistantDtos.Reference referenceFor(FoodDtos.Response f) {
        String source=Objects.toString(f.sourceName(),"Source unrecorded"); String url=f.sourceReference();
        Matcher matcher=Pattern.compile("https?://\\S+").matcher(Objects.toString(url,""));
        return ref(source, matcher.find()?matcher.group().replaceAll("[),.]$",""):null);
    }
    private static NutritionAssistantDtos.Reference ref(String name,String url) { return new NutritionAssistantDtos.Reference(name,url); }
}
