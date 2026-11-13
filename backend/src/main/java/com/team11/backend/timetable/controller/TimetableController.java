package com.team11.backend.timetable.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.timetable.dto.EverytimeImportRequest;
import com.team11.backend.timetable.dto.SemesterListResponse;
import com.team11.backend.timetable.dto.TimetableEntryRequest;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.dto.TimetableResponse;
import com.team11.backend.timetable.service.TimetableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping("/semesters")
    public ApiResponse<SemesterListResponse> getSemesters() {
        return ApiResponse.success(timetableService.getSemesters());
    }

    /** semester 생략 시 현재 학기(방학이면 다음 학기). */
    @GetMapping("/timetable")
    public ApiResponse<TimetableResponse> getTimetable(@AuthenticationPrincipal Long userId,
                                                       @RequestParam(required = false) String semester) {
        return ApiResponse.success(timetableService.getTimetable(userId, semester));
    }

    @PostMapping("/timetable/entries")
    public ResponseEntity<ApiResponse<TimetableEntryView>> createEntry(@AuthenticationPrincipal Long userId,
                                                                         @Valid @RequestBody TimetableEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(timetableService.createEntry(userId, request)));
    }

    @PutMapping("/timetable/entries/{entryId}")
    public ApiResponse<TimetableEntryView> updateEntry(@AuthenticationPrincipal Long userId, @PathVariable Long entryId,
                                                       @Valid @RequestBody TimetableEntryRequest request) {
        return ApiResponse.success(timetableService.updateEntry(userId, entryId, request));
    }

    @DeleteMapping("/timetable/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal Long userId, @PathVariable Long entryId) {
        timetableService.deleteEntry(userId, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/timetable/import/everytime")
    public ApiResponse<TimetableResponse> importFromEverytime(@AuthenticationPrincipal Long userId,
                                                              @RequestParam(required = false) String semester,
                                                              @Valid @RequestBody EverytimeImportRequest request) {
        return ApiResponse.success(timetableService.importFromEverytime(userId, semester, request.url()));
    }
}
