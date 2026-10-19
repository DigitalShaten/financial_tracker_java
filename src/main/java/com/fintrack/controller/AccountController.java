package com.fintrack.controller;

import com.fintrack.dto.AccountForm;
import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.CategoryType;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.AccountService;
import com.fintrack.service.CategoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AccountsPage accountsPage;

    @GetMapping("/accounts")
    public String page(@AuthenticationPrincipal CustomUserDetails me,
                       @RequestParam(required = false) String month, Model model) {
        accountsPage.fill(model, me.id(), month);
        return "accounts/index";
    }

    @PostMapping("/accounts")
    public String create(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("accountForm") AccountForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            accountsPage.fill(model, me.id(), null);
            return "accounts/index";
        }
        accountService.create(me.id(), form);
        redirect.addFlashAttribute("toast", "Счёт «" + form.getName().trim() + "» добавлен");
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}")
    public String update(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         @Valid @ModelAttribute("editForm") AccountForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            // Проверяем, что счёт свой, ещё до показа формы с ошибками
            accountService.getOwned(me.id(), id);
            accountsPage.fill(model, me.id(), null);
            model.addAttribute("editAccountId", id);
            model.addAttribute("editError", binding.getAllErrors().get(0).getDefaultMessage());
            return "accounts/index";
        }
        accountService.update(me.id(), id, form);
        redirect.addFlashAttribute("toast", "Счёт сохранён");
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}/archive")
    public String archive(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                          RedirectAttributes redirect) {
        boolean archived = accountService.toggleArchive(me.id(), id);
        redirect.addFlashAttribute("toast", archived ? "Счёт перенесён в архив" : "Счёт возвращён из архива");
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         RedirectAttributes redirect) {
        try {
            accountService.delete(me.id(), id);
            redirect.addFlashAttribute("toast", "Счёт удалён");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/accounts";
    }

    /** Флажок "Видно семье". Используется на страницах "Счета и бюджеты" и "Семья". */
    @PostMapping("/accounts/{id}/visibility")
    public String visibility(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean shared,
                             @RequestParam(defaultValue = "accounts") String returnTo,
                             RedirectAttributes redirect) {
        accountService.setVisibility(me.id(), id, shared);
        redirect.addFlashAttribute("toast", shared ? "Счёт теперь виден семье" : "Счёт скрыт от семьи");
        return "family".equals(returnTo) ? "redirect:/family" : "redirect:/accounts";
    }

    @PostMapping("/categories")
    public String createCategory(@AuthenticationPrincipal CustomUserDetails me,
                                 @Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult binding,
                                 Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                categoryService.create(me.id(), form);
                redirect.addFlashAttribute("toast", "Категория «" + form.getName().trim() + "» добавлена");
                return "redirect:/accounts";
            } catch (BusinessException e) {
                binding.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        accountsPage.fill(model, me.id(), null);
        return "accounts/index";
    }

    @PostMapping("/categories/{id}")
    public String renameCategory(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                                 @RequestParam String name, RedirectAttributes redirect) {
        try {
            categoryService.rename(me.id(), id, name);
            redirect.addFlashAttribute("toast", "Категория переименована");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/categories/{id}/archive")
    public String archiveCategory(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                                  RedirectAttributes redirect) {
        boolean archived = categoryService.toggleArchive(me.id(), id);
        redirect.addFlashAttribute("toast", archived ? "Категория перенесена в архив" : "Категория возвращена");
        return "redirect:/accounts";
    }

    /** Справочники для выпадающих списков. */
    @ModelAttribute("accountTypes")
    public AccountType[] accountTypes() {
        return AccountType.values();
    }

    @ModelAttribute("categoryTypes")
    public CategoryType[] categoryTypes() {
        return CategoryType.values();
    }
}
