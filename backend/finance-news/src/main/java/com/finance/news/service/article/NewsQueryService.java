package com.finance.news.service.article;

import com.finance.news.service.article.NewsCacheService;
import com.finance.news.service.article.NewsQueryService;


import com.finance.shared.dto.response.GroupCount;
import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.shared.util.EnumParser;
import com.finance.shared.util.LikeSearchSpec;
import com.finance.news.mapper.NewsResponseMapper;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import java.util.List;
import com.finance.news.repository.NewsArticleRepository;
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
                .orElseThrow(() -> new ResourceNotFoundException("error.news.articleNotFound", id));
        return responseMapper.toDetailResponse(article);
    }

    @Transactional(readOnly = true)
    public List<GroupCount> getCategoryCounts() {
        return articleRepository.countByCategory().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }

    private Specification<NewsArticle> buildSpecification(String category, String searchTerm) {
        Specification<NewsArticle> spec = (root, query, cb) -> cb.conjunction();

        NewsCategory newsCategory = category == null || category.isBlank()
                ? null
                : EnumParser.parseOrBadRequest(NewsCategory.class, category.toUpperCase(), "enum.field.newsCategory");
        if (newsCategory != null) {
            NewsCategory fixed = newsCategory;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), fixed));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContainsAllTokensUnaccent(root, cb, searchTerm, "title", "description", "content"));
        }

        return spec;
    }

    private Sort buildSort(String sortBy, String direction) {
        String field = "title".equals(sortBy) ? "title" : "publishedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}
