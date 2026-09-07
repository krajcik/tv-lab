(function () {
    'use strict'

    if (window.plugin_russian_sports_ready) return
    window.plugin_russian_sports_ready = true

    var streams = [
        {
            title: 'Racing · NASCAR Cup · Be on Edge · Вадим Крымов',
            type: 'rutube',
            id: '523b9406b11c746c95db6b628be21e26'
        },
        {
            title: 'Boxing · Усик — Дюбуа · ПраймСпорт · русский комментарий',
            type: 'rutube',
            id: 'db34af67da222daa35e06cf5ac2c7760'
        },
        {
            title: 'MMA · MMA Серия-92 · Давыдов / Софронов',
            type: 'rutube',
            id: '767e12ce51f0e32445da86f02f30673e'
        },
        {
            title: 'Hockey NHL · Вашингтон — Питтсбург · SportCast',
            type: 'vk',
            oid: '-41593209',
            id: '456253482'
        },
        {
            title: 'Basketball NBA · Кливленд — Детройт · AANBA',
            type: 'rutube',
            id: 'c08136215579c6c7bcf2dda800927e12'
        },
        {
            title: 'Football EPL · Арсенал — Челси · SportCast',
            type: 'vk',
            oid: '-214218677',
            id: '456252675'
        }
    ]

    var network = new Lampa.Reguest()
    network.timeout(20000)

    function firstString(value, test) {
        if (typeof value === 'string') {
            return test(value) ? value : null
        }

        if (!value || typeof value !== 'object') return null

        var keys = Object.keys(value)
        for (var i = 0; i < keys.length; i++) {
            var found = firstString(value[keys[i]], test)
            if (found) return found
        }

        return null
    }

    function resolveRutube(stream, done, fail) {
        var url = 'https://rutube.ru/api/play/options/' + stream.id + '/'

        network.native(url, function (body) {
            try {
                var data = typeof body === 'string' ? JSON.parse(body) : body
                var hls = firstString(data, function (value) {
                    return /\.m3u8(?:\?|$)/i.test(value)
                })

                if (hls) done(hls)
                else fail('Rutube не вернул HLS')
            }
            catch (error) {
                fail('Не удалось разобрать ответ Rutube')
            }
        }, function () {
            fail('Rutube недоступен')
        }, false, {dataType: 'text'})
    }

    function resolveVk(stream, done, fail) {
        var url = 'https://vkvideo.ru/video_ext.php?oid=' + encodeURIComponent(stream.oid) + '&id=' + stream.id

        network.native(url, function (body) {
            var text = typeof body === 'string' ? body : String(body || '')
            var matches = text.match(/https?:\\?\/\\?\/[^"'\\ ]+?\.m3u8[^"'\\ ]*/ig) || []

            if (!matches.length) return fail('VK не вернул HLS')

            done(matches[0].replace(/\\\//g, '/'))
        }, function () {
            fail('VK недоступен')
        }, false, {dataType: 'text'})
    }

    function play(stream) {
        Lampa.Noty.show('Получаю свежую ссылку…')

        var resolved = function (url) {
            Lampa.Player.play({
                url: url,
                title: stream.title,
                launch_player: 'inner'
            })
        }

        var failed = function (message) {
            Lampa.Noty.show(message)
        }

        if (stream.type === 'rutube') resolveRutube(stream, resolved, failed)
        else resolveVk(stream, resolved, failed)
    }

    function addButton() {
        if (!Lampa.Menu || !Lampa.Menu.addButton) return

        Lampa.Menu.addButton(
            '<svg viewBox="0 0 38 36"><path d="M3 10h32v18H3zM8 5h22M8 31h22" fill="none" stroke="currentColor" stroke-width="3"/></svg>',
            'RU Спорт',
            function () {
                Lampa.Select.show({
                    title: 'Русские спортивные трансляции',
                    items: streams.map(function (stream) {
                        return {title: stream.title, stream: stream}
                    }),
                    onSelect: function (item) {
                        Lampa.Controller.toggle('content')
                        play(item.stream)
                    },
                    onBack: function () {
                        Lampa.Controller.toggle('content')
                    }
                })
            }
        )
    }

    if (window.appready) addButton()
    else Lampa.Listener.follow('app', function (event) {
        if (event.type === 'ready') addButton()
    })
}())
