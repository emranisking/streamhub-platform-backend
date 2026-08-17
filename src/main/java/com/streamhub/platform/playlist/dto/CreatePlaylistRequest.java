package com.streamhub.platform.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePlaylistRequest {
    @NotBlank(message = "title is required")
    private String title;
}
