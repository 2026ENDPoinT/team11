package com.team11.backend.timetable.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.timetable.dto.BulkReplaceTimetableRequest;
import com.team11.backend.timetable.dto.CreateTimetableEntryRequest;
import com.team11.backend.timetable.dto.EverytimeImportRequest;
import com.team11.backend.timetable.dto.SemesterListResponse;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.dto.TimetableResponse;
import com.team11.backend.timetable.dto.UpdateTimetableEntryRequest;
import com.team11.backend.timetable.service.TimetableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<ApiResponse<SemesterListResponse>> getSemesters() {
        return ResponseEntity.ok(ApiResponse.success(timetableService.getSemesters()));
    }

    @GetMapping("/timetable")
    public ResponseEntity<ApiResponse<TimetableResponse>> getTimetable(@AuthenticationPrincipal Long userId,
                                                                         @RequestParam(required = false) String semester) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.getTimetable(userId, semester)));
    }

    @PostMapping("/timetable/entries")
    public ResponseEntity<ApiResponse<TimetableEntryView>> createEntry(@AuthenticationPrincipal Long userId,
                                                                         @Valid @RequestBody CreateTimetableEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(timetableService.createEntry(userId, request)));
    }

    @PatchMapping("/timetable/entries/{entryId}")
    public ResponseEntity<ApiResponse<TimetableEntryView>> updateEntry(@AuthenticationPrincipal Long userId,
                                                                         @PathVariable Long entryId,
                                                                         @Valid @RequestBody UpdateTimetableEntryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.updateEntry(userId, entryId, request)));
    }

    @DeleteMapping("/timetable/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal Long userId, @PathVariable Long entryId) {
        timetableService.deleteEntry(userId, entryId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/timetable")
    public ResponseEntity<ApiResponse<TimetableResponse>> bulkReplace(@AuthenticationPrincipal Long userId,
                                                                        @RequestParam String semester,
                                                                        @Valid @RequestBody BulkReplaceTimetableRequest request) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.bulkReplace(userId, semester, request)));
    }

    @PostMapping("/timetable/import/everytime")
    public ResponseEntity<ApiResponse<TimetableResponse>> importFromEverytime(@AuthenticationPrincipal Long userId,
                                                                                 @RequestParam String semester,
                                                                                 @Valid @RequestBody EverytimeImportRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(timetableService.importFromEverytime(userId, semester, request.url())));
    }
}
