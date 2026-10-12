package com.fintrack.repository;

import com.fintrack.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, Long>  {
}
