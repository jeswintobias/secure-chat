package com.securechat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for creating a new group conversation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 2, max = 100, message = "Group name must be between 2 and 100 characters")
    private String name;

    /**
     * Optional referral code for controlling group access.
     * If not provided, the system will auto-generate one.
     */
    @Size(max = 64, message = "Referral code must not exceed 64 characters")
    private String referralCode;
}
