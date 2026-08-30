/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.dto.GalleryImageView;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.form.DownloadForm;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.service.download.DownloadCoordinator;
import dev.despical.tikfetch.service.LatestVideoCacheService;
import dev.despical.tikfetch.service.RateLimiterService;
import dev.despical.tikfetch.service.download.VideoDurationService;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DownloadedVideoRepository videoRepository;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final VideoViewMapper viewMapper;
    private final DownloadCoordinator downloadCoordinator;
    private final RateLimiterService rateLimiterService;
    private final LocalFileStorageService storageService;
    private final VideoDurationService videoDurationService;
    private final LatestVideoCacheService latestVideoCacheService;

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("downloadForm")) {
            model.addAttribute("downloadForm", new DownloadForm(""));
        }

        model.addAttribute("latestVideos", latestVideoCacheService.current());
        return "public/index";
    }

    @PostMapping("/download")
    public String download(
        @Valid @ModelAttribute("downloadForm") DownloadForm form,
        BindingResult bindingResult,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        String clientIP = rateLimiterService.getClientIP(request);

        if (!rateLimiterService.tryConsumeDownload(clientIP)) {
            bindingResult.reject("rateLimited", "Too many requests. Please wait a minute and try again.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.downloadForm", bindingResult);
            redirectAttributes.addFlashAttribute("downloadForm", form);
            return "redirect:/";
        }

        try {
            var downloadedVideo = downloadCoordinator.queue(form.url(), clientIP);
            return "redirect:/downloads/" + downloadedVideo.getId();
        } catch (UserFacingException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("downloadForm", form);
        }

        return "redirect:/";
    }

    @GetMapping("/downloads/{id}")
    public String downloadResult(@PathVariable Long id, Model model) {
        var video = videoRepository.findById(id)
            .orElseThrow(() -> new UserFacingException("That download is no longer available."));

        if (video.getStatus() != DownloadStatus.READY && video.getStatus() != DownloadStatus.SUCCESS) {
            model.addAttribute("downloadId", video.getId());
            model.addAttribute("downloadFailed", video.getStatus() == DownloadStatus.FAILED);
            model.addAttribute("downloadError", video.getErrorMessage());
            return "public/download-pending";
        }

        if (!model.containsAttribute("downloadForm")) {
            model.addAttribute("downloadForm", new DownloadForm(""));
        }

        model.addAttribute("video", viewMapper.toLatestView(ensureDuration(video)));
        model.addAttribute("galleryImages", galleryImages(video));
        model.addAttribute("latestVideos", latestVideoCacheService.current());
        return "public/download-result";
    }

    @GetMapping("/downloads/{id}/status")
    @ResponseBody
    public DownloadStatusResponse downloadStatus(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .orElseThrow(() -> new UserFacingException("That download is no longer available."));

        return switch (video.getStatus()) {
            case READY -> new DownloadStatusResponse("ready", "/downloads/" + id, null, false);
            case SUCCESS -> new DownloadStatusResponse("ready", "/downloads/" + id, null, true);
            case FAILED -> new DownloadStatusResponse("failed", null, video.getErrorMessage(), false);
            case PENDING, PROCESSING -> new DownloadStatusResponse("processing", null, null, false);
        };
    }

    private List<GalleryImageView> galleryImages(DownloadedVideo video) {
        return mediaItemRepository.findByVideoOrderByPositionIndexAsc(video)
            .stream()
            .map(item -> new GalleryImageView(
                item.getPositionIndex(),
                "/media/stream/" + video.getId() + "?position=" + item.getPositionIndex(),
                "/media/gallery/" + video.getId() + "/" + item.getPositionIndex(),
                viewMapper.humanReadableByteCount(item.getFileSize())
            ))
            .toList();
    }

    private DownloadedVideo ensureDuration(DownloadedVideo video) {
        if (video.getDurationSeconds() != null || video.getVideoPath() == null) {
            return video;
        }

        try {
            Long duration = videoDurationService.detectDurationSeconds(storageService.resolveStoredPath(video.getVideoPath()));

            if (duration != null && duration > 0) {
                video.setDurationSeconds(duration);
                return videoRepository.save(video);
            }
        } catch (RuntimeException _) {
            return video;
        }

        return video;
    }

    public record DownloadStatusResponse(String status, String resultUrl, String message, boolean mediaCached) {
    }
}
