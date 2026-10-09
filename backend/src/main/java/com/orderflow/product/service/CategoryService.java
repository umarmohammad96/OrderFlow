package com.orderflow.product.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.product.domain.Category;
import com.orderflow.product.dto.CategoryRequest;
import com.orderflow.product.dto.CategoryResponse;
import com.orderflow.product.repository.CategoryRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("CATEGORY_EXISTS", "Category '" + name + "' already exists");
        }
        return CategoryResponse.from(categories.save(new Category(name)));
    }
}
