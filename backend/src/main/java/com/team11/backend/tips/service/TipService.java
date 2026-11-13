package com.team11.backend.tips.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Notion TIP-01(오늘의 팁)을 그대로 만들지 않고, 홈 화면 캐릭터 말풍선 한 줄로 단순화한 버전.
 * 팁 목록 조회(TIP-02)·피드백(TIP-03) 화면이 없어서 category/source는 API로 내보내지 않고 선택에만 쓴다.
 * 같은 날 같은 사용자는 같은 팁이 나오도록 (userId, 날짜) 해시로 고정 — 별도 캐시 테이블 없음.
 */
@Service
public class TipService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    private enum Situation {
        EXAM, LATE_NIGHT, GENERAL
    }

    private record Candidate(long id, Situation situation, String text) {
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate(1, Situation.EXAM, "시험이 머지않았어요. 밤새우기보다 짧은 낮잠으로 집중력을 지켜보세요."),
            new Candidate(2, Situation.EXAM, "시험 전날엔 평소보다 조금 더 자는 게 기억 정리에 도움이 돼요."),
            new Candidate(3, Situation.LATE_NIGHT, "오늘은 잠자리에 드는 시간이 늦어요. 취침 1시간 전엔 화면 밝기를 낮춰 보세요."),
            new Candidate(4, Situation.LATE_NIGHT, "늦게 자는 날엔 카페인을 평소보다 더 일찍 끊는 게 좋아요."),
            new Candidate(5, Situation.GENERAL, "자기 전 1시간은 화면보다 조명을 낮춘 공간이 잠드는 데 도움이 돼요."),
            new Candidate(6, Situation.GENERAL, "주말에도 기상 시간을 비슷하게 유지하면 평일 컨디션이 좋아져요."),
            new Candidate(7, Situation.GENERAL, "낮잠은 20분 안팎, 오후 이른 시간에 짧게 자는 편이 좋아요.")
    );

    public Tip getTodayTip(Long userId, boolean examSoon, boolean lateNight) {
        Situation situation = examSoon ? Situation.EXAM : lateNight ? Situation.LATE_NIGHT : Situation.GENERAL;
        List<Candidate> pool = CANDIDATES.stream().filter(c -> c.situation() == situation).toList();
        int index = Math.floorMod(Objects.hash(userId, LocalDate.now(ASIA_SEOUL)), pool.size());
        Candidate chosen = pool.get(index);
        return new Tip(chosen.id(), chosen.text());
    }

    public record Tip(long id, String text) {
    }
}
