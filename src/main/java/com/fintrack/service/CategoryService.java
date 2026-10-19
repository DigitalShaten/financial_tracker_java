package com.fintrack.service;

import com.fintrack.dto.CategoryForm;
import com.fintrack.dto.CategoryView;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.TransactionType;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Категории: стандартные (общие для всех, ведёт администратор) и свои.
 * Пользователь меняет только свои; стандартные для него "не найдены".
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    /** Стандартные и свои неархивные категории - для выбора в формах. */
    @Transactional(readOnly = true)
    public List<CategoryView> available(Long userId) {
        return categoryRepository.findAvailable(userId).stream().map(CategoryService::toView).toList();
    }

    /** Доступные категории расходов (для бюджетов). */
    @Transactional(readOnly = true)
    public List<CategoryView> availableExpenses(Long userId) {
        return available(userId).stream().filter(c -> c.type() == CategoryType.EXPENSE).toList();
    }

    /** Свои категории пользователя, включая архивные. */
    @Transactional(readOnly = true)
    public List<CategoryView> own(Long userId) {
        return categoryRepository.findAllByUser_IdOrderByTypeAscNameAsc(userId).stream()
                .map(CategoryService::toView).toList();
    }

    /** Стандартные неархивные категории - показываются на странице как справка. */
    @Transactional(readOnly = true)
    public List<CategoryView> standard() {
        return categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .filter(c -> !c.isArchived())
                .map(CategoryService::toView).toList();
    }

    /**
     * Категория для операции: должна быть доступна пользователю (стандартная или своя),
     * и её тип должен совпадать с типом операции.
     */
    @Transactional(readOnly = true)
    public Category getAvailable(Long userId, Long categoryId, TransactionType type) {
        if (categoryId == null) {
            throw new BusinessException("Выберите категорию");
        }
        Category category = categoryRepository.findAvailableById(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (type == TransactionType.TRANSFER || category.getType() != expected) {
            throw new BusinessException("Категория не подходит к типу операции");
        }
        return category;
    }

    @Transactional
    public Category create(Long userId, CategoryForm form) {
        String name = form.getName().trim();
        if (categoryRepository.existsAvailableByName(userId, name, form.getType())) {
            throw new BusinessException("Такая категория уже есть");
        }
        Category category = new Category();
        category.setUser(userRepository.getReferenceById(userId));
        category.setName(name);
        category.setType(form.getType());
        return categoryRepository.save(category);
    }

    @Transactional
    public void rename(Long userId, Long categoryId, String newName) {
        Category category = getOwn(userId, categoryId);
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new BusinessException("Название категории — от 1 до 60 символов");
        }
        if (!name.equalsIgnoreCase(category.getName())
                && categoryRepository.existsAvailableByName(userId, name, category.getType())) {
            throw new BusinessException("Такая категория уже есть");
        }
        category.setName(name);
    }

    /** Архивирует свою категорию или возвращает из архива. */
    @Transactional
    public boolean toggleArchive(Long userId, Long categoryId) {
        Category category = getOwn(userId, categoryId);
        category.setArchived(!category.isArchived());
        return category.isArchived();
    }

    private Category getOwn(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUser_Id(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    static CategoryView toView(Category c) {
        return new CategoryView(c.getId(), c.getName(), c.getType(), c.getType().getLabel(),
                c.isStandard(), c.isArchived());
    }
}
