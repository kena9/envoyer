/**
 * Olly Threads — Browser Push Subscription
 *
 * Registers the service worker, fetches the VAPID public key,
 * and subscribes the browser to push notifications.
 *
 * The bell button (id="push-bell") is shown in the layout.
 * Clicking it requests permission and subscribes.
 */

(function () {
    'use strict';

    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
        // Browser doesn't support push — hide the bell
        hideBell();
        return;
    }

    const bell = document.getElementById('push-bell');
    if (!bell) return;

    // Register SW immediately
    navigator.serviceWorker.register('/sw.js').then(function (reg) {
        // Check existing subscription state
        reg.pushManager.getSubscription().then(function (sub) {
            if (sub) {
                setSubscribed(true);
            } else {
                setSubscribed(false);
            }
        });

        bell.addEventListener('click', function () {
            if (bell.dataset.subscribed === 'true') {
                unsubscribe(reg);
            } else {
                subscribe(reg);
            }
        });
    }).catch(function (err) {
        console.warn('[Push] SW registration failed:', err);
        hideBell();
    });

    // ── Subscribe ──────────────────────────────────────────────────────────────

    function subscribe(reg) {
        if (Notification.permission === 'denied') {
            alert('Notifications are blocked. Please enable them in your browser settings.');
            return;
        }

        fetch('/push/vapid-public-key')
            .then(r => r.json())
            .then(function (data) {
                return reg.pushManager.subscribe({
                    userVisibleOnly:      true,
                    applicationServerKey: urlBase64ToUint8Array(data.publicKey)
                });
            })
            .then(function (sub) {
                const key  = sub.getKey('p256dh');
                const auth = sub.getKey('auth');

                const body = new URLSearchParams({
                    endpoint: sub.endpoint,
                    p256dh:   arrayBufferToBase64(key),
                    auth:     arrayBufferToBase64(auth)
                });

                const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
                const headers    = { 'Content-Type': 'application/x-www-form-urlencoded' };
                if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

                return fetch('/push/subscribe', { method: 'POST', headers, body: body.toString() });
            })
            .then(function () {
                setSubscribed(true);
            })
            .catch(function (err) {
                console.warn('[Push] Subscribe failed:', err);
            });
    }

    // ── Unsubscribe ────────────────────────────────────────────────────────────

    function unsubscribe(reg) {
        reg.pushManager.getSubscription().then(function (sub) {
            if (!sub) { setSubscribed(false); return; }

            const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
            const headers    = { 'Content-Type': 'application/x-www-form-urlencoded' };
            if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

            fetch('/push/subscribe?' + new URLSearchParams({ endpoint: sub.endpoint }), {
                method: 'DELETE',
                headers
            }).then(function () {
                return sub.unsubscribe();
            }).then(function () {
                setSubscribed(false);
            }).catch(console.warn);
        });
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    function setSubscribed(yes) {
        if (!bell) return;
        bell.dataset.subscribed = yes ? 'true' : 'false';
        bell.title = yes ? 'Turn off notifications' : 'Get drop & deal notifications';
        bell.querySelector('.bell-active').style.display   = yes ? 'block' : 'none';
        bell.querySelector('.bell-inactive').style.display = yes ? 'none'  : 'block';
    }

    function hideBell() {
        const b = document.getElementById('push-bell');
        if (b) b.style.display = 'none';
    }

    // ── Crypto helpers ─────────────────────────────────────────────────────────

    function urlBase64ToUint8Array(base64String) {
        const padding  = '='.repeat((4 - base64String.length % 4) % 4);
        const base64   = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
        const rawData  = atob(base64);
        const arr      = new Uint8Array(rawData.length);
        for (let i = 0; i < rawData.length; i++) arr[i] = rawData.charCodeAt(i);
        return arr;
    }

    function arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let   str   = '';
        for (const b of bytes) str += String.fromCharCode(b);
        return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }
})();
