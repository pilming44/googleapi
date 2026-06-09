package com.example.ytclassifier.repository;

import com.example.ytclassifier.domain.SearchRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchRunRepository extends JpaRepository<SearchRun, Long> {

    /** 검색 실행 이력을 최신순으로 조회(요구사항 10). */
    List<SearchRun> findAllByOrderByStartedAtDesc();

    /** 같은 검색어 + 길이 옵션의 최근 실행을 조회(이어서 수집 기준점 산정에 사용). */
    SearchRun findTopByQueryAndVideoDurationOrderByStartedAtDescIdDesc(String query, String videoDuration);
}
