package com.fintrack.repository;

import com.fintrack.entity.GoalContributions;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalContributionRepository extends JpaRepository<GoalContributions, Long> {
}
