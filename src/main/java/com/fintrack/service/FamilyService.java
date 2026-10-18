package com.fintrack.service;

import com.fintrack.dto.CurrentUserView;
import com.fintrack.dto.InviteView;
import com.fintrack.dto.MemberView;
import com.fintrack.entity.*;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.*;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Семья: создание, приглашения, вступление, выход, исключение, роспуск.
 * Данные (счета, операции, цели) всегда принадлежат пользователям и при любых изменениях семьи остаются у них.
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration INVITE_TTL = Duration.ofHours(24);

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyInviteRepository inviteRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Family create(Long userId, String name) {
        if (memberRepository.existsByUser_Id(userId)) {
            throw new BusinessException("Вы уже состоите в семье");
        }
        User user = userRepository.getReferenceById(userId);
        Family family = new Family();
        family.setName(name.trim());
        family.setOwner(user);
        familyRepository.save(family);

        FamilyMember owner = new FamilyMember();
        owner.setFamily(family);
        owner.setUser(user);
        owner.setRole(FamilyRole.OWNER);
        memberRepository.save(owner);
        return family;
    }

    /** Новый код приглашения; старые неиспользованные коды семьи гасятся. */
    @Transactional
    public FamilyInvite createInvite(Long ownerId) {
        FamilyMember owner = memberRepository.findByUser_Id(ownerId)
                .filter(m -> m.getRole() == FamilyRole.OWNER)
                .orElseThrow(() -> new BusinessException("Приглашать может только владелец семьи"));

        OffsetDateTime now = now();
        inviteRepository.expireActive(owner.getFamily().getId(), now);

        FamilyInvite invite = new FamilyInvite();
        invite.setFamily(owner.getFamily());
        invite.setCreatedBy(owner.getUser());
        invite.setCode(uniqueCode());
        invite.setExpiresAt(now.plus(INVITE_TTL));
        return inviteRepository.save(invite);
    }

    /** Вступление по коду: код существует, не использован и не истёк; пользователь ещё не в семье. */
    @Transactional
    public Family join(Long userId, String rawCode) {
        if (memberRepository.existsByUser_Id(userId)) {
            throw new BusinessException("Вы уже состоите в семье");
        }
        OffsetDateTime now = now();
        FamilyInvite invite = inviteRepository.findByCode(normalizeCode(rawCode))
                .filter(i -> i.getUsedAt() == null)
                .filter(i -> i.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException("Код недействителен или истёк"));

        FamilyMember member = new FamilyMember();
        member.setFamily(invite.getFamily());
        member.setUser(userRepository.getReferenceById(userId));
        member.setRole(FamilyRole.MEMBER);
        memberRepository.save(member);
        invite.setUsedAt(now);
        return invite.getFamily();
    }

    /**
     * Выход из семьи. Участник уходит со всеми своими данными, его семейные цели становятся личными.
     * Владелец выйти не может, пока в семье есть другие участники; если он один — семья распускается.
     */
    @Transactional
    public void leave(Long userId) {
        FamilyMember member = membership(userId);
        Long familyId = member.getFamily().getId();
        if (member.getRole() == FamilyRole.OWNER) {
            if (memberRepository.countByFamily_Id(familyId) > 1) {
                throw new BusinessException("Владелец не может выйти, пока в семье есть другие участники — распустите семью");
            }
            dissolve(userId);
            return;
        }
        goalRepository.detachOwnerGoalsFromFamily(familyId, userId);
        memberRepository.delete(member);
    }

    /** Исключение участника владельцем (себя — нельзя). */
    @Transactional
    public void removeMember(Long ownerId, Long memberUserId) {
        FamilyMember owner = ownerMembership(ownerId);
        if (ownerId.equals(memberUserId)) {
            throw new BusinessException("Нельзя исключить себя — можно только распустить семью");
        }
        FamilyMember member = memberRepository.findByUser_Id(memberUserId)
                .filter(m -> m.getFamily().getId().equals(owner.getFamily().getId()))
                .orElseThrow(() -> new NotFoundException("Участник не найден"));
        goalRepository.detachOwnerGoalsFromFamily(owner.getFamily().getId(), memberUserId);
        memberRepository.delete(member);
    }

    /** Роспуск: семейные цели становятся личными целями их создателей, данные участников сохраняются. */
    @Transactional
    public void dissolve(Long ownerId) {
        Long familyId = ownerMembership(ownerId).getFamily().getId();
        goalRepository.detachAllFromFamily(familyId);
        inviteRepository.deleteAllByFamilyId(familyId);
        memberRepository.deleteAllByFamilyId(familyId);
        familyRepository.deleteFamilyById(familyId);
    }

    // ---------------------------------------------------------------- для страницы

    @Transactional(readOnly = true)
    public Optional<FamilyMember> findMembership(Long userId) {
        return memberRepository.findByUser_Id(userId);
    }

    /** Действующий код приглашения семьи (для владельца). */
    @Transactional(readOnly = true)
    public Optional<InviteView> activeInvite(Long ownerId, String baseUrl) {
        return memberRepository.findByUser_Id(ownerId)
                .filter(m -> m.getRole() == FamilyRole.OWNER)
                .flatMap(m -> inviteRepository.findFirstByFamily_IdAndUsedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(
                        m.getFamily().getId(), now()))
                .map(i -> new InviteView(i.getCode(), expiresText(i, ownerId), baseUrl + "/family/join?code=" + i.getCode()));
    }

    /** Код вводится без учёта регистра и пробелов; дефис можно не ставить. */
    public static String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase();
        if (code.length() == 6 && !code.contains("-")) {
            code = code.substring(0, 3) + "-" + code.substring(3);
        }
        return code;
    }

    private FamilyMember membership(Long userId) {
        return memberRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException("Вы не состоите в семье"));
    }

    private FamilyMember ownerMembership(Long userId) {
        FamilyMember member = membership(userId);
        if (member.getRole() != FamilyRole.OWNER) {
            throw new BusinessException("Это может сделать только владелец семьи");
        }
        return member;
    }

    private String uniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (inviteRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i == 3) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String expiresText(FamilyInvite invite, Long userId) {
        ZoneId zone = ZoneId.of(userRepository.findById(userId).map(User::getTimeZone).orElse("Europe/Minsk"));
        var at = invite.getExpiresAt().atZoneSameInstant(zone);
        return "Действует до " + DateFormat.dayMonth(at.toLocalDate()) + ", " + at.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
