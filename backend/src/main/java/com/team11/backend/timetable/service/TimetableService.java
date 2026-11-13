package com.team11.backend.timetable.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.Kst;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.config.SemesterProperties.SemesterConfig;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.Source;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.dto.SemesterListResponse;
import com.team11.backend.timetable.dto.SemesterListResponse.SemesterView;
import com.team11.backend.timetable.dto.TimetableEntryRequest;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.dto.TimetableResponse;
import com.team11.backend.timetable.external.EverytimeClient;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimetableService {

    private static final Pattern SEMESTER_FORMAT = Pattern.compile("^\\d{4}-(1|2|S|W)$");

    private final TimetableEntryRepository repository;
    private final SemesterProperties semesterProperties;
    private final EverytimeClient everytimeClient;

    public TimetableService(TimetableEntryRepository repository, SemesterProperties semesterProperties,
                             EverytimeClient everytimeClient) {
        this.repository = repository;
        this.semesterProperties = semesterProperties;
        this.everytimeClient = everytimeClient;
    }

    @Transactional(readOnly = true)
    public SemesterListResponse getSemesters() {
        String current = currentSemesterCode();
        return new SemesterListResponse(semesterProperties.semesters().stream()
                .sorted(Comparator.comparing(SemesterConfig::startDate).reversed())
                .map(c -> new SemesterView(c.code(), c.startDate(), c.endDate(), c.code().equals(current)))
                .toList());
    }

    @Transactional(readOnly = true)
    public TimetableResponse getTimetable(Long userId, String semester) {
        String resolved = resolveSemester(semester);
        List<TimetableEntryView> entries = repository
                .findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, resolved).stream()
                .map(TimetableEntryView::from).toList();
        return new TimetableResponse(resolved, entries);
    }

    public TimetableEntryView createEntry(Long userId, TimetableEntryRequest request) {
        validateSemesterFormat(request.semester());
        validateTimeRange(request.startTime(), request.endTime());
        checkOverlap(userId, request, null);
        TimetableEntry entry = new TimetableEntry(userId, request.semester(), request.dayOfWeek(), request.startTime(),
                request.endTime(), request.courseName().trim(), blankToNull(request.room()), Source.MANUAL, null);
        return TimetableEntryView.from(repository.save(entry));
    }

    public TimetableEntryView updateEntry(Long userId, Long entryId, TimetableEntryRequest request) {
        TimetableEntry entry = findOwned(userId, entryId);
        validateSemesterFormat(request.semester());
        validateTimeRange(request.startTime(), request.endTime());
        checkOverlap(userId, request, entryId);
        entry.update(request.semester(), request.dayOfWeek(), request.startTime(), request.endTime(),
                request.courseName().trim(), blankToNull(request.room()));
        return TimetableEntryView.from(entry);
    }

    public void deleteEntry(Long userId, Long entryId) {
        repository.delete(findOwned(userId, entryId));
    }

    /** 공유 링크의 수업을 가져와 그 학기의 EVERYTIME 출처 수업을 통째로 교체한다(수동 입력 수업은 유지). */
    public TimetableResponse importFromEverytime(Long userId, String semester, String url) {
        String resolved = resolveSemester(semester);
        List<EverytimeClient.Subject> subjects = everytimeClient.fetchSubjects(url);

        repository.deleteAll(repository.findByUserIdAndSemesterAndSource(userId, resolved, Source.EVERYTIME));
        List<TimetableEntry> entries = subjects.stream()
                .flatMap(subject -> subject.times().stream().map(time -> new TimetableEntry(userId, resolved,
                        toDayOfWeek(time.day()), toLocalTime(time.startUnit()), toLocalTime(time.endUnit()),
                        truncate(subject.name(), 50), truncate(time.place(), 50), Source.EVERYTIME, subject.id())))
                .filter(e -> e.getStartTime().isBefore(e.getEndTime()))
                .toList();
        repository.saveAll(entries);
        return getTimetable(userId, resolved);
    }

    /** 플랜 계산용: 그 날짜(KST)에 실제로 열리는 수업. 방학이면 빈 목록. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> entriesOn(Long userId, LocalDate date) {
        return semesterProperties.containing(date)
                .map(c -> repository.findByUserIdAndSemesterAndDayOfWeek(userId, c.code(),
                        DayOfWeek.of(date.getDayOfWeek())))
                .orElse(List.of());
    }

    /** AI 스냅샷용: 그 날짜가 속한 학기의 수업 전체와 학기 정보. 방학이면 빈 목록. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> entriesForSemesterOf(Long userId, LocalDate date) {
        return semesterProperties.containing(date)
                .map(c -> repository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, c.code()))
                .orElse(List.of());
    }

    public SemesterProperties semesters() {
        return semesterProperties;
    }

    private String resolveSemester(String semester) {
        if (semester == null) {
            String current = currentSemesterCode();
            if (current == null) {
                throw new ApiException(ErrorCode.SEMESTER_NOT_FOUND);
            }
            return current;
        }
        validateSemesterFormat(semester);
        if (semesterProperties.find(semester).isEmpty()) {
            throw new ApiException(ErrorCode.SEMESTER_NOT_FOUND);
        }
        return semester;
    }

    private String currentSemesterCode() {
        return semesterProperties.resolveFor(Kst.today()).map(SemesterConfig::code).orElse(null);
    }

    private TimetableEntry findOwned(Long userId, Long entryId) {
        TimetableEntry entry = repository.findById(entryId)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMETABLE_ENTRY_NOT_FOUND));
        if (!entry.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return entry;
    }

    private void checkOverlap(Long userId, TimetableEntryRequest r, Long excludeEntryId) {
        List<Map<String, Object>> conflicts = repository
                .findByUserIdAndSemesterAndDayOfWeek(userId, r.semester(), r.dayOfWeek()).stream()
                .filter(e -> !e.getId().equals(excludeEntryId))
                .filter(e -> e.getStartTime().isBefore(r.endTime()) && r.startTime().isBefore(e.getEndTime()))
                .map(e -> Map.<String, Object>of("entryId", e.getId(), "courseName", e.getCourseName(),
                        "startTime", e.getStartTime().toString(), "endTime", e.getEndTime().toString()))
                .toList();
        if (!conflicts.isEmpty()) {
            throw new ApiException(ErrorCode.TIMETABLE_OVERLAP, Map.of("conflicts", conflicts));
        }
    }

    private static void validateSemesterFormat(String semester) {
        if (semester == null || !SEMESTER_FORMAT.matcher(semester).matches()) {
            throw ApiException.invalid("semester", "yyyy-{1,2,S,W} 형식이어야 해요.");
        }
    }

    private static void validateTimeRange(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
    }

    private static DayOfWeek toDayOfWeek(int day) {
        if (day < 0 || day >= DayOfWeek.values().length) {
            throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED);
        }
        return DayOfWeek.values()[day];
    }

    private static LocalTime toLocalTime(int fiveMinuteUnit) {
        int minutes = EverytimeClient.toMinutesOfDay(fiveMinuteUnit);
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    private static String truncate(String value, int max) {
        String v = blankToNull(value);
        return v == null ? null : (v.length() > max ? v.substring(0, max) : v);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
