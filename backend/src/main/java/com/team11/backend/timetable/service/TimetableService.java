package com.team11.backend.timetable.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.FieldViolation;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.config.SemesterProperties.SemesterConfig;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.common.domain.Source;
import com.team11.backend.timetable.dto.BulkReplaceTimetableRequest;
import com.team11.backend.timetable.dto.CreateTimetableEntryRequest;
import com.team11.backend.timetable.dto.SemesterListResponse;
import com.team11.backend.timetable.dto.SemesterListResponse.SemesterView;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.dto.TimetableResponse;
import com.team11.backend.timetable.dto.UpdateTimetableEntryRequest;
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
    private static final int MAX_SEMESTERS_RETURNED = 4;

    private final TimetableEntryRepository timetableEntryRepository;
    private final SemesterProperties semesterProperties;
    private final EverytimeClient everytimeClient;

    public TimetableService(TimetableEntryRepository timetableEntryRepository, SemesterProperties semesterProperties,
                             EverytimeClient everytimeClient) {
        this.timetableEntryRepository = timetableEntryRepository;
        this.semesterProperties = semesterProperties;
        this.everytimeClient = everytimeClient;
    }

    @Transactional(readOnly = true)
    public SemesterListResponse getSemesters() {
        LocalDate today = LocalDate.now();
        String currentCode = resolveCurrentSemesterCode(today);
        List<SemesterView> views = semesterProperties.semesters().stream()
                .sorted(Comparator.comparing(SemesterConfig::startDate).reversed())
                .limit(MAX_SEMESTERS_RETURNED)
                .map(c -> new SemesterView(c.code(), c.startDate(), c.endDate(), c.code().equals(currentCode)))
                .toList();
        return new SemesterListResponse(views);
    }

    @Transactional(readOnly = true)
    public TimetableResponse getTimetable(Long userId, String semester) {
        String resolved = semester != null ? semester : resolveCurrentSemesterCode(LocalDate.now());
        validateSemesterFormat(resolved);
        if (semester != null && semesterProperties.semesters().stream().noneMatch(c -> c.code().equals(semester))) {
            throw new ApiException(ErrorCode.SEMESTER_NOT_FOUND);
        }
        List<TimetableEntryView> entries = timetableEntryRepository
                .findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, resolved).stream()
                .map(TimetableEntryView::from)
                .toList();
        return new TimetableResponse(resolved, entries);
    }

    public TimetableEntryView createEntry(Long userId, CreateTimetableEntryRequest request) {
        validateSemesterFormat(request.semester());
        validateTimeRange(request.startTime(), request.endTime());
        checkOverlap(userId, request.semester(), request.dayOfWeek(), request.startTime(), request.endTime(), null);

        TimetableEntry entry = new TimetableEntry(userId, request.semester(), request.dayOfWeek(),
                request.startTime(), request.endTime(), request.courseName(), request.location(),
                Source.MANUAL, null);
        timetableEntryRepository.save(entry);
        return TimetableEntryView.from(entry);
    }

    public TimetableEntryView updateEntry(Long userId, Long entryId, UpdateTimetableEntryRequest request) {
        TimetableEntry entry = findOwnedEntry(userId, entryId);

        if (entry.getSource() != Source.MANUAL && request.changesReadonlyFields()) {
            throw new ApiException(ErrorCode.TIMETABLE_READONLY_FIELD);
        }

        DayOfWeek dayOfWeek = request.dayOfWeek() != null ? request.dayOfWeek() : entry.getDayOfWeek();
        LocalTime startTime = request.startTime() != null ? request.startTime() : entry.getStartTime();
        LocalTime endTime = request.endTime() != null ? request.endTime() : entry.getEndTime();
        String courseName = request.courseName() != null ? request.courseName() : entry.getCourseName();
        String location = request.location() != null ? request.location() : entry.getLocation();

        validateTimeRange(startTime, endTime);
        checkOverlap(userId, entry.getSemester(), dayOfWeek, startTime, endTime, entryId);

        entry.updateAll(dayOfWeek, startTime, endTime, courseName, location);
        return TimetableEntryView.from(entry);
    }

    public void deleteEntry(Long userId, Long entryId) {
        TimetableEntry entry = findOwnedEntry(userId, entryId);
        timetableEntryRepository.delete(entry);
    }

    public TimetableResponse bulkReplace(Long userId, String semester, BulkReplaceTimetableRequest request) {
        validateSemesterFormat(semester);
        if (request.source() == Source.ECLASS) {
            for (BulkReplaceTimetableRequest.Entry item : request.entries()) {
                if (item.externalId() == null || item.externalId().isBlank()) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            List.of(new FieldViolation("entries.externalId", "ECLASS 출처는 externalId가 필요해요.")));
                }
            }
        }
        for (BulkReplaceTimetableRequest.Entry item : request.entries()) {
            validateTimeRange(item.startTime(), item.endTime());
        }
        checkInternalOverlap(request.entries());

        List<TimetableEntry> existing = timetableEntryRepository
                .findByUserIdAndSemesterAndSource(userId, semester, request.source());
        timetableEntryRepository.deleteAll(existing);

        List<TimetableEntry> created = request.entries().stream()
                .map(item -> new TimetableEntry(userId, semester, item.dayOfWeek(), item.startTime(), item.endTime(),
                        item.courseName(), item.location(), request.source(), item.externalId()))
                .toList();
        timetableEntryRepository.saveAll(created);

        return getTimetable(userId, semester);
    }

    /** 에브리타임 공유 링크에서 수업을 가져와 해당 학기의 EVERYTIME 출처 수업을 통째로 교체한다. */
    public TimetableResponse importFromEverytime(Long userId, String semester, String everytimeUrl) {
        validateSemesterFormat(semester);
        List<EverytimeClient.Subject> subjects = everytimeClient.fetchSubjects(everytimeUrl);

        List<BulkReplaceTimetableRequest.Entry> entries = subjects.stream()
                .flatMap(subject -> subject.times().stream()
                        .map(time -> new BulkReplaceTimetableRequest.Entry(
                                subject.id(),
                                toDayOfWeek(time.day()),
                                toLocalTime(time.startUnit()),
                                toLocalTime(time.endUnit()),
                                truncate(subject.name(), 50),
                                truncate(time.place(), 30))))
                .toList();

        return bulkReplace(userId, semester, new BulkReplaceTimetableRequest(Source.EVERYTIME, entries));
    }

    private DayOfWeek toDayOfWeek(int day) {
        if (day < 0 || day >= DayOfWeek.values().length) {
            throw new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED);
        }
        return DayOfWeek.values()[day];
    }

    private LocalTime toLocalTime(int fiveMinuteUnit) {
        int minutes = EverytimeClient.toMinutesOfDay(fiveMinuteUnit);
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private TimetableEntry findOwnedEntry(Long userId, Long entryId) {
        TimetableEntry entry = timetableEntryRepository.findById(entryId)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMETABLE_ENTRY_NOT_FOUND));
        if (!entry.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return entry;
    }

    private void validateSemesterFormat(String semester) {
        if (semester == null || !SEMESTER_FORMAT.matcher(semester).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("semester", "yyyy-{1,2,S,W} 형식이어야 해요.")));
        }
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
    }

    private void checkOverlap(Long userId, String semester, DayOfWeek dayOfWeek, LocalTime startTime,
                               LocalTime endTime, Long excludeEntryId) {
        List<Map<String, Object>> conflicts = timetableEntryRepository
                .findByUserIdAndSemesterAndDayOfWeek(userId, semester, dayOfWeek).stream()
                .filter(e -> !e.getId().equals(excludeEntryId))
                .filter(e -> e.getStartTime().isBefore(endTime) && startTime.isBefore(e.getEndTime()))
                .map(e -> Map.<String, Object>of(
                        "entryId", e.getId(), "courseName", e.getCourseName(),
                        "startTime", e.getStartTime().toString(), "endTime", e.getEndTime().toString()))
                .toList();
        if (!conflicts.isEmpty()) {
            throw new ApiException(ErrorCode.TIMETABLE_OVERLAP, Map.of("conflicts", conflicts));
        }
    }

    private void checkInternalOverlap(List<BulkReplaceTimetableRequest.Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                BulkReplaceTimetableRequest.Entry a = entries.get(i);
                BulkReplaceTimetableRequest.Entry b = entries.get(j);
                boolean overlaps = a.dayOfWeek() == b.dayOfWeek()
                        && a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
                if (overlaps) {
                    throw new ApiException(ErrorCode.TIMETABLE_OVERLAP,
                            Map.of("conflicts", List.of(Map.of("courseName", a.courseName()),
                                    Map.of("courseName", b.courseName()))));
                }
            }
        }
    }

    private String resolveCurrentSemesterCode(LocalDate today) {
        return semesterProperties.semesters().stream()
                .filter(c -> !today.isBefore(c.startDate()) && !today.isAfter(c.endDate()))
                .findFirst()
                .or(() -> semesterProperties.semesters().stream()
                        .filter(c -> c.startDate().isAfter(today))
                        .min(Comparator.comparing(SemesterConfig::startDate)))
                .map(SemesterConfig::code)
                .orElse(null);
    }
}
