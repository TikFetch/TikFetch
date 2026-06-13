package dev.despical.tikfetch.service.download;

import dev.despical.tikfetch.validation.ValidatedTikTokUrl;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface TikTokDownloadService {

    DownloadedTikTokVideo download(ValidatedTikTokUrl url);
}
