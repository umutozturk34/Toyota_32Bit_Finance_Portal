package com.finance.backend.service;

import com.finance.backend.dto.response.NewsArticleDetailResponse;
import com.finance.backend.dto.response.NewsArticleResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.exception.BadRequestException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.NewsResponseMapper;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import java.util.List;
import java.util.Map;
import com.finance.backend.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private final NewsArticleRepository articleRepository;
    private final NewsCacheService newsCacheService;
    private final NewsResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public PagedResponse<NewsArticleResponse> search(String category, String searchTerm,
                                                      String sortBy, String direction,
                                                      int page, int size) {
        Specification<NewsArticle> spec = buildSpecification(category, searchTerm);

        PageRequest pageRequest = PageRequest.of(page, size, buildSort(sortBy, direction));
        Page<NewsArticle> result = articleRepository.findAll(spec, pageRequest);

        return PagedResponse.of(
                responseMapper.toResponses(result.getContent()),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NewsArticleDetailResponse getById(Long id) {
        NewsArticle article = newsCacheService.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News article not found: " + id));
        return responseMapper.toDetailResponse(article);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryCounts() {
        return articleRepository.countByCategory().stream()
                .map(row -> Map.<String, Object>of("type", row[0].toString(), "count", row[1]))
                .toList();
    }

    private Specification<NewsArticle> buildSpecification(String category, String searchTerm) {
        Specification<NewsArticle> spec = (root, query, cb) -> cb.conjunction();

        if (category != null && !category.isBlank()) {
            try {
                NewsCategory newsCategory = NewsCategory.valueOf(category.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), newsCategory));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid news category: " + category);
            }
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }

        return spec;
    }

    private Sort buildSort(String sortBy, String direction) {
        String field = "title".equals(sortBy) ? "title" : "publishedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}
