package com.team11.backend.timetable.repository;

import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.domain.TimetableSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    List<TimetableEntry> findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(Long userId, String semester);

    List<TimetableEntry> findByUserIdAndSemesterAndDayOfWeek(Long userId, String semester, DayOfWeek dayOfWeek);

    List<TimetableEntry> findByUserIdAndSemesterAndSource(Long userId, String semester, TimetableSource source);
}
