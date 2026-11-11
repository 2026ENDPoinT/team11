package com.team11.backend.users.dto;

import com.team11.backend.auth.dto.ConsentItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateConsentsRequest(@NotEmpty List<@Valid ConsentItem> consents) {
}
