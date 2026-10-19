package com.fintrack.controller.admin;

import com.fintrack.dto.AdminForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.AdminService;
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

/**
 * Администраторы: создание, изменение, удаление (последнего и самого себя - нельзя).
 */
@Controller
@RequiredArgsConstructor
public class AdminAdminController {

    private final AdminService adminService;

    @GetMapping("/admin/admins")
    public String page(@AuthenticationPrincipal CustomUserDetails me, Model model) {
        model.addAttribute("adminForm", new AdminForm());
        model.addAttribute("admins", adminService.admins(me.id()));
        return "admin/admins";
    }

    @PostMapping("/admin/admins")
    public String create(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("adminForm") AdminForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                adminService.createAdmin(form);
                redirect.addFlashAttribute("toast", "Администратор добавлен");
                return "redirect:/admin/admins";
            } catch (BusinessException e) {
                binding.reject("admin", e.getMessage());
            }
        }
        model.addAttribute("admins", adminService.admins(me.id()));
        return "admin/admins";
    }

    @PostMapping("/admin/admins/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute AdminForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("toast", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/admins";
        }
        try {
            adminService.updateAdmin(id, form);
            redirect.addFlashAttribute("toast", "Данные администратора сохранены");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    @PostMapping("/admin/admins/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                         RedirectAttributes redirect) {
        try {
            adminService.deleteAdmin(me.id(), id);
            redirect.addFlashAttribute("toast", "Администратор удалён");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/admins";
    }
}
