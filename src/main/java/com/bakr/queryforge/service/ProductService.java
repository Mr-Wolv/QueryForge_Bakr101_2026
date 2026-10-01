package com.bakr.queryforge.service;

import com.bakr.queryforge.entity.Product;
import com.bakr.queryforge.repo.ProductKeysetRepository;
import com.bakr.queryforge.repo.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductKeysetRepository keyset;

    public ProductService(ProductRepository products, ProductKeysetRepository keyset) {
        this.products = products;
        this.keyset = keyset;
    }

    /** Workload A — point lookup. */
    public ProductRow get(long id) {
        return products.findById(id).map(ProductService::toRow)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    /** Workloads B/C — filtered search with OFFSET pagination (Stage 0: plain specification query). */
    public PageResponse<ProductRow> search(SearchParams p) {
        Sort sort = Sort.by(p.asc() ? Sort.Direction.ASC : Sort.Direction.DESC, p.sortField());
        Pageable pageable = PageRequest.of(p.page(), p.size(), sort);
        Specification<Product> spec = ProductSpecs.search(p.categoryId(), p.status(), p.minPrice(), p.maxPrice());
        Page<Product> page = products.findAll(spec, pageable);
        List<ProductRow> content = page.getContent().stream().map(ProductService::toRow).toList();
        boolean hasNext = (long) (p.page() + 1) * p.size() < page.getTotalElements();
        return new PageResponse<>(content, p.page(), p.size(), page.getTotalElements(), hasNext);
    }

    /** Workload D — keyset (cursor) pagination over the same filters. */
    public KeysetPage<ProductRow> searchKeyset(SearchParams p, String cursor, Integer limit) {
        int lim = limit == null ? p.size() : limit;
        if (lim < 1 || lim > SearchParams.MAX_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + SearchParams.MAX_SIZE);
        }
        ProductKeysetRepository.KeysetQuery q = new ProductKeysetRepository.KeysetQuery(
                p.categoryId(), p.status(), p.minPrice(), p.maxPrice(),
                ProductKeysetRepository.validatedColumn(p.sortField()), p.asc());

        Instant cursorValue = null;
        long cursorId = 0;
        if (cursor != null && !cursor.isBlank()) {
            Cursor.Decoded d = Cursor.decode(cursor);
            cursorValue = d.createdAt();
            cursorId = d.id();
        }

        List<ProductRow> rows = keyset.find(q, cursorValue, cursorId, lim + 1);
        boolean hasNext = rows.size() > lim;
        if (hasNext) {
            rows = rows.subList(0, lim);
        }
        String nextCursor = null;
        if (hasNext) {
            ProductRow last = rows.get(rows.size() - 1);
            nextCursor = Cursor.encode(last.createdAt(), last.id());
        }
        return new KeysetPage<>(rows, nextCursor);
    }

    static ProductRow toRow(Product product) {
        return new ProductRow(product.getId(), product.getSku(), product.getName(), product.getCategoryId(),
                product.getPrice(), product.getStockQuantity(), product.getStatus(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
