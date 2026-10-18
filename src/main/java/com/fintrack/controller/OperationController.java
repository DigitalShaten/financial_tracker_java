package com.fintrack.controller;

import com.fintrack.dto.TransactionFilter;
import com.fintrack.dto.TransactionForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Операции: список с фильтрами и форма новой операции, изменение и удаление своих операций.
 */
@Controller
@RequiredArgsConstructor
public class OperationController {

    private final TransactionService transactionService;
    private final OperationForms forms;

    @GetMapping("/operations")
    public String list(@AuthenticationPrincipal CustomUserDetails me,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long accountId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long memberId,
                       @RequestParam(required = false) String query,
                       @RequestParam(required = false) Integer page,
                       Model model) {
        TransactionFilter filter = new TransactionFilter(from, to, accountId, categoryId, memberId, query, page);
        model.addAttribute("form", forms.newForm(me.id()));
        fillListPage(model, me, filter);
        return "operations/index";
    }

    @PostMapping("/operations")
    public String create(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("form") TransactionForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                transactionService.create(me.id(), form);
                redirect.addFlashAttribute("toast", "Операция сохранена");
                return "redirect:/operations";
            } catch (BusinessException e) {
                binding.reject("business", e.getMessage());
            }
        }
        fillListPage(model, me, new TransactionFilter(null, null, null, null, null, null, null));
        return "operations/index";
    }

    @GetMapping("/operations/{id}/edit")
    public String edit(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id, Model model) {
        model.addAttribute("form", transactionService.formOf(me.id(), id));
        model.addAttribute("operationId", id);
        forms.fillFormOptions(model, me);
        return "operations/edit";
    }

    @PostMapping("/operations/{id}")
    public String update(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         @Valid @ModelAttribute("form") TransactionForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        // Чужая операция → 404 ещё до проверки формы
        transactionService.getOwned(me.id(), id);
        if (!binding.hasErrors()) {
            try {
                transactionService.update(me.id(), id, form);
                redirect.addFlashAttribute("toast", "Операция изменена");
                return "redirect:/operations";
            } catch (BusinessException e) {
                binding.reject("business", e.getMessage());
            }
        }
        model.addAttribute("operationId", id);
        forms.fillFormOptions(model, me);
        return "operations/edit";
    }

    @PostMapping("/operations/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         RedirectAttributes redirect) {
        transactionService.delete(me.id(), id);
        redirect.addFlashAttribute("toast", "Операция удалена");
        return "redirect:/operations";
    }

    private void fillListPage(Model model, CustomUserDetails me, TransactionFilter rawFilter) {
        TransactionFilter filter = transactionService.withDefaults(me.id(), rawFilter);
        model.addAttribute("filter", filter);
        model.addAttribute("ops", transactionService.list(me.id(), filter));
        forms.fillFormOptions(model, me);
        forms.fillFilterOptions(model, me);
    }
}
