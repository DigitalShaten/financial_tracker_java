package com.fintrack.controller;

import com.fintrack.dto.ContributionForm;
import com.fintrack.dto.GoalForm;
import com.fintrack.dto.GoalPlanForm;
import com.fintrack.entity.Goal;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Цели: список, расчёт выбранной цели, новая цель, план, пополнение, удаление.
 */
@Controller
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    /** Открывает первую цель (или приглашение создать первую). */
    @GetMapping("/goals")
    public String goals(@AuthenticationPrincipal CustomUserDetails me, Model model) {
        List<Goal> goals = goalService.visibleGoals(me.id());
        fill(model, me, goals.isEmpty() ? null : goals.get(0).getId());
        return "goals/index";
    }

    @GetMapping("/goals/{id}")
    public String goal(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id, Model model) {
        goalService.getVisible(me.id(), id);
        fill(model, me, id);
        return "goals/index";
    }

    @PostMapping("/goals")
    public String create(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("goalForm") GoalForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                Goal goal = goalService.create(me.id(), form);
                redirect.addFlashAttribute("toast", "Цель «" + goal.getName() + "» создана");
                return "redirect:/goals/" + goal.getId();
            } catch (BusinessException e) {
                binding.reject("business", e.getMessage());
            }
        }
        List<Goal> goals = goalService.visibleGoals(me.id());
        fill(model, me, goals.isEmpty() ? null : goals.get(0).getId());
        model.addAttribute("openNewGoal", true);
        return "goals/index";
    }

    @PostMapping("/goals/{id}/plan")
    public String plan(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                       @Valid @ModelAttribute("planForm") GoalPlanForm form, BindingResult binding,
                       RedirectAttributes redirect) {
        goalService.getVisible(me.id(), id);
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("toast", binding.getAllErrors().get(0).getDefaultMessage());
        } else {
            try {
                goalService.updatePlan(me.id(), id, form);
                redirect.addFlashAttribute("toast", "План цели сохранён");
            } catch (BusinessException e) {
                redirect.addFlashAttribute("toast", e.getMessage());
            }
        }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/goals/{id}/contributions")
    public String contribute(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                             @Valid @ModelAttribute("contributionForm") ContributionForm form, BindingResult binding,
                             RedirectAttributes redirect) {
        goalService.getVisible(me.id(), id);
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("toast", binding.getAllErrors().get(0).getDefaultMessage());
        } else {
            goalService.contributeToday(me.id(), id, form.getAmount());
            redirect.addFlashAttribute("toast", "Цель пополнена");
        }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/goals/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         RedirectAttributes redirect) {
        try {
            goalService.delete(me.id(), id);
            redirect.addFlashAttribute("toast", "Цель удалена");
            return "redirect:/goals";
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
            return "redirect:/goals/" + id;
        }
    }

    private void fill(Model model, CustomUserDetails me, Long activeId) {
        model.addAttribute("cards", goalService.cards(me.id(), activeId));
        model.addAttribute("goal", activeId == null ? null : goalService.detail(me.id(), activeId));
        if (!model.containsAttribute("goalForm")) {
            model.addAttribute("goalForm", new GoalForm());
        }
        model.addAttribute("contributionForm", new ContributionForm());
    }
}
