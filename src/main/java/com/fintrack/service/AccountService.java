package com.fintrack.service;

import com.fintrack.dto.AccountForm;
import com.fintrack.dto.AccountView;
import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Счета пользователя. Любое изменение ищет счёт по id и владельцу: чужой счёт -> 404.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /** Счёт пользователя или {@link NotFoundException}, если счёт чужой или его нет. */
    @Transactional(readOnly = true)
    public Account getOwned(Long userId, Long accountId) {
        if (accountId == null) {
            throw new NotFoundException("Счёт не найден");
        }
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Счёт не найден"));
    }

    /** Неархивные счета - для выбора в формах. */
    @Transactional(readOnly = true)
    public List<Account> activeAccounts(Long userId) {
        return accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(userId);
    }

    /** Все счета для страницы "Счета и бюджеты": сначала активные, потом архивные. */
    @Transactional(readOnly = true)
    public List<AccountView> accountViews(Long userId) {
        String currency = userRepository.findById(userId).map(User::getCurrency).orElse("BYN");
        return accountRepository.findAllByUser_IdOrderByArchivedAscNameAsc(userId).stream()
                .map(a -> toView(a, currency))
                .toList();
    }

    @Transactional
    public Account create(Long userId, AccountForm form) {
        User user = userRepository.getReferenceById(userId);
        Account account = new Account();
        account.setUser(user);
        account.setName(form.getName().trim());
        account.setType(form.getType());
        account.setCurrency(userRepository.findById(userId).map(User::getCurrency).orElse("BYN"));
        account.setBalance(form.getInitialBalance() == null ? BigDecimal.ZERO : form.getInitialBalance());
        account.setSharedWithFamily(form.isSharedWithFamily());
        return accountRepository.save(account);
    }

    /** Изменение названия, типа и видимости. Баланс меняется только операциями. */
    @Transactional
    public void update(Long userId, Long accountId, AccountForm form) {
        Account account = getOwned(userId, accountId);
        account.setName(form.getName().trim());
        account.setType(form.getType());
        account.setSharedWithFamily(form.isSharedWithFamily());
    }

    /** Архивирует счёт или возвращает его из архива. Возвращает новое состояние. */
    @Transactional
    public boolean toggleArchive(Long userId, Long accountId) {
        Account account = getOwned(userId, accountId);
        account.setArchived(!account.isArchived());
        return account.isArchived();
    }

    /** Удалить можно только счёт без операций - иначе история потеряется. */
    @Transactional
    public void delete(Long userId, Long accountId) {
        Account account = getOwned(userId, accountId);
        if (transactionRepository.existsByAnyAccount(accountId)) {
            throw new BusinessException("По счёту есть операции — его можно только архивировать");
        }
        accountRepository.delete(account);
    }

    @Transactional
    public void setVisibility(Long userId, Long accountId, boolean shared) {
        getOwned(userId, accountId).setSharedWithFamily(shared);
    }

    static AccountView toView(Account a, String currency) {
        return new AccountView(a.getId(), a.getName(), a.getType(), a.getType().getLabel(),
                MoneyFormat.format(a.getBalance(), currency), a.isSharedWithFamily(), a.isArchived());
    }
}
