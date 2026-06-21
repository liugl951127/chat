package com.fin.chat.verify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRef {
    private String id;
    private String title;
    private String summary;
    private String url;
    private long ts;
}
