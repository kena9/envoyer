/**
 * Olly Threads — Service Worker
 *
 * Handles incoming push notifications from the server.
 * Registered by push.js on every page load.
 */

self.addEventListener('push', function (event) {
    let data = { title: 'Olly Threads', body: 'New drop or deal!' };

    if (event.data) {
        try {
            data = event.data.json();
        } catch (e) {
            data.body = event.data.text();
        }
    }

    const options = {
        body:    data.body  || '',
        icon:    data.icon  || '/img/icon-192.png',
        badge:   '/img/icon-192.png',
        vibrate: [100, 50, 100],
        data:    { url: data.url || '/' }
    };

    event.waitUntil(
        self.registration.showNotification(data.title, options)
    );
});

self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    const url = (event.notification.data && event.notification.data.url) ? event.notification.data.url : '/';
    event.waitUntil(
        clients.openWindow(url)
    );
});
