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

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.dto.GalleryImageView;
import dev.despical.tikfetch.dto.LatestVideoView;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.form.DownloadForm;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.service.download.DownloadCoordinator;
import dev.despical.tikfetch.service.RateLimiterService;
import dev.despical.tikfetch.service.download.VideoDurationService;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AppProperties properties;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final VideoViewMapper viewMapper;
    private final DownloadCoordinator downloadCoordinator;
    private final RateLimiterService rateLimiterService;
    private final LocalFileStorageService storageService;
    private final VideoDurationService videoDurationService;

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("downloadForm")) {
            model.addAttribute("downloadForm", new DownloadForm(""));
        }

        model.addAttribute("latestVideos", latestVideos());
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
            var downloadedVideo = downloadCoordinator.download(form.url(), clientIP);
            redirectAttributes.addFlashAttribute("successMessage", "Video downloaded successfully.");
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
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .orElseThrow(() -> new UserFacingException("That download is no longer available."));

        if (!model.containsAttribute("downloadForm")) {
            model.addAttribute("downloadForm", new DownloadForm(""));
        }

        model.addAttribute("video", viewMapper.toLatestView(ensureDuration(video)));
        model.addAttribute("galleryImages", galleryImages(video));
        model.addAttribute("latestVideos", latestVideos());
        return "public/download-result";
    }

    private List<LatestVideoView> latestVideos() {
        return videoRepository.findByStatusOrderByDownloadedAtDesc(
                DownloadStatus.SUCCESS,
                PageRequest.of(0, properties.latestVideosLimit())
            )
            .stream()
            .map(this::ensureDuration)
            .map(viewMapper::toLatestView)
            .toList();
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
}
