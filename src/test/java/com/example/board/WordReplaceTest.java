package com.example.board;

import com.example.board.entity.Post;
import com.example.board.repository.PostRepository;
import com.example.board.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
class WordReplaceTest {

    @Autowired
    PostService service;

    @Autowired
    PostRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Long save(String title, String content) {
        Post p = new Post();
        p.setTitle(title);
        p.setName("kim");
        p.setContent(content);
        return repository.save(p).getId();
    }

    @Test
    void 대소문자가_달라_실제로_바뀌지_않은_글은_변경_건수에_포함되지_않는다() {
        Long lower = save("kfc 치킨", "맛있다");
        Long upper = save("KFC 치킨", "맛있다");

        // 검색은 대소문자를 무시하므로 둘 다 조회된다
        List<Long> found = service.searchIdsByWord("kfc");
        assertThat(found).containsExactly(lower, upper);

        // 치환은 대소문자를 구분하므로 소문자 글만 실제로 바뀐다
        int changed = service.replaceWord(found, "kfc", "bhc");

        assertThat(changed).isEqualTo(1);
        assertThat(service.get(lower).getTitle()).isEqualTo("bhc 치킨");
        assertThat(service.get(upper).getTitle()).isEqualTo("KFC 치킨");
    }

    @Test
    void 제목이나_내용이_바뀐_글은_모두_변경_건수에_포함된다() {
        Long a = save("kfc", "내용");
        Long b = save("제목", "오늘 kfc 먹음");

        int changed = service.replaceWord(List.of(a, b), "kfc", "bhc");

        assertThat(changed).isEqualTo(2);
        assertThat(service.get(a).getTitle()).isEqualTo("bhc");
        assertThat(service.get(b).getContent()).isEqualTo("오늘 bhc 먹음");
    }

    @Test
    void 도중에_실패하면_앞서_처리한_글도_모두_롤백된다() {
        Long ok = save("kfc", "내용");
        // 치환 후 제목이 100자를 넘어 저장 검증에 실패하는 글
        Long tooLong = save("kfc" + "a".repeat(95), "내용");

        assertThatThrownBy(() -> service.replaceWord(List.of(ok, tooLong), "kfc", "bhcbhcbhc"))
                .isInstanceOf(Exception.class);

        assertThat(service.get(ok).getTitle()).isEqualTo("kfc");
        assertThat(service.get(tooLong).getTitle()).startsWith("kfc");
    }

    @Test
    void 없는_id는_건너뛰고_건수에_포함되지_않는다() {
        Long a = save("kfc", "내용");

        int changed = service.replaceWord(List.of(a, 99999L), "kfc", "bhc");

        assertThat(changed).isEqualTo(1);
    }
}
