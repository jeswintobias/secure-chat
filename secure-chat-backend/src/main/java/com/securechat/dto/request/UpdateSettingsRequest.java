package com.securechat.dto.request;

import lombok.Data;

@Data
public class UpdateSettingsRequest {
    private String lastSeenPrivacy;
    private Boolean readReceiptsEnabled;
}
