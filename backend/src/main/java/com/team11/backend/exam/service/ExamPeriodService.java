package com.team11.backend.exam.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.repository.EventRepository;
import com.team11.backend.exam.domain.ExamPeriod;
import com.team11.backend.exam.domain.ExamPeriodExam;
import com.team11.backend.exam.domain.ExamPeriodStatus;
import com.team11.backend.exam.dto.CreateExamPeriodRequest;
import com.team11.backend.exam.dto.CreateExamPeriodRequest.Constraints;
import com.team11.backend.exam.dto.CreateExamPeriodRequest.ExamEntry;
import com.team11.backend.exam.dto.ExamPeriodListResponse;
import com.team11.backend.exam.dto.ExamPeriodView;
import com.team11.backend.exam.dto.UpdateExamPeriodRequest;
import com.team11.backend.exam.repository.ExamPeriodRepository;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExamPeriodService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ABSOLUTE_MIN_SLEEP_MIN = 240;
    private static final long MAX_SPAN_DAYS = 30;

    private final ExamPeriodRepository examPeriodRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public ExamPeriodService(ExamPeriodRepository examPeriodRepository, EventRepository eventRepository,
                              UserRepository userRepository) {
        this.examPeriodRepository = examPeriodRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    public ExamPeriodView createExamPeriod(Long userId, CreateExamPeriodRequest request) {
        validateDateRange(request.from(), request.to());
        checkOverlap(userId, request.from(), request.to(), null);

        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        Constraints constraints = request.constraints();
        int minSleepMin = resolveMinSleepMin(constraints, user.getMinSleepMin());
        Integer dailyStudyHoursMax = constraints != null ? constraints.dailyStudyHoursMax() : null;
        boolean includeNaps = constraints == null || constraints.includeNapsOrDefault();

        ExamPeriod examPeriod = new ExamPeriod(userId, request.name(), request.from(), request.to(),
                dailyStudyHoursMax, minSleepMin, includeNaps);
        Map<Long, Event> eventsById = resolveAndValidateExams(userId, request.exams(), request.from(), request.to());
        examPeriod.replaceExams(toExamPeriodExams(request.exams()));
        examPeriodRepository.save(examPeriod);
        return ExamPeriodView.of(examPeriod, eventsById);
    }

    @Transactional(readOnly = true)
    public ExamPeriodListResponse getExamPeriods(Long userId, ExamPeriodStatus status) {
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        List<ExamPeriodView.Summary> summaries = examPeriodRepository.findByUserIdOrderByFromDateDesc(userId).stream()
                .map(period -> ExamPeriodView.Summary.of(period, today))
                .filter(summary -> status == null || summary.status() == status)
                .toList();
        return new ExamPeriodListResponse(summaries);
    }

    @Transactional(readOnly = true)
    public ExamPeriodView getExamPeriod(Long userId, Long examPeriodId) {
        ExamPeriod examPeriod = findOwnedExamPeriod(userId, examPeriodId);
        return ExamPeriodView.of(examPeriod, loadEventsById(examPeriod.getExams()));
    }

    public ExamPeriodView updateExamPeriod(Long userId, Long examPeriodId, UpdateExamPeriodRequest request) {
        ExamPeriod examPeriod = findOwnedExamPeriod(userId, examPeriodId);

        LocalDate from = request.from() != null ? request.from() : examPeriod.getFromDate();
        LocalDate to = request.to() != null ? request.to() : examPeriod.getToDate();
        validateDateRange(from, to);
        checkOverlap(userId, from, to, examPeriodId);

        String name = request.name() != null ? request.name() : examPeriod.getName();
        Constraints constraints = request.constraints();
        int minSleepMin = constraints != null
                ? resolveMinSleepMin(constraints, examPeriod.getMinSleepMin())
                : examPeriod.getMinSleepMin();
        Integer dailyStudyHoursMax = constraints != null
                ? constraints.dailyStudyHoursMax()
                : examPeriod.getDailyStudyHoursMax();
        boolean includeNaps = constraints != null ? constraints.includeNapsOrDefault() : examPeriod.isIncludeNaps();

        List<ExamEntry> exams = request.exams() != null
                ? request.exams()
                : examPeriod.getExams().stream()
                        .map(e -> new ExamEntry(e.getEventId(), e.getDifficulty(), e.getStudyHoursNeeded()))
                        .toList();
        Map<Long, Event> eventsById = resolveAndValidateExams(userId, exams, from, to);

        examPeriod.update(name, from, to, dailyStudyHoursMax, minSleepMin, includeNaps);
        if (request.exams() != null) {
            examPeriod.replaceExams(toExamPeriodExams(exams));
        }
        return ExamPeriodView.of(examPeriod, eventsById);
    }

    public void deleteExamPeriod(Long userId, Long examPeriodId) {
        examPeriodRepository.delete(findOwnedExamPeriod(userId, examPeriodId));
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void checkOverlap(Long userId, LocalDate from, LocalDate to, Long excludeExamPeriodId) {
        boolean overlaps = examPeriodRepository.findByUserIdOrderByFromDateDesc(userId).stream()
                .filter(period -> !period.getId().equals(excludeExamPeriodId))
                .anyMatch(period -> period.overlaps(from, to));
        if (overlaps) {
            throw new ApiException(ErrorCode.EXAM_PERIOD_OVERLAP);
        }
    }

    private int resolveMinSleepMin(Constraints constraints, int fallback) {
        if (constraints == null || constraints.minSleepMin() == null) {
            return fallback;
        }
        if (constraints.minSleepMin() < ABSOLUTE_MIN_SLEEP_MIN) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return constraints.minSleepMin();
    }

    /** exams[].eventId는 본인 소유의 EXAM 일정이고 기간(from~to) 안에 있어야 한다. */
    private Map<Long, Event> resolveAndValidateExams(Long userId, List<ExamEntry> exams, LocalDate from,
                                                       LocalDate to) {
        Map<Long, Event> eventsById = eventRepository.findAllById(exams.stream().map(ExamEntry::eventId).toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));

        for (ExamEntry entry : exams) {
            Event event = eventsById.get(entry.eventId());
            if (event == null || event.getType() != EventType.EXAM) {
                throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
            }
            if (!event.isOwnedBy(userId)) {
                throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
            }
            LocalDate examDate = event.getStartAt().toLocalDate();
            if (examDate.isBefore(from) || examDate.isAfter(to)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
        }
        return eventsById;
    }

    private Map<Long, Event> loadEventsById(List<ExamPeriodExam> exams) {
        List<Long> eventIds = exams.stream().map(ExamPeriodExam::getEventId).toList();
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
    }

    private List<ExamPeriodExam> toExamPeriodExams(List<ExamEntry> exams) {
        return exams.stream()
                .map(e -> new ExamPeriodExam(e.eventId(), e.difficulty(), e.studyHoursNeeded()))
                .toList();
    }

    private ExamPeriod findOwnedExamPeriod(Long userId, Long examPeriodId) {
        ExamPeriod examPeriod = examPeriodRepository.findById(examPeriodId)
                .orElseThrow(() -> new ApiException(ErrorCode.EXAM_PERIOD_NOT_FOUND));
        if (!examPeriod.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return examPeriod;
    }
}
