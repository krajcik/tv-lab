/** TV Lab: remember the last torrent selected for each series and season. */
(function () {
    'use strict';

    var STORAGE_KEY = 'tv_lab_torrent_resume_v1';
    var MAX_MOVIES = 200;
    var session = null;

    function read() {
        var value = Lampa.Storage.get(STORAGE_KEY, {});
        return value && typeof value === 'object' ? value : {};
    }

    function write(value) {
        var keys = Object.keys(value);
        if (keys.length > MAX_MOVIES) {
            keys.sort(function (a, b) {
                return (value[a].updated || 0) - (value[b].updated || 0);
            });
            keys.slice(0, keys.length - MAX_MOVIES).forEach(function (key) {
                delete value[key];
            });
        }
        Lampa.Storage.set(STORAGE_KEY, value);
    }

    function activeMovie() {
        var activity = Lampa.Activity && Lampa.Activity.active ? Lampa.Activity.active() : null;
        return activity && activity.movie ? activity.movie : null;
    }

    function movieKey(movie) {
        if (!movie || movie.id === undefined || movie.id === null) return null;
        return String(movie.id) + ':' + (movie.name || movie.original_name ? 'tv' : 'movie');
    }

    function isSeries(movie) {
        return !!(movie && (movie.name || movie.original_name || movie.number_of_seasons));
    }

    function seasonsFor(element, movie) {
        if (!isSeries(movie)) return ['movie'];

        var general = element && element.general ? element.general : {};
        var seasons = Array.isArray(general.seasons) ? general.seasons.slice() : [];
        if (!seasons.length && general.season !== undefined && general.season !== null) {
            seasons = String(general.season).split('-').map(function (part) {
                return parseInt(part, 10);
            });
        }
        seasons = seasons.filter(function (season) {
            return Number.isFinite(Number(season)) && Number(season) > 0;
        }).map(function (season) {
            return String(parseInt(season, 10));
        });

        return seasons.length ? seasons : ['all'];
    }

    function selectedSeason(movie) {
        if (!isSeries(movie) || !Lampa.Storage.cache) return null;

        var all = Lampa.Storage.cache('torrents_filter_data', 500, {});
        var data = all && all[movieKey(movie)];
        var value = data && data.season;
        if (Array.isArray(value) && value.length === 1) value = value[0];
        return value ? String(value) : null;
    }

    function preferredHash(movie) {
        var key = movieKey(movie);
        var value = read()[key];
        if (!value) return null;

        var season = selectedSeason(movie);
        if (season && value.seasons && value.seasons[season]) return value.seasons[season].hash;
        return value.last && value.last.hash ? value.last.hash : null;
    }

    function remember(movie, element) {
        var key = movieKey(movie);
        if (!key || !element || !element.hash) return;

        var state = read();
        var value = state[key] || {seasons: {}};
        var entry = {
            hash: String(element.hash),
            link: element.MagnetUri || element.Link || '',
            title: element.Title || element.title || '',
            updated: Date.now()
        };

        value.last = entry;
        value.updated = entry.updated;
        value.seasons = value.seasons || {};
        seasonsFor(element, movie).forEach(function (season) {
            value.seasons[season] = entry;
        });
        state[key] = value;
        write(state);
    }

    function onTorrent(event) {
        var movie = activeMovie();
        if (!movie || !movie.id) return;

        if (event.type === 'onenter') {
            remember(movie, event.element);
            if (session && session.key === movieKey(movie)) session.done = true;
            return;
        }

        if (event.type !== 'render' || !event.element || !event.element.hash) return;
        if (!session || session.key !== movieKey(movie)) {
            session = {key: movieKey(movie), hash: preferredHash(movie), done: false};
        }
        if (session.done || !session.hash || String(event.element.hash) !== String(session.hash)) return;

        session.done = true;
        // The item is appended immediately after the render event. Defer the
        // synthetic enter until it is part of the list and controller can focus it.
        setTimeout(function () {
            if (event.item && event.item.trigger) event.item.trigger('hover:enter');
        }, 0);
    }

    function install() {
        if (window.tvLabTorrentResume || !window.Lampa || !Lampa.Listener) return;
        window.tvLabTorrentResume = true;
        Lampa.Listener.follow('torrent', onTorrent);
        Lampa.Listener.follow('activity', function (event) {
            if (event.type === 'start') {
                session = null;
            }
        });
    }

    if (window.appready) install();
    else Lampa.Listener.follow('app', function (event) {
        if (event.type === 'ready') install();
    });
}());
