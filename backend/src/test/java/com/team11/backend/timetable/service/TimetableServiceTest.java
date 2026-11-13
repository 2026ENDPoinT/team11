package com.team11.backend.timetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.domain.Source;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.external.EverytimeClient;
import com.team11.backend.timetable.external.EverytimeClient.Subject;
import com.team11.backend.timetable.external.EverytimeClient.Time;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    @Mock
    private TimetableEntryRepository timetableEntryRepository;
    @Mock
    private SemesterProperties semesterProperties;
    @Mock
    private EverytimeClient everytimeClient;

    private TimetableService service;

    @BeforeEach
    void setUp() {
        service = new TimetableService(timetableEntryRepository, semesterProperties, everytimeClient);
    }

    @Test
    void importFromEverytime_convertsSubjectsAndReplacesOnlyEverytimeSource() {
        when(semesterProperties.semesters()).thenReturn(
                List.of(new SemesterProperties.SemesterConfig("2026-2", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 20))));
        when(timetableEntryRepository.findByUserIdAndSemesterAndSource(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(timetableEntryRepository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        String longName = "가".repeat(60);
        String longPlace = "나".repeat(40);
        List<Subject> subjects = List.of(
                new Subject("8096439", "오픈소스소프트웨어", List.of(new Time(0, 108, 120, "미래관-319"))),
                new Subject("8096454", longName, List.of(new Time(2, 180, 192, longPlace))));
        when(everytimeClient.fetchSubjects("https://everytime.kr/@abc")).thenReturn(subjects);

        service.importFromEverytime(1L, "2026-2", "https://everytime.kr/@abc");

        ArgumentCaptor<List<TimetableEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(timetableEntryRepository).saveAll(captor.capture());
        List<TimetableEntry> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        TimetableEntry first = saved.get(0);
        assertThat(first.getExternalId()).isEqualTo("8096439");
        assertThat(first.getDayOfWeek()).isEqualTo(DayOfWeek.MON);
        assertThat(first.getStartTime().toString()).isEqualTo("09:00");
        assertThat(first.getEndTime().toString()).isEqualTo("10:00");
        assertThat(first.getSource()).isEqualTo(Source.EVERYTIME);

        TimetableEntry second = saved.get(1);
        assertThat(second.getDayOfWeek()).isEqualTo(DayOfWeek.WED);
        assertThat(second.getCourseName()).hasSize(50); // 50자로 잘림
        assertThat(second.getLocation()).hasSize(30); // 30자로 잘림

        verify(timetableEntryRepository).findByUserIdAndSemesterAndSource(1L, "2026-2", Source.EVERYTIME);
    }

    @Test
    void importFromEverytime_whenFetchFails_propagatesError() {
        when(everytimeClient.fetchSubjects(any())).thenThrow(new ApiException(ErrorCode.EVERYTIME_IMPORT_FAILED));

        assertThatThrownBy(() -> service.importFromEverytime(1L, "2026-2", "https://everytime.kr/@abc"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVERYTIME_IMPORT_FAILED);
    }

    @Test
    void importFromEverytime_whenDayIndexOutOfRange_throws() {
        when(everytimeClient.fetchSubjects(any())).thenReturn(
                List.of(new Subject("1", "이상한수업", List.of(new Time(7, 0, 12, "")))));

        assertThatThrownBy(() -> service.importFromEverytime(1L, "2026-2", "https://everytime.kr/@abc"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVERYTIME_IMPORT_FAILED);
    }

    @Test
    void importFromEverytime_whenSemesterFormatInvalid_throwsBeforeFetching() {
        assertThatThrownBy(() -> service.importFromEverytime(1L, "bad-semester", "https://everytime.kr/@abc"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
