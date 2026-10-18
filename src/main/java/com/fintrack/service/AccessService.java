package com.fintrack.service;

import com.fintrack.dto.MemberOption;
import com.fintrack.entity.FamilyMember;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Единственное место, где решается, какие счета пользователь ВИДИТ.
 * Все запросы обзора, списка операций и отчётов фильтруются по {@link #visibleAccountIds}.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final AccountRepository accountRepository;
    private final FamilyMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Set<Long> visibleAccountIds(Long userId, ViewMode mode) {
        Set<Long> ids = new HashSet<>();
        if (mode.includesMe()) {
            accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(userId)
                    .forEach(a -> ids.add(a.getId()));
        }
        List<Long> others = otherMembersToShow(userId, mode);
        if (!others.isEmpty()) {
            accountRepository.findAllByUser_IdInAndSharedWithFamilyTrueAndArchivedFalse(others)
                    .forEach(a -> ids.add(a.getId()));
        }
        return ids;
    }

    /** Пользователи, чьи данные попадают в выбранный режим (для бюджетов и целей). */
    @Transactional(readOnly = true)
    public List<Long> visibleUserIds(Long userId, ViewMode mode) {
        List<Long> result = new ArrayList<>();
        if (mode.includesMe()) {
            result.add(userId);
        }
        result.addAll(otherMembersToShow(userId, mode));
        return result;
    }

    /** Все участники семьи пользователя: он сам первым, затем остальные по порядку вступления. */
    @Transactional(readOnly = true)
    public List<MemberOption> familyMembers(Long userId) {
        return memberRepository.findByUser_Id(userId)
                .map(me -> memberRepository.findAllWithUsers(me.getFamily().getId()).stream()
                        .map(m -> new MemberOption(m.getUser().getId(), m.getUser().getName(),
                                m.getUser().getId().equals(userId)))
                        .sorted(Comparator.comparing(MemberOption::me).reversed())
                        .toList())
                .orElse(List.of());
    }

    /** Есть ли в семье пользователя другие участники. */
    @Transactional(readOnly = true)
    public boolean hasOtherMembers(Long userId) {
        return !memberRepository.findFamilyUserIds(userId).isEmpty();
    }

    /** Состоит ли пользователь в той же семье, что и другой пользователь. */
    @Transactional(readOnly = true)
    public boolean sameFamily(Long userId, Long otherUserId) {
        return userId.equals(otherUserId) || memberRepository.findFamilyUserIds(userId).contains(otherUserId);
    }

    @Transactional(readOnly = true)
    public Long familyIdOf(Long userId) {
        return memberRepository.findByUser_Id(userId).map(FamilyMember::getFamily).map(f -> f.getId()).orElse(null);
    }

    private List<Long> otherMembersToShow(Long userId, ViewMode mode) {
        if (mode.isOnlyMe()) {
            return List.of();
        }
        List<Long> familyUserIds = memberRepository.findFamilyUserIds(userId); // все, кроме меня
        if (mode.isAll()) {
            return familyUserIds;
        }
        if (!familyUserIds.contains(mode.memberId())) {
            throw new NotFoundException("Участник не найден");
        }
        return List.of(mode.memberId());
    }
}
