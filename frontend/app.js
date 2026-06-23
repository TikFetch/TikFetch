import './styles.css';

const downloadForms = document.querySelectorAll('[data-download-form]');
const inputWraps = document.querySelectorAll('[data-clipboard-input]');
const scrollTopButton = document.querySelector('[data-scroll-top]');
const galleryViewers = document.querySelectorAll('[data-gallery-viewer]');
const passkeyLoginButton = document.querySelector('[data-passkey-login]');
const passkeyRegisterButton = document.querySelector('[data-passkey-register]');
const adminNavScrolls = document.querySelectorAll('.admin-nav-scroll');

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

    if (button.hideTimeout) {
        window.clearTimeout(button.hideTimeout);
        button.hideTimeout = null;
    }

    button.closest('[data-clipboard-input]')?.classList.add('has-input-action');
    button.hidden = false;

    requestAnimationFrame(() => button.classList.add('is-visible'));
};

const hideButton = (button, afterHide) => {
    if (!button || button.hidden) return;

    if (button.hideTimeout) {
        window.clearTimeout(button.hideTimeout);
    }

    button.classList.remove('is-visible');
    button.hideTimeout = window.setTimeout(() => {
        button.hideTimeout = null;
        button.hidden = true;

        const wrap = button.closest('[data-clipboard-input]');
        const hasVisibleAction = wrap?.querySelector('.clipboard-input-button:not([hidden])');

        if (!hasVisibleAction) {
            wrap?.classList.remove('has-input-action');
        }

        afterHide?.();
    }, 180);
};

inputWraps.forEach((wrap) => {
    const input = wrap.querySelector('input[type="url"]');
    const pasteButton = wrap.querySelector('[data-paste-tiktok]');
    const clearButton = wrap.querySelector('[data-clear-input]');
    const error = wrap.closest('form')?.querySelector('[data-download-error]');

    const clearError = () => {
        if (!error) return;

        error.hidden = true;
        error.textContent = '';
    };

    const syncButtons = () => {
        if (input?.value.trim()) {
            hideButton(pasteButton);
            showButton(clearButton);
            return;
        }

        hideButton(clearButton);
        showButton(pasteButton);
    };

    pasteButton?.addEventListener('click', async () => {
        if (!input) return;
        clearError();

        try {
            const clipboardText = await navigator.clipboard?.readText?.();

            if (clipboardText) {
                input.value = clipboardText.trim();
            }
        } catch {
            // Keep the paste button visible when clipboard access is denied.
        }

        input.focus();
        syncButtons();
    });

    clearButton?.addEventListener('click', () => {
        if (!input) return;

        clearError();

        input.value = '';
        input.focus();

        syncButtons();
    });

    input?.addEventListener('input', syncButtons);
    input?.addEventListener('paste', () => window.setTimeout(syncButtons, 0));

    syncButtons();
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

adminNavScrolls.forEach((nav) => {
    let scrollTimeout;

    nav.addEventListener('scroll', () => {
        nav.classList.add('is-scrolling');
        window.clearTimeout(scrollTimeout);

        scrollTimeout = window.setTimeout(() => {
            nav.classList.remove('is-scrolling');
        }, 700);
    }, {passive: true});
});

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
    let renderToken = 0;
    const preloadedImages = new Map();

    const preload = (item) => {
        if (!item || preloadedImages.has(item.url)) {
            return preloadedImages.get(item?.url);
        }

        const preloadImage = new Image();
        preloadImage.decoding = 'async';
        preloadImage.src = item.url;

        preloadedImages.set(item.url, preloadImage);
        return preloadImage;
    };

    const preloadAround = () => {
        if (items.length < 2) return;

        preload(items[(index + 1) % items.length]);
        preload(items[(index - 1 + items.length) % items.length]);
    };

    const apply = (item) => {
        image.src = item.url;

        if (download) {
            download.href = item.download;
        }

        if (counter) {
            counter.textContent = `${index + 1} / ${items.length}`;
        }

        if (size && item.size) {
            size.textContent = item.size;
        }
    };

    const render = () => {
        if (!image || items.length === 0) return;

        const token = ++renderToken;
        const item = items[index];
        const preloadImage = preload(item);

        preloadAround();

        const applyIfCurrent = () => {
            if (token === renderToken) {
                apply(item);
            }
        };

        if (!preloadImage || preloadImage.complete) {
            applyIfCurrent();
            return;
        }

        preloadImage.addEventListener('load', applyIfCurrent, {once: true});
        preloadImage.addEventListener('error', applyIfCurrent, {once: true});
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
    items.forEach(preload);
});

const supportsPasskeys = () => window.PublicKeyCredential && navigator.credentials;

const base64UrlToBuffer = (value) => {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i += 1) {
        bytes[i] = binary.charCodeAt(i);
    }

    return bytes.buffer;
};

const bufferToBase64Url = (buffer) => {
    if (!buffer) return undefined;

    const bytes = new Uint8Array(buffer);
    let binary = '';

    bytes.forEach((byte) => {
        binary += String.fromCharCode(byte);
    });

    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
};

const publicKeyOptions = (options) => options.publicKey || options;

const parseCreationOptions = (options) => {
    const publicKey = publicKeyOptions(options);

    if (PublicKeyCredential.parseCreationOptionsFromJSON) {
        return PublicKeyCredential.parseCreationOptionsFromJSON(publicKey);
    }

    return {
        ...publicKey,
        challenge: base64UrlToBuffer(publicKey.challenge),
        user: {
            ...publicKey.user,
            id: base64UrlToBuffer(publicKey.user.id),
        },
        excludeCredentials: publicKey.excludeCredentials?.map((credential) => ({
            ...credential,
            id: base64UrlToBuffer(credential.id),
        })),
    };
};

const parseRequestOptions = (options) => {
    const publicKey = publicKeyOptions(options);

    if (PublicKeyCredential.parseRequestOptionsFromJSON) {
        return PublicKeyCredential.parseRequestOptionsFromJSON(publicKey);
    }

    return {
        ...publicKey,
        challenge: base64UrlToBuffer(publicKey.challenge),
        allowCredentials: publicKey.allowCredentials?.map((credential) => ({
            ...credential,
            id: base64UrlToBuffer(credential.id),
        })),
    };
};

const credentialToJson = (credential) => {
    if (credential.toJSON) {
        return credential.toJSON();
    }

    const response = credential.response;
    return {
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        type: credential.type,
        authenticatorAttachment: credential.authenticatorAttachment,
        clientExtensionResults: credential.getClientExtensionResults(),
        response: {
            attestationObject: bufferToBase64Url(response.attestationObject),
            authenticatorData: bufferToBase64Url(response.authenticatorData),
            clientDataJSON: bufferToBase64Url(response.clientDataJSON),
            signature: bufferToBase64Url(response.signature),
            userHandle: bufferToBase64Url(response.userHandle),
        },
    };
};

const postJson = async (url, csrfToken, body = {}) => {
    const response = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': csrfToken,
        },
        body: JSON.stringify(body),
    });

    if (!response.ok) {
        let message = 'The passkey request could not be completed.';

        try {
            const data = await response.json();
            message = data.message || message;
        } catch {
            // Keep the generic message when the server does not return JSON.
        }

        throw new Error(message);
    }

    return response.json();
};

const passkeyErrorMessage = (error, fallback) => {
    if (error?.name === 'NotAllowedError') {
        return 'Passkey verification was cancelled or timed out.';
    }

    if (error?.name === 'AbortError') {
        return 'Passkey verification was cancelled.';
    }

    return error?.message || fallback;
};

const showPasskeyMessage = (selector, message, success = false) => {
    const element = document.querySelector(selector);
    if (!element) return;

    element.textContent = message;
    element.classList.remove('hidden', 'text-red-600', 'text-emerald-700', 'bg-red-50', 'bg-emerald-50');
    element.classList.add(success ? 'text-emerald-700' : 'text-red-600');

    if (selector.includes('register')) {
        element.classList.add(success ? 'bg-emerald-50' : 'bg-red-50');
    }
};

passkeyLoginButton?.addEventListener('click', async () => {
    if (!supportsPasskeys()) {
        showPasskeyMessage('[data-passkey-login-message]', 'This browser does not support passkeys yet.');
        return;
    }

    passkeyLoginButton.disabled = true;
    const originalText = passkeyLoginButton.textContent;
    passkeyLoginButton.textContent = 'Waiting for passkey';

    try {
        const options = await postJson(passkeyLoginButton.dataset.optionsUrl, passkeyLoginButton.dataset.csrfToken);
        const publicKey = parseRequestOptions(options);
        const credential = await navigator.credentials.get({publicKey});
        const result = await postJson(passkeyLoginButton.dataset.finishUrl, passkeyLoginButton.dataset.csrfToken, {
            credential: credentialToJson(credential),
        });

        window.location.assign(result.redirect || '/admin');
    } catch (error) {
        showPasskeyMessage(
            '[data-passkey-login-message]',
            passkeyErrorMessage(error, 'Passkey sign-in failed.')
        );
    } finally {
        passkeyLoginButton.disabled = false;
        passkeyLoginButton.textContent = originalText;
    }
});

passkeyRegisterButton?.addEventListener('click', async () => {
    if (!supportsPasskeys()) {
        showPasskeyMessage('[data-passkey-register-message]', 'This browser does not support passkeys yet.');
        return;
    }

    const labelInput = document.getElementById(passkeyRegisterButton.dataset.labelInput);
    const label = labelInput?.value?.trim() || 'Passkey';
    const originalText = passkeyRegisterButton.textContent;
    passkeyRegisterButton.disabled = true;
    passkeyRegisterButton.textContent = 'Waiting for passkey';

    try {
        const options = await postJson(passkeyRegisterButton.dataset.optionsUrl, passkeyRegisterButton.dataset.csrfToken, {label});
        const publicKey = parseCreationOptions(options);
        const credential = await navigator.credentials.create({publicKey});
        const result = await postJson(passkeyRegisterButton.dataset.finishUrl, passkeyRegisterButton.dataset.csrfToken, {
            label,
            credential: credentialToJson(credential),
        });

        showPasskeyMessage('[data-passkey-register-message]', 'Passkey added successfully.', true);
        window.location.assign(result.redirect || '/admin/security');
    } catch (error) {
        showPasskeyMessage(
            '[data-passkey-register-message]',
            passkeyErrorMessage(error, 'Could not add this passkey.')
        );
    } finally {
        passkeyRegisterButton.disabled = false;
        passkeyRegisterButton.textContent = originalText;
    }
});
