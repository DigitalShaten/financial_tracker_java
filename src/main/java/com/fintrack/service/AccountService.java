package com.fintrack.service;

import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public Account createDefaultCashAccount(User user) {
        Account account = new Account();
        account.setUser(user);
        account.setName("Наличные");
        account.setType(AccountType.CASH);
        account.setCurrency(user.getCurrency());
        account.setBalance(BigDecimal.ZERO);
        account.setSharedWithFamily(true);
        return accountRepository.save(account);
    }
}