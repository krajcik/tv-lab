# 100 изображений

В версии 0.4 действительно 100 отдельных сцен:47 животных,25 природных и 28
космических сюжетов. Прежние 12 сохранены; добавлены 88 новых. Это сгенерированные
иллюстрации, не фотографии конкретных животных и не документальные снимки космоса.

Для хранения используются 14 PNG-атласов. Каждая запись [images.json](../apps/particle-dream/src/main/res/raw/images.json)
указывает отдельную область изображения, загружаемую как самостоятельная сцена.
У новых атласов сетка 3×3; из последнего используются 7 ячеек. Проверены 100 разных
непустых облаков на Android. Границы идут по чёрным промежуткам между рисунками.

Иллюстрации созданы с помощью OpenAI image generation2026-09-05/06 для проекта.
Материалы сайта «Взаперти» в APK не входят. Кеш хранит лишь текущую и ближайшие
сцены; расширение каталога не возвращает предварительную загрузку всех облаков.

## Каталог

| № | Сюжет | Категория | Атлас |
|---|---|---|---|
| 1 | Ретривер | animals | pets |
| 2 | Дуб | nature | nature |
| 3 | Сатурн | space | space |
| 4 | Курильский бобтейл | animals | pets |
| 5 | Горный массив | nature | nature |
| 6 | Спиральная галактика | space | space |
| 7 | Рэгдолл | animals | pets |
| 8 | Папоротник | nature | nature |
| 9 | Лунный серп | space | space |
| 10 | Портрет ретривера | animals | pet_portraits |
| 11 | Коралл | nature | library_05 |
| 12 | Земля | space | library_08 |
| 13 | Портрет бобтейла | animals | pet_portraits |
| 14 | Морская раковина | nature | library_05 |
| 15 | Полная Луна | space | library_08 |
| 16 | Портрет рэгдолла | animals | pet_portraits |
| 17 | Наутилус | nature | library_05 |
| 18 | Марс | space | library_08 |
| 19 | Отдыхающий ретривер | animals | library_01 |
| 20 | Лотос | nature | library_05 |
| 21 | Юпитер | space | library_08 |
| 22 | Бобтейл с коротким хвостом | animals | library_01 |
| 23 | Сосна | nature | library_06 |
| 24 | Уран | space | library_08 |
| 25 | Спящий рэгдолл | animals | library_01 |
| 26 | Берёза | nature | library_06 |
| 27 | Нептун | space | library_08 |
| 28 | Лиса | animals | library_01 |
| 29 | Клён | nature | library_06 |
| 30 | Комета | space | library_08 |
| 31 | Волк | animals | library_01 |
| 32 | Бамбук | nature | library_06 |
| 33 | Астероид | space | library_08 |
| 34 | Малая панда | animals | library_01 |
| 35 | Пальма | nature | library_06 |
| 36 | Солнечная корона | space | library_08 |
| 37 | Енот | animals | library_01 |
| 38 | Ива | nature | library_06 |
| 39 | Галактика анфас | space | library_09 |
| 40 | Заяц | animals | library_01 |
| 41 | Лист монстеры | nature | library_06 |
| 42 | Галактика с ребра | space | library_09 |
| 43 | Белка | animals | library_01 |
| 44 | Ветвь гинкго | nature | library_06 |
| 45 | Галактика с перемычкой | space | library_09 |
| 46 | Олень | animals | library_02 |
| 47 | Орхидея | nature | library_06 |
| 48 | Слияние галактик | space | library_09 |
| 49 | Лань | animals | library_02 |
| 50 | Две снежные вершины | nature | library_07 |
| 51 | Планетарная туманность | space | library_09 |
| 52 | Лось | animals | library_02 |
| 53 | Водопад | nature | library_07 |
| 54 | Газовая туманность | space | library_09 |
| 55 | Лошадь | animals | library_02 |
| 56 | Вулкан | nature | library_07 |
| 57 | Туманность Розетка | space | library_09 |
| 58 | Бизон | animals | library_02 |
| 59 | Песчаные дюны | nature | library_07 |
| 60 | Чёрная дыра | space | library_09 |
| 61 | Бурый медведь | animals | library_02 |
| 62 | Бонсай | nature | library_07 |
| 63 | Остаток сверхновой | space | library_09 |
| 64 | Белый медведь | animals | library_02 |
| 65 | Гроза | nature | library_07 |
| 66 | Шлем космонавта | space | library_10 |
| 67 | Рысь | animals | library_02 |
| 68 | Кристаллы кварца | nature | library_07 |
| 69 | Космонавт | space | library_10 |
| 70 | Снежный барс | animals | library_02 |
| 71 | Сосновая шишка | nature | library_07 |
| 72 | Космическая станция | space | library_10 |
| 73 | Лев | animals | library_03 |
| 74 | Иней на листе | nature | library_07 |
| 75 | Межпланетный зонд | space | library_10 |
| 76 | Тигр | animals | library_03 |
| 77 | Ракета | space | library_10 |
| 78 | Леопард | animals | library_03 |
| 79 | Радиотелескоп | space | library_10 |
| 80 | Гепард | animals | library_03 |
| 81 | Лунный модуль | space | library_10 |
| 82 | Пума | animals | library_03 |
| 83 | Ягуар | animals | library_03 |
| 84 | Выдра | animals | library_03 |
| 85 | Ёж | animals | library_03 |
| 86 | Коала | animals | library_03 |
| 87 | Сипуха | animals | library_04 |
| 88 | Орёл | animals | library_04 |
| 89 | Колибри | animals | library_04 |
| 90 | Зимородок | animals | library_04 |
| 91 | Журавль | animals | library_04 |
| 92 | Лебедь | animals | library_04 |
| 93 | Цапля | animals | library_04 |
| 94 | Ворон | animals | library_04 |
| 95 | Павлин | animals | library_04 |
| 96 | Дельфин | animals | library_05 |
| 97 | Горбатый кит | animals | library_05 |
| 98 | Морская черепаха | animals | library_05 |
| 99 | Стрекоза | animals | library_05 |
| 100 | Бабочка | animals | library_05 |
