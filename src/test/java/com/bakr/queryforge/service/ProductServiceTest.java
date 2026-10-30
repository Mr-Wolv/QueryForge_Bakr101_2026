package com.bakr.queryforge.service;

import com.bakr.queryforge.entity.Product;
import com.bakr.queryforge.repo.ProductKeysetRepository;
import com.bakr.queryforge.repo.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductKeysetRepository keyset = mock(ProductKeysetRepository.class);
    private final ProductService service = new ProductService(products, keyset);

    @Test
    void getMapsFoundProductToRow() {
        Product product = product(7L, Instant.parse("2024-06-01T12:34:56Z"));
        when(products.findById(7L)).thenReturn(Optional.of(product));

        ProductRow row = service.get(7L);

        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.sku()).isEqualTo("SKU-7");
        assertThat(row.name()).isEqualTo("Product 7");
        assertThat(row.categoryId()).isEqualTo(3L);
        assertThat(row.price()).isEqualByComparingTo("12.50");
        assertThat(row.stockQuantity()).isEqualTo(8);
        assertThat(row.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getThrowsNotFoundForMissingProduct() {
        when(products.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product 404 not found");
    }

    @Test
    void searchAppliesRequestedSortAndBuildsNextPageResponse() {
        SearchParams params = new SearchParams(null, null, null, null, "price", true, 0, 2);
        var pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "price"));
        Product product = product(1L, Instant.parse("2024-06-01T12:34:56Z"));
        when(products.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 3));

        PageResponse<ProductRow> response = service.search(params);

        assertThat(response.content()).extracting(ProductRow::id).containsExactly(1L);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        verify(products).findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable));
    }

    @Test
    void searchMarksLastPageWithoutNext() {
        SearchParams params = new SearchParams(null, null, null, null, null, false, 1, 2);
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(products.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product(1L, Instant.EPOCH)), pageable, 3));

        PageResponse<ProductRow> response = service.search(params);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void keysetSearchTrimsLookaheadAndEncodesLastReturnedRow() {
        SearchParams params = new SearchParams(4L, "ACTIVE", BigDecimal.ONE,
                BigDecimal.TEN, null, true, 0, 2);
        Instant firstTime = Instant.parse("2024-06-01T12:34:56Z");
        Instant lastTime = firstTime.plusSeconds(1);
        ProductRow first = row(10L, firstTime);
        ProductRow last = row(11L, lastTime);
        when(keyset.find(any(), eq(firstTime), eq(8L), eq(3)))
                .thenReturn(List.of(first, last, row(12L, lastTime.plusSeconds(1))));

        KeysetPage<ProductRow> response = service.searchKeyset(
                params, Cursor.encode(firstTime, 8L), 2);

        assertThat(response.content()).containsExactly(first, last);
        assertThat(response.nextCursor()).isEqualTo(Cursor.encode(lastTime, 11L));
        verify(keyset).find(
                eq(new ProductKeysetRepository.KeysetQuery(
                        4L, "ACTIVE", BigDecimal.ONE, BigDecimal.TEN, "created_at", true)),
                eq(firstTime), eq(8L), eq(3));
    }

    @Test
    void keysetSearchUsesDefaultSizeAndReturnsNoCursorWhenThereIsNoLookahead() {
        SearchParams params = new SearchParams(null, null, null, null, null, false, 0, 2);
        when(keyset.find(any(), eq(null), eq(0L), eq(3)))
                .thenReturn(List.of(row(1L, Instant.EPOCH), row(2L, Instant.EPOCH.plusSeconds(1))));

        KeysetPage<ProductRow> response = service.searchKeyset(params, null, null);

        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void keysetSearchRejectsUnsupportedSortAndLimitsBeforeQuerying() {
        SearchParams priceSort = new SearchParams(null, null, null, null, "price", false, 0, 2);
        SearchParams createdAtSort = new SearchParams(null, null, null, null, null, false, 0, 2);

        assertThatThrownBy(() -> service.searchKeyset(priceSort, null, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort=createdAt");
        assertThatThrownBy(() -> service.searchKeyset(createdAtSort, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be between");
        assertThatThrownBy(() -> service.searchKeyset(createdAtSort, null, SearchParams.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be between");

        verifyNoInteractions(keyset);
    }

    private static Product product(long id, Instant createdAt) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("Product " + id);
        product.setCategoryId(3L);
        product.setPrice(new BigDecimal("12.50"));
        product.setStockQuantity(8);
        product.setStatus("ACTIVE");
        product.setCreatedAt(createdAt);
        product.setUpdatedAt(createdAt.plusSeconds(1));
        return product;
    }

    private static ProductRow row(long id, Instant createdAt) {
        return new ProductRow(id, "SKU-" + id, "Product " + id, 3L,
                new BigDecimal("12.50"), 8, "ACTIVE", createdAt, createdAt.plusSeconds(1));
    }
}
