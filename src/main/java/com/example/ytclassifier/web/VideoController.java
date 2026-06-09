package com.example.ytclassifier.web;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.repository.SearchRunRepository;
import com.example.ytclassifier.service.VideoClassificationService;
import com.example.ytclassifier.web.dto.SearchForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 화면 라우팅: 상태별 탭(요구사항 7) + 검색 이력(요구사항 10).
 */
@Controller
public class VideoController {

    private final VideoClassificationService classificationService;
    private final SearchRunRepository searchRunRepository;
    private final String defaultApiKey;
    private final int maxTarget;

    public VideoController(VideoClassificationService classificationService,
                           SearchRunRepository searchRunRepository,
                           @Value("${youtube.api.key:}") String defaultApiKey,
                           @Value("${youtube.collect.max-target:5000}") int maxTarget) {
        this.classificationService = classificationService;
        this.searchRunRepository = searchRunRepository;
        this.defaultApiKey = defaultApiKey;
        this.maxTarget = maxTarget;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/videos?status=UNCLASSIFIED";
    }

    @GetMapping("/videos")
    public String videos(@RequestParam(defaultValue = "UNCLASSIFIED") String status,
                         HttpSession session, Model model) {
        ClassificationStatus current = parseStatus(status);

        // 직전 검색 입력값을 세션에서 복원(없으면 기본 폼 + Key 프리필) → 검색/탭 이동 후에도 폼이 초기화되지 않음.
        SearchForm form = (SearchForm) session.getAttribute(SearchForm.SESSION_KEY);
        if (form == null) {
            form = new SearchForm();
            form.setApiKey(defaultApiKey);
        }
        model.addAttribute("searchForm", form);

        model.addAttribute("cards", classificationService.cards(current));
        model.addAttribute("counts", classificationService.counts());
        model.addAttribute("currentStatus", current);
        model.addAttribute("statuses", ClassificationStatus.values());
        model.addAttribute("maxTarget", maxTarget);
        return "videos";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("runs", searchRunRepository.findAllByOrderByStartedAtDesc());
        return "history";
    }

    private ClassificationStatus parseStatus(String status) {
        try {
            return ClassificationStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ClassificationStatus.UNCLASSIFIED;
        }
    }
}
