package dev.despical.tikfetch.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record DownloadForm(
    @NotBlank(message = "Paste a TikTok video or photo URL first.")
    @Size(max = 2048, message = "That URL is too long.")
    String url
) {
}
