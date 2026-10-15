package com.hiddenwhite.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRegistrationRequest(
        @NotBlank @Size(max = 30) String displayName,
        @NotBlank @Pattern(regexp = "^[0-9+() -]{6,20}$") String phone,
        @NotBlank @Size(min = 6, max = 72) String password) {
}
