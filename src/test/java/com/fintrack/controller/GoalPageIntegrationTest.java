package com.fintrack.controller;

import com.fintrack.entity.User;
import com.fintrack.repository.GoalRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalPageIntegrationTest extends IntegrationTest {

    @Autowired
    private GoalRepository goalRepository;

    private User anna;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
    }

    @Test
    void emptyPageInvitesToCreateFirstGoal() throws Exception {
        mvc.perform(get("/goals").with(user(fixtures.principalOf(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Целей пока нет")));
    }

    @Test
    void createdGoalIsShownWithPlanAndCanBeContributed() throws Exception {
        var me = fixtures.principalOf(anna);
        mvc.perform(post("/goals").with(user(me)).with(csrf())
                        .param("name", "Квартира").param("price", "95000").param("years", "5")
                        .param("inflation", "5").param("rate", "6").param("family", "false"))
                .andExpect(status().is3xxRedirection());
        Long id = goalRepository.findAll().get(0).getId();

        mvc.perform(post("/goals/{id}/contributions", id).with(user(me)).with(csrf()).param("amount", "18 400"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/goals/{id}", id).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Расчёт цели «Квартира»")))
                .andExpect(content().string(containsString("1 382")))          // взнос для примера 95 000 / 5 лет
                .andExpect(content().string(containsString("121 247 BYN")))
                .andExpect(content().string(containsString("60 ежемесячных взносов")))
                .andExpect(content().string(containsString("Свободных денег в последние месяцы не оставалось")));
    }

    @Test
    void invalidGoalFormShowsErrors() throws Exception {
        mvc.perform(post("/goals").with(user(fixtures.principalOf(anna))).with(csrf())
                        .param("name", "").param("price", "0").param("years", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Введите название цели")))
                .andExpect(content().string(containsString("Срок — от 1 до 20 лет")));
        assertThat(goalRepository.count()).isZero();
    }

    @Test
    void otherUsersGoalIsNotFound() throws Exception {
        User boris = fixtures.createUser("Борис", "boris@test.by");
        mvc.perform(post("/goals").with(user(fixtures.principalOf(boris))).with(csrf())
                .param("name", "Секрет").param("price", "100").param("years", "1"));
        Long id = goalRepository.findAll().get(0).getId();

        mvc.perform(get("/goals/{id}", id).with(user(fixtures.principalOf(anna)))).andExpect(status().isNotFound());
        mvc.perform(post("/goals/{id}/delete", id).with(user(fixtures.principalOf(anna))).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(goalRepository.existsById(id)).isTrue();
    }
}
