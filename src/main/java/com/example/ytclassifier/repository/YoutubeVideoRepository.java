package com.example.ytclassifier.repository;

import com.example.ytclassifier.domain.YoutubeVideo;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * video_id 기준 영상 저장소. existsById / getReferenceById(상속) 로 중복 판별·FK 참조에 사용한다.
 */
public interface YoutubeVideoRepository extends JpaRepository<YoutubeVideo, String> {
}
