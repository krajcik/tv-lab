package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends Activity {
    private static final int PICK_IMAGE = 1;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText phrases;
    private CheckBox text, pictures, slow, quotes, shuffle;
    private Spinner quality, font, previewScene, saverKind, duration;
    private final CheckBox[] modes=new CheckBox[ParticleModes.COUNT];
    private TextView imageStatus;
    private Button importButton, deleteButton;
    private boolean importing;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(32), dp(56), dp(36));
        scroll.addView(content);
        setContentView(scroll);
        label(content, "TV LAB  /  ЗАСТАВКА", 13, 0xFF91BDB8);
        TextView heading = label(content, "Поток", 38, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        label(content, "Замкнутое пространство, в котором рождается движение.", 17, 0xFFB7C3CD);

        LinearLayout topButtons = row(content);
        Button preview = button(topButtons, "Посмотреть", () -> {
            save(); startActivity(new Intent(this, PreviewActivity.class).putExtra("mode",saverKind.getSelectedItemPosition()==0?previewScene.getSelectedItemPosition():0));
        });
        button(topButtons, "Выбрать системную заставку", () -> {
            save();
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException e) {
                toast("На этой прошивке нет отдельного экрана выбора. Проверьте Настройки → Система → Ambient mode.");
            }
        });
        label(content, "Выберите «Поток · TV Lab» в настройках телевизора. Предпросмотр закрывается кнопкой «Назад».", 14, 0xFF91A1AE);

        label(content, "Что появляется из частиц", 22, Color.WHITE);
        DreamConfig config = new DreamConfig(this);
        label(content,"Режим заставки",16,0xFFB7C3CD);
        saverKind=new Spinner(this);saverKind.setId(R.id.saver_kind);saverKind.setContentDescription("Режим заставки");
        ArrayAdapter<String> kindsAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Живые сцены · медитация","Изображения и цитаты"});kindsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);saverKind.setAdapter(kindsAdapter);saverKind.setSelection(config.procedural?0:1);content.addView(saverKind,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout liveOptions=new LinearLayout(this);liveOptions.setOrientation(LinearLayout.VERTICAL);content.addView(liveOptions);
        label(liveOptions,"Длительность одной сцены",16,0xFFB7C3CD);
        duration=new Spinner(this);duration.setId(R.id.mode_duration);duration.setContentDescription("Длительность одной сцены");
        ArrayAdapter<String> durationAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"15 минут","30 минут","1 час","3 часа","Не переключать"});durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);duration.setAdapter(durationAdapter);for(int i=0;i<ParticleModes.DURATIONS.length;i++)if(ParticleModes.DURATIONS[i]==config.modeSeconds)duration.setSelection(i);liveOptions.addView(duration,new LinearLayout.LayoutParams(-1,dp(56)));
        label(liveOptions,"Сцена для предпросмотра",16,0xFFB7C3CD);
        previewScene=new Spinner(this);previewScene.setId(R.id.preview_scene);previewScene.setContentDescription("Сцена для предпросмотра");
        String[] previewNames=new String[ParticleModes.COUNT+1];previewNames[0]="Автоматическая последовательность";
        System.arraycopy(ParticleModes.NAMES,0,previewNames,1,ParticleModes.COUNT);
        ArrayAdapter<String> modeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,previewNames);modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        previewScene.setAdapter(modeAdapter);liveOptions.addView(previewScene,new LinearLayout.LayoutParams(-1,dp(56)));
        button(liveOptions,"Показать выбранную сцену",()->{save();startActivity(new Intent(this,PreviewActivity.class).putExtra("mode",previewScene.getSelectedItemPosition()));});
        label(liveOptions,"Выбор здесь меняет только предпросмотр. Ниже — сцены системной заставки.",13,0xFF91A1AE);
        for(int i=0;i<modes.length;i++)modes[i]=checkbox(liveOptions,ParticleModes.NAMES[i],(config.modes&(1<<i))!=0);
        label(liveOptions,"Только частицы. Сюжет развивается непрерывно; переходы между сценами по 20 секунд. Без переключения остаётся одна из выбранных сцен. Если снять все отметки, останется «Дыхание».",14,0xFF91A1AE);
        shuffle = checkbox(content, "Случайный порядок при каждом запуске", config.shuffle);
        LinearLayout galleryOptions=new LinearLayout(this);galleryOptions.setOrientation(LinearLayout.VERTICAL);content.addView(galleryOptions);
        quotes = checkbox(galleryOptions, "Цитаты стоиков", config.quotes);
        label(galleryOptions, "1000 цитат Сенеки, Марка Аврелия и Эпиктета. С подписью автора.", 14, 0xFF91A1AE);
        label(galleryOptions, "Шрифт текста", 16, 0xFFB7C3CD);
        font = new Spinner(this);font.setId(R.id.font);
        ArrayAdapter<String> fonts = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
                new String[]{"Книжный · с засечками", "Лёгкий · без засечек", "Компактный · узкий"});
        fonts.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        font.setAdapter(fonts);font.setContentDescription("Шрифт текста");
        font.setSelection(config.font.equals(DreamConfig.FONTS[1])?1:config.font.equals(DreamConfig.FONTS[2])?2:0);
        galleryOptions.addView(font,new LinearLayout.LayoutParams(-1,dp(56)));
        text = checkbox(galleryOptions, "Свои фразы", config.text);
        phrases = new EditText(this);
        phrases.setId(R.id.phrases);
        phrases.setText(config.phrases);
        phrases.setHint("Одна фраза на строку");
        phrases.setContentDescription("Фразы, одна на строку");
        phrases.setTextColor(Color.WHITE);
        phrases.setTextSize(18);
        phrases.setMinLines(3); phrases.setMaxLines(6);
        phrases.setGravity(Gravity.TOP | Gravity.START);
        phrases.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        phrases.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1212)});
        galleryOptions.addView(phrases, new LinearLayout.LayoutParams(-1, -2));
        label(galleryOptions, "До 12 фраз, до 100 символов в каждой. Короткие фразы лучше читаются с дивана.", 13, 0xFF91A1AE);
        pictures = checkbox(galleryOptions, "Картинки: животные, природа и космос или ваше изображение", config.pictures);
        imageStatus = label(galleryOptions, "", 14, 0xFFB7C3CD);
        LinearLayout imageButtons = row(galleryOptions);
        importButton = button(imageButtons, "Добавить картинку", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, PICK_IMAGE); }
            catch (ActivityNotFoundException e) {
                toast("На телевизоре нет приложения выбора файлов. Встроенные животные и фразы доступны без него.");
            }
        });
        deleteButton = button(imageButtons, "Убрать свою картинку", () -> {
            new AtomicFile(imageFile()).delete(); updateImageStatus();
        });
        label(galleryOptions, "Лучше всего подходят контрастные силуэты. Картинка хранится только внутри приложения. Максимум 12 МБ.", 13, 0xFF91A1AE);
        updateImageStatus();

        liveOptions.setVisibility(config.procedural?View.VISIBLE:View.GONE);galleryOptions.setVisibility(config.procedural?View.GONE:View.VISIBLE);
        saverKind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){liveOptions.setVisibility(position==0?View.VISIBLE:View.GONE);galleryOptions.setVisibility(position==1?View.VISIBLE:View.GONE);}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        label(content, "Движение", 22, Color.WHITE);
        label(galleryOptions, "Текст: сборка 8 с, чтение 20 с, распад 5 с. Изображения: 6 / 3,9 / 3 с.", 14, 0xFF91A1AE);
        slow = checkbox(galleryOptions, "Длинные паузы между образами", config.cycleSeconds == 32);
        label(content, "Количество частиц", 16, 0xFFB7C3CD);
        quality = new Spinner(this);
        quality.setId(R.id.quality);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[] {"50 000 · экономно", "100 000 · средне", "200 000 · как в оригинале"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quality.setAdapter(adapter);
        quality.setSelection(config.count == 50000 ? 0 : config.count == 100000 ? 1 : 2);
        quality.setContentDescription("Количество частиц");
        content.addView(quality, new LinearLayout.LayoutParams(-1, dp(56)));
        label(content, "Без звука и подключения к интернету. Если движение прерывается, выберите 50 000 частиц.", 14, 0xFF91A1AE);
        button(content, "Сохранить", () -> { save(); toast("Настройки сохранены"); });
        preview.requestFocus();
    }

    private void save() {
        if (phrases == null) return;
        StringBuilder cleaned = new StringBuilder();
        int lines = 0;
        for (String line : phrases.getText().toString().split("\n")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (lines++ == 12) break;
            if (cleaned.length() > 0) cleaned.append('\n');
            cleaned.append(value, 0, Math.min(value.length(), 100));
        }
        int selectedModes=0;for(int i=0;i<modes.length;i++)if(modes[i].isChecked())selectedModes|=1<<i;
        DreamConfig.preferences(this).edit().putBoolean("procedural",saverKind.getSelectedItemPosition()==0).putInt("mode_seconds",ParticleModes.DURATIONS[duration.getSelectedItemPosition()]).putInt("modes",selectedModes).putString("phrases", cleaned.toString())
                .putBoolean("shuffle", shuffle.isChecked()).putString("font", DreamConfig.FONTS[font.getSelectedItemPosition()]).putBoolean("quotes", quotes.isChecked()).putBoolean("text", text.isChecked()).putBoolean("pictures", pictures.isChecked())
                .putBoolean("long_cycle", slow.isChecked()).putInt("count", new int[] {50000, 100000, 200000}[quality.getSelectedItemPosition()])
                .apply();
    }

    @Override public void onPause() { save(); super.onPause(); }

    @Override public void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null || importing) return;
        Uri uri = data.getData();
        importing = true;
        importButton.setEnabled(false); deleteButton.setEnabled(false);
        imageStatus.setText("Подготавливаем картинку…");
        io.execute(() -> {
            String error = null;
            try { importImage(uri); }
            catch (IOException | RuntimeException e) { error = "Не удалось прочитать картинку. Выберите PNG, JPEG или WebP размером до 12 МБ."; }
            final String message = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                importing = false;
                importButton.setEnabled(true);
                updateImageStatus();
                if (message != null) toast(message);
            });
        });
    }

    private void importImage(Uri uri) throws IOException {
        byte[] bytes;
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("No image stream");
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled");
                if (output.size() + count > 12 * 1024 * 1024) throw new IOException("Image is too large");
                output.write(chunk, 0, count);
            }
            bytes = output.toByteArray();
        }
        // ImageDecoder applies EXIF rotation/mirroring and decodes directly at the bounded size.
        Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                (decoder, info, source) -> {
                    int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                    if (width <= 0 || height <= 0 || width > 30000 || height > 30000)
                        throw new IllegalArgumentException("Unsupported image dimensions");
                    float scale = Math.min(1, Math.min(640f / width, 360f / height));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                });
        try {
            AtomicFile file = new AtomicFile(imageFile());
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Image encoding failed");
                if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled");
                file.finishWrite(output);
            } catch (IOException | RuntimeException e) { file.failWrite(output); throw e; }
        } finally {
            decoded.recycle();
        }
    }

    private File imageFile() { return new File(getFilesDir(), DreamConfig.IMAGE_FILE); }

    private void updateImageStatus() {
        boolean exists = imageFile().isFile();
        imageStatus.setText(exists ? "Своя картинка заменяет стандартный набор. Новая заменит её." : "100 образов: животные, природа и космос.");
        deleteButton.setEnabled(exists && !importing);
    }

    private CheckBox checkbox(LinearLayout parent, String title, boolean checked) {
        CheckBox view = new CheckBox(this);
        view.setText(title); view.setTextSize(17); view.setChecked(checked);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView label(LinearLayout parent, String title, int size, int color) {
        TextView text = new TextView(this);
        text.setText(title); text.setTextSize(size); text.setTextColor(color);
        text.setPadding(0, dp(8), 0, dp(8));
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private Button button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false); button.setTextSize(16);
        button.setPadding(dp(20), dp(8), dp(20), dp(8));
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(52));
        button.setOnFocusChangeListener((view, focused) -> styleButton(button, focused));
        styleButton(button, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(10), dp(16), dp(10));
        parent.addView(button, params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void styleButton(Button button, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(focused ? 0xFF40655F : 0xFF233139);
        background.setStroke(dp(2), focused ? 0xFFCDEFE6 : 0xFF34454F);
        button.setBackground(background);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
