package com.fintrack.service;

import com.fintrack.dto.PasswordForm;
import com.fintrack.dto.ProfileForm;
import com.fintrack.entity.Family;
import com.fintrack.entity.FamilyMember;
import com.fintrack.entity.FamilyRole;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.FamilyMemberRepository;
import com.fintrack.repository.FamilyRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.support.IntegrationTest;
import com.fintrack.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FamilyRepository familyRepository;
    @Autowired
    private FamilyMemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User anna;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
    }

    @Test
    void profileUpdateChangesNameCurrencyAndTimeZone() {
        ProfileForm form = new ProfileForm();
        form.setName("Анна Иванова");
        form.setCurrency("EUR");
        form.setTimeZone("Europe/Moscow");

        userService.updateProfile(anna.getId(), form);

        User saved = userRepository.findById(anna.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Анна Иванова");
        assertThat(saved.getCurrency()).isEqualTo("EUR");
        assertThat(saved.getTimeZone()).isEqualTo("Europe/Moscow");
        assertThat(accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(anna.getId()))
                .allSatisfy(a -> assertThat(a.getCurrency()).isEqualTo("EUR"));
    }

    @Test
    void unknownTimeZoneIsRejected() {
        ProfileForm form = new ProfileForm();
        form.setName("Анна");
        form.setCurrency("BYN");
        form.setTimeZone("Mars/Olympus");

        assertThatThrownBy(() -> userService.updateProfile(anna.getId(), form)).isInstanceOf(BusinessException.class);
    }

    @Test
    void passwordChangeRequiresCurrentPassword() {
        PasswordForm form = new PasswordForm();
        form.setCurrentPassword("wrong-password");
        form.setNewPassword("newpassword1");
        form.setConfirmPassword("newpassword1");

        assertThatThrownBy(() -> userService.changePassword(anna.getId(), form))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Текущий пароль");

        form.setCurrentPassword(TestFixtures.PASSWORD);
        userService.changePassword(anna.getId(), form);
        assertThat(passwordEncoder.matches("newpassword1",
                userRepository.findById(anna.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void deleteRequiresCorrectPassword() {
        assertThatThrownBy(() -> userService.deleteAccount(anna.getId(), "wrong-password"))
                .isInstanceOf(BusinessException.class);
        assertThat(userRepository.existsById(anna.getId())).isTrue();
    }

    @Test
    void familyOwnerWithMembersMustDissolveFirst() {
        User boris = fixtures.createUser("Борис", "boris@test.by");
        Family family = new Family();
        family.setName("Семья");
        family.setOwner(anna);
        familyRepository.save(family);
        memberRepository.save(member(family, anna, FamilyRole.OWNER));
        memberRepository.save(member(family, boris, FamilyRole.MEMBER));

        assertThatThrownBy(() -> userService.deleteAccount(anna.getId(), TestFixtures.PASSWORD))
                .isInstanceOf(BusinessException.class).hasMessage("Сначала распустите семью");
    }

    @Test
    void deleteRemovesUserWithAllData() {
        fixtures.createAccount(anna, "Карта", "10", true);
        Family family = new Family();
        family.setName("Семья");
        family.setOwner(anna);
        familyRepository.save(family);
        memberRepository.save(member(family, anna, FamilyRole.OWNER));

        userService.deleteAccount(anna.getId(), TestFixtures.PASSWORD);

        assertThat(userRepository.existsById(anna.getId())).isFalse();
        assertThat(accountRepository.findAllByUser_IdOrderByArchivedAscNameAsc(anna.getId())).isEmpty();
        assertThat(familyRepository.existsById(family.getId())).isFalse();
    }

    private static FamilyMember member(Family family, User user, FamilyRole role) {
        FamilyMember m = new FamilyMember();
        m.setFamily(family);
        m.setUser(user);
        m.setRole(role);
        return m;
    }
}
