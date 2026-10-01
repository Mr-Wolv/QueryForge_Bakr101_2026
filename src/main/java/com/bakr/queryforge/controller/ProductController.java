package com.bakr.queryforge.controller;

import com.bakr.queryforge.service.KeysetPage;
import com.bakr.queryforge.service.PageResponse;
import com.bakr.queryforge.service.ProductRow;
import com.bakr.queryforge.service.ProductService;
import com.bakr.queryforge.service.SearchParams;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Workload A — point lookup. */
    @GetMapping("/{id}")
    public ProductRow getOne(@PathVariable long id) {
        return service.get(id);
    }

    /** Workloads B/C — filtered search with OFFSET pagination. */
    @GetMapping
    public PageResponse<ProductRow> search(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        SearchParams p = new SearchParams(category, status, minPrice, maxPrice, sort, parseDir(dir), page, size);
        return service.search(p);
    }

    /** Workload D — keyset (cursor) pagination over the same filters (sort=createdAt only). */
    @GetMapping("/keyset")
    public KeysetPage<ProductRow> searchKeyset(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer size) {
        SearchParams p = new SearchParams(category, status, minPrice, maxPrice, sort, parseDir(dir), 0,
                SearchParams.MAX_SIZE);
        Integer effectiveLimit = limit != null ? limit : size;
        return service.searchKeyset(p, cursor, effectiveLimit);
    }

    private static boolean parseDir(String dir) {
        return switch (dir == null ? "desc" : dir.toLowerCase()) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new IllegalArgumentException("dir must be asc or desc");
        };
    }
}
