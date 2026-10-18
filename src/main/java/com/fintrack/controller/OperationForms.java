package com.fintrack.controller;

import com.fintrack.dto.AccountOption;
import com.fintrack.dto.MemberOption;
import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.repository.AccountRepository;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * Справочники для формы операции и фильтров: счета, категории, участники семьи.
 */
@Component
@RequiredArgsConstructor
public class OperationForms {

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AccessService accessService;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    /** Новая форма: расход, первый счёт, сегодняшняя дата. */
    public TransactionForm newForm(Long userId) {
        TransactionForm form = new TransactionForm();
        form.setOperationDate(transactionService.today(userId));
        List<Account> accounts = accountService.activeAccounts(userId);
        if (!accounts.isEmpty()) {
            form.setAccountId(accounts.get(0).getId());
        }
        return form;
    }

    /** Списки для формы операции. */
    public void fillFormOptions(Model model, CustomUserDetails me) {
        model.addAttribute("myAccounts", accountService.activeAccounts(me.id()).stream()
                .map(a -> new AccountOption(a.getId(), a.getName(), a.isSharedWithFamily())).toList());
        model.addAttribute("categories", categoryService.available(me.id()));
        List<MemberOption> members = accessService.familyMembers(me.id());
        String others = String.join(", ", members.stream().filter(m -> !m.me()).map(MemberOption::name).toList());
        // Подсказка видимости показывается только тем, кто в семье с другими участниками
        model.addAttribute("familyNames", others.isEmpty() ? null : others);
    }

    /** Списки для фильтров: видимые счета (свои и открытые счета семьи) и участники. */
    public void fillFilterOptions(Model model, CustomUserDetails me) {
        var visible = accessService.visibleAccountIds(me.id(), ViewMode.all());
        model.addAttribute("filterAccounts", visible.isEmpty() ? List.of()
                : accountRepository.findAllWithUsers(visible).stream()
                .map(a -> new AccountOption(a.getId(),
                        a.getUser().getId().equals(me.id()) ? a.getName() : a.getName() + " (" + a.getUser().getName() + ")",
                        a.isSharedWithFamily()))
                .toList());
        List<MemberOption> members = accessService.familyMembers(me.id());
        model.addAttribute("members", members.size() > 1 ? members : List.of());
    }
}
