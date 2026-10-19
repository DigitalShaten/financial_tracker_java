package com.fintrack.controller.admin;

import com.fintrack.dto.AdminCategoryView;
import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.CategoryType;
import com.fintrack.exception.BusinessException;
import com.fintrack.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Стандартные категории: создание, переименование, архив, удаление (только без операций).
 */
@Controller
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminService adminService;

    @GetMapping("/admin/categories")
    public String page(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        fill(model);
        return "admin/categories";
    }

    @PostMapping("/admin/categories")
    public String create(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                adminService.createCategory(form);
                redirect.addFlashAttribute("toast", "Категория добавлена");
                return "redirect:/admin/categories";
            } catch (BusinessException e) {
                binding.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        fill(model);
        return "admin/categories";
    }

    @PostMapping("/admin/categories/{id}")
    public String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirect) {
        try {
            adminService.renameCategory(id, name);
            redirect.addFlashAttribute("toast", "Категория переименована");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        boolean archived = adminService.toggleArchive(id);
        redirect.addFlashAttribute("toast", archived ? "Категория в архиве" : "Категория снова доступна");
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            adminService.deleteCategory(id);
            redirect.addFlashAttribute("toast", "Категория удалена");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    private void fill(Model model) {
        List<AdminCategoryView> all = adminService.standardCategories();
        // две таблицы: "Расходы" и "Доходы" (LinkedHashMap сохраняет порядок)
        Map<String, List<AdminCategoryView>> groups = new LinkedHashMap<>();
        groups.put("Расходы", all.stream().filter(c -> c.type() == CategoryType.EXPENSE).toList());
        groups.put("Доходы", all.stream().filter(c -> c.type() == CategoryType.INCOME).toList());
        model.addAttribute("categoryGroups", groups);
        model.addAttribute("types", CategoryType.values());
    }
}
