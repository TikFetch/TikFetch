import './styles.css';

const downloadForms = document.querySelectorAll('[data-download-form]');
const inputWraps = document.querySelectorAll('[data-clipboard-input]');
const scrollTopButton = document.querySelector('[data-scroll-top]');
const galleryViewers = document.querySelectorAll('[data-gallery-viewer]');

downloadForms.forEach((downloadForm) => {
    downloadForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const button = downloadForm.querySelector('[data-submit-button]');
        const progress = downloadForm.querySelector('[data-download-progress]');
        const progressLabel = downloadForm.querySelector('[data-progress-label]');
        const error = downloadForm.querySelector('[data-download-error]');
        const urlInput = downloadForm.querySelector('input[type="url"]');
        const rawUrl = urlInput?.value.trim() ?? '';

        const showError = (message) => {
            if (!error) return;
            error.textContent = message;
            error.hidden = false;
        };

        if (progress) {
            progress.classList.remove('is-active');
            progress.setAttribute('aria-hidden', 'true');
        }
        if (error) {
            error.hidden = true;
            error.textContent = '';
        }

        if (!rawUrl) {
            showError('Please paste a TikTok video or photo URL first.');
            urlInput?.focus();
            return;
        }

        if (!isTikTokUrl(rawUrl)) {
            showError('Please enter a valid TikTok video or photo URL.');
            urlInput?.focus();
            return;
        }

        if (button) {
            button.disabled = true;
            button.textContent = 'Preparing download';
        }
        if (progressLabel) {
            progressLabel.textContent = rawUrl.includes('/photo/') ? 'Downloading photo' : 'Downloading video';
        }
        if (progress) {
            progress.classList.add('is-active');
            progress.setAttribute('aria-hidden', 'false');
        }

        try {
            const response = await fetch(downloadForm.action, {
                method: downloadForm.method || 'POST',
                body: new FormData(downloadForm),
                credentials: 'same-origin',
                redirect: 'follow',
            });

            window.location.assign(response.url || '/');
        } catch {
            downloadForm.submit();
        }
    });
});

const isTikTokUrl = (value) => {
    try {
        const url = new URL(value.trim());
        return ['tiktok.com', 'www.tiktok.com', 'm.tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'].includes(url.hostname.toLowerCase());
    } catch {
        return false;
    }
};

const showButton = (button) => {
    if (!button) return;
    button.hidden = false;
    requestAnimationFrame(() => button.classList.add('is-visible'));
};

const hideButton = (button, afterHide) => {
    if (!button || button.hidden) return;
    button.classList.remove('is-visible');
    window.setTimeout(() => {
        button.hidden = true;
        afterHide?.();
    }, 180);
};

inputWraps.forEach((wrap) => {
    const input = wrap.querySelector('input[type="url"]');
    const pasteButton = wrap.querySelector('[data-paste-tiktok]');
    const clearButton = wrap.querySelector('[data-clear-input]');
    let clipboardTikTokUrl = '';

    const syncButtons = () => {
        if (input?.value.trim()) {
            hideButton(pasteButton);
            showButton(clearButton);
            return;
        }

        hideButton(clearButton);
        if (clipboardTikTokUrl) {
            showButton(pasteButton);
        } else {
            hideButton(pasteButton);
        }
    };

    const refreshClipboard = async () => {
        if (!navigator.clipboard?.readText) {
            syncButtons();
            return;
        }

        try {
            const clipboardText = await navigator.clipboard.readText();
            clipboardTikTokUrl = isTikTokUrl(clipboardText) ? clipboardText.trim() : '';
        } catch {
            clipboardTikTokUrl = '';
        }

        syncButtons();
    };

    pasteButton?.addEventListener('click', async () => {
        if (!input) return;
        if (!clipboardTikTokUrl) {
            await refreshClipboard();
        }
        if (!clipboardTikTokUrl) return;

        input.value = clipboardTikTokUrl;
        input.focus();
        hideButton(pasteButton, () => showButton(clearButton));
    });

    clearButton?.addEventListener('click', () => {
        if (!input) return;
        input.value = '';
        input.focus();
        hideButton(clearButton, () => {
            if (clipboardTikTokUrl) {
                showButton(pasteButton);
            }
        });
    });

    input?.addEventListener('focus', refreshClipboard);
    input?.addEventListener('click', refreshClipboard);
    input?.addEventListener('input', syncButtons);
    input?.addEventListener('paste', () => window.setTimeout(syncButtons, 0));
    refreshClipboard();
});

if (scrollTopButton) {
    const updateScrollButton = () => {
        scrollTopButton.classList.toggle('is-visible', window.scrollY > 420);
    };

    window.addEventListener('scroll', updateScrollButton, {passive: true});
    scrollTopButton.addEventListener('click', () => {
        window.scrollTo({top: 0, behavior: 'smooth'});
    });
    updateScrollButton();
}

galleryViewers.forEach((viewer) => {
    const image = viewer.querySelector('[data-gallery-image]');
    const previous = viewer.querySelector('[data-gallery-prev]');
    const next = viewer.querySelector('[data-gallery-next]');
    const counter = viewer.querySelector('[data-gallery-counter]');
    const download = document.querySelector('[data-gallery-download]');
    const size = document.querySelector('[data-gallery-size]');
    const items = Array.from(viewer.querySelectorAll('[data-gallery-items] span'))
        .map((item) => ({
            url: item.dataset.url,
            download: item.dataset.download,
            size: item.dataset.size,
        }))
        .filter((item) => item.url && item.download);
    let index = 0;

    const render = () => {
        if (!image || items.length === 0) return;
        image.src = items[index].url;
        if (download) {
            download.href = items[index].download;
        }
        if (counter) {
            counter.textContent = `${index + 1} / ${items.length}`;
        }
        if (size && items[index].size) {
            size.textContent = items[index].size;
        }
    };

    previous?.addEventListener('click', () => {
        index = (index - 1 + items.length) % items.length;
        render();
    });

    next?.addEventListener('click', () => {
        index = (index + 1) % items.length;
        render();
    });

    render();
});
