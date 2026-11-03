package com.childnutrition;

import com.childnutrition.dto.NutritionAssistantDtos;
import com.childnutrition.dto.ReportDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.repository.MealRepository;
import com.childnutrition.service.FoodNutritionService;
import com.childnutrition.service.NutritionAiUnavailableException;
import com.childnutrition.service.NutritionAssistantService;
import com.childnutrition.service.NutritionLanguageModel;
import com.childnutrition.service.NutritionReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NutritionAssistantServiceTests {
    @Test
    void sendsBroadQuestionsAndConversationHistoryToConfiguredModel() {
        NutritionLanguageModel model = mock(NutritionLanguageModel.class);
        when(model.answer(anyString(), anyList(), anyString())).thenReturn(new NutritionLanguageModel.ModelAnswer(
                "Calories measure energy; carbohydrates are one type of nutrient that provides energy.", List.of()));
        NutritionAssistantDtos.Turn previous = new NutritionAssistantDtos.Turn("user", "Explain calories.");
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("nutritionLanguageModel", model);
        NutritionAssistantService service = new NutritionAssistantService(mock(FoodNutritionService.class),
                mock(MealRepository.class), mock(NutritionReportService.class), beans.getBeanProvider(NutritionLanguageModel.class));

        NutritionAssistantDtos.Response response = service.answer("How are calories different from carbohydrates?", "parent@example.test", null, List.of(previous), null);

        assertEquals("ai", response.answerMode());
        assertTrue(response.answer().contains("Calories measure energy"));
        verify(model).answer(eq("How are calories different from carbohydrates?"), eq(List.of(previous)), contains("Matching application food records"));
    }

    @Test
    void providerFailureIsExplicitAndDoesNotInventAnAnswer() {
        NutritionLanguageModel model = mock(NutritionLanguageModel.class);
        when(model.answer(anyString(), anyList(), anyString())).thenThrow(new NutritionAiUnavailableException());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("nutritionLanguageModel", model);
        NutritionAssistantService service = new NutritionAssistantService(mock(FoodNutritionService.class),
                mock(MealRepository.class), mock(NutritionReportService.class), beans.getBeanProvider(NutritionLanguageModel.class));

        NutritionAssistantDtos.Response response = service.answer("Can you compare calories and carbohydrates?", "parent@example.test", null, List.of(), null);

        assertEquals("unavailable", response.answerMode());
        assertTrue(response.answer().contains("AI service is temporarily unavailable"));
        assertTrue(response.answer().contains("could not verify an answer"));
    }

    @Test
    void withoutAConfiguredModelResponseIdentifiesDatabaseOnlyMode() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        NutritionAssistantService service = new NutritionAssistantService(mock(FoodNutritionService.class),
                mock(MealRepository.class), mock(NutritionReportService.class), beans.getBeanProvider(NutritionLanguageModel.class));

        NutritionAssistantDtos.Response response = service.answer("What is protein?", "parent@example.test", null, List.of(), null);

        assertEquals("database", response.answerMode());
        assertTrue(response.dataNote().contains("not configured"));
    }

    @Test
    void reportQuestionUsesSelectedChildDailyReportAsGroundingContext() {
        NutritionLanguageModel model = mock(NutritionLanguageModel.class);
        when(model.answer(anyString(), anyList(), anyString())).thenAnswer(invocation ->
                new NutritionLanguageModel.ModelAnswer(invocation.getArgument(2, String.class).contains("Recorded on")
                        ? "This is a food-log estimate, not a diagnosis." : "No report context.", List.of()));
        LocalDate date = LocalDate.now();
        ReportDtos.NutrientReport nutrient = new ReportDtos.NutrientReport("Calories", new BigDecimal("430"), "kcal",
                null, null, null, true, false, "Recorded food energy", List.of());
        ReportDtos.DailyNutrition report = new ReportDtos.DailyNutrition(date, 6, "female", List.of(nutrient), List.of(),
                false, List.of(), "A food log may be incomplete.");
        NutritionReportService reports = mock(NutritionReportService.class);
        when(reports.daily(any(), eq(date), anyList(), eq("parent@example.test"))).thenReturn(report);
        MealRepository meals = mock(MealRepository.class);
        when(meals.findByChildIdAndMealDate(7L, date)).thenReturn(List.of());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("nutritionLanguageModel", model);
        NutritionAssistantService service = new NutritionAssistantService(mock(FoodNutritionService.class),
                meals, reports, beans.getBeanProvider(NutritionLanguageModel.class));
        Child child = new Child(); child.setId(7L); child.setDateOfBirth(date.minusYears(6)); child.setGender("female");

        NutritionAssistantDtos.Response response = service.answer("Explain my child's nutrition report", "parent@example.test", child, List.of(), date);

        assertTrue(response.answer().contains("not a diagnosis"));
        verify(model).answer(eq("Explain my child's nutrition report"), eq(List.of()), contains("Calories 430 kcal"));
    }
}
