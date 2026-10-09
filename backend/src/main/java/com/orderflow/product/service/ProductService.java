package com.orderflow.product.service;

import com.orderflow.common.config.CacheNames;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.common.response.PageResponse;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.Product;
import com.orderflow.product.dto.CreateProductRequest;
import com.orderflow.product.dto.ProductQuery;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.dto.UpdateProductRequest;
import com.orderflow.product.repository.CategoryRepository;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.product.repository.ProductSpecifications;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product catalogue. Reads are cached in Redis; every write evicts the affected entries. */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryService inventoryService;

    public ProductService(ProductRepository products, CategoryRepository categories, InventoryService inventoryService) {
        this.products = products;
        this.categories = categories;
        this.inventoryService = inventoryService;
    }

    @Cacheable(cacheNames = CacheNames.PRODUCT, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return products.findWithCategoryById(id).map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Cacheable(cacheNames = CacheNames.PRODUCT_PAGES, key = "#query.cacheKey()")
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listActive(ProductQuery query) {
        Specification<Product> spec = Specification.allOf(ProductSpecifications.active(),
                ProductSpecifications.inCategory(query.categoryId()), ProductSpecifications.nameContains(query.q()));
        return PageResponse.from(products.findAll(spec, PageRequest.of(query.page(), query.size(), query.sort().sort())),
                ProductResponse::from);
    }

    /** Uncached, authoritative read used when pricing orders and rendering carts. */
    @Transactional(readOnly = true)
    public Map<Long, ProductResponse> getProducts(Collection<Long> ids) {
        return products.findWithCategoryByIdIn(ids).stream().map(ProductResponse::from)
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCT_PAGES, allEntries = true)
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = products.save(new Product(request.name().trim(), request.description(), request.price(),
                category(request.categoryId())));
        inventoryService.initialize(product.getId(), request.initialStock());
        return ProductResponse.from(product);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_PAGES, allEntries = true)})
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = products.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("Product", id));
        product.update(request.name().trim(), request.description(), request.price(), category(request.categoryId()),
                request.active());
        return ProductResponse.from(products.saveAndFlush(product));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_PAGES, allEntries = true)})
    @Transactional
    public void deactivate(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
        product.deactivate();
    }

    private Category category(Long categoryId) {
        return categories.findById(categoryId).orElseThrow(() -> new NotFoundException("Category", categoryId));
    }
}
