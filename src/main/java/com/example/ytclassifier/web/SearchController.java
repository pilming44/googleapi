package com.example.ytclassifier.web;

import com.example.ytclassifier.exception.SearchException;
import com.example.ytclassifier.service.SearchService;
import com.example.ytclassifier.web.dto.SearchForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 검색 실행(요구사항 1~3, 9). 성공/실패 모두 PRG로 처리하여 새로고침 재전송을 막고,
 * 성공 시 미분류 탭으로 이동한다(요구사항 9).
 */
@Controller
public class SearchController {

    private final SearchService searchService;
    private final String defaultApiKey;

    public SearchController(SearchService searchService,
                            @Value("${youtube.api.key:}") String defaultApiKey) {
        this.searchService = searchService;
        this.defaultApiKey = defaultApiKey;
    }

    @PostMapping("/search")
    public String search(@ModelAttribute("searchForm") SearchForm form, HttpSession session,
                         RedirectAttributes redirectAttributes) {
        // 입력값을 세션에 보관 → 검색 후에도(그리고 탭 이동 후에도) 폼이 초기화되지 않는다.
        session.setAttribute(SearchForm.SESSION_KEY, form);
        String apiKey = StringUtils.hasText(form.getApiKey()) ? form.getApiKey() : defaultApiKey;
        try {
            searchService.runSearch(form, apiKey);
        } catch (SearchException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/videos?status=UNCLASSIFIED";
        }
        return "redirect:/videos?status=UNCLASSIFIED";
    }
}
