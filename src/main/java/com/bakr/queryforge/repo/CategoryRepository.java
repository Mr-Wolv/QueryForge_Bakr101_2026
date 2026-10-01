package com.bakr.queryforge.repo;

import com.bakr.queryforge.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
