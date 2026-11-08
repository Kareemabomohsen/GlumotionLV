package com.example.glumotion.chat;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;
import com.example.glumotion.common.FRef;
import com.example.glumotion.food.CustomFoodDialog;
import com.example.glumotion.food.FoodItem;
import com.example.glumotion.medications.MedicationModel;
import com.example.glumotion.profile.ImageHelper;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * One chat screen for the 4 bots of the app (the mode is sent inside the Intent):
 * 🤖 GENERAL - everything (it is opened from the chat button on every page)
 * 🩸 SUGAR   - the first bot: blood sugar, low / high, HbA1c (DM page)
 * 🍽 FOOD    - "I ate ..." adds food, meal photo, "can I eat ...?", new food (Food page)
 * 💪 WORKOUT - exercises, YouTube videos, plans, sugar before training (Workout page)
 * All the bots use the user's real data (ChatBotEngine).
 */
public class ChatBotActivity extends BaseActivity {

    public static final String EXTRA_MODE = "chat_mode";
    public static final String EXTRA_MEAL = "chat_meal";   // the food bot adds to this meal (from the "add meal" window)
    public static final String MODE_GENERAL = "general";
    public static final String MODE_SUGAR = "sugar";
    public static final String MODE_FOOD = "food";
    public static final String MODE_WORKOUT = "workout";

    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private EditText etMessage;
    private final ChatBotEngine engine = new ChatBotEngine();
    private FoodChatBot foodBot;
    private WorkoutChatBot workoutBot;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String mode = MODE_GENERAL;
    private boolean lastArabic = false;   // language of the last message (for the photo answer)

    // Meal photo: camera permission, camera, gallery
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    // Opens the chat with one of the bots (used by the chat buttons of all the pages)
    public static void open(Context context, String mode) {
        Intent intent = new Intent(context, ChatBotActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        context.startActivity(intent);
    }

    // Builds the screen and loads the user's data
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_bot);

        String extra = getIntent().getStringExtra(EXTRA_MODE);
        if (extra != null) {
            mode = extra;
        }
        setupBackBar(titleOfMode(mode));
        setupToolbarAction("Bots", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseBot();
            }
        });

        foodBot = new FoodChatBot(engine);
        String meal = getIntent().getStringExtra(EXTRA_MEAL);
        if (meal != null) {
            foodBot.setDefaultMeal(meal);
        }
        workoutBot = new WorkoutChatBot(engine);

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etChatMessage);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        rvChat.setLayoutManager(manager);
        adapter = new ChatAdapter(messages);
        adapter.setOnDeleteFoodsListener(new ChatAdapter.OnDeleteFoodsListener() {
            @Override
            public void onDeleteFoods(ChatMessage message) {
                chooseFoodsToDelete(message);
            }
        });
        rvChat.setAdapter(adapter);

        addMessage("...", false);
        engine.load(new ChatBotEngine.ReadyListener() {
            @Override
            public void onReady() {
                messages.clear();
                addMessage(welcome(), false);
            }
        });

        setupInput();
        setupCamera();
    }

    // Title of the back bar for every bot
    private String titleOfMode(String m) {
        if (m.equals(MODE_SUGAR)) {
            return "Sugar Bot 🩸";
        }
        if (m.equals(MODE_FOOD)) {
            return "Food Bot 🍽";
        }
        if (m.equals(MODE_WORKOUT)) {
            return "Workout Bot 💪";
        }
        return "GluBot 🤖";
    }

    // First message of every bot
    private String welcome() {
        if (mode.equals(MODE_FOOD)) {
            return "🍽 Food Bot / بوت الأكل\n\n"
                    + "Calories left today: " + engine.getKcalLeft() + "\n\n"
                    + "• Tell me what you ate: \"I ate 150g rice and 2 eggs\" / \"اكلت صحن حمص\" → I add it\n"
                    + "• 📷 Send a photo of your meal\n"
                    + "• Ask: \"Can I eat dates?\" → I check it for your sugar\n"
                    + "• \"Delete the rice\" / \"احذف الرز\" → I delete it (or tap 🗑 under the food)\n"
                    + "• \"New food\" → add a food that isn't in the app";
        }
        if (mode.equals(MODE_WORKOUT)) {
            return "💪 Workout Bot / بوت التمارين\n\n"
                    + "Level: " + engine.getFitnessLevel() + " • Routines: " + engine.getRoutines().size() + "\n\n"
                    + "Ask me about any exercise (\"squat video\", \"فيديو بنش\"), muscles (\"chest exercises\"), "
                    + "a plan for you, rest, sets & reps, or your sugar before training.";
        }
        if (mode.equals(MODE_GENERAL)) {
            return engine.welcome() + "\n\nI can also add your food (\"I ate ...\"), add a medication "
                    + "(\"add medication Metformin 500mg morning\" / \"ضيف دوا ...\"), tell you about a medication from the "
                    + "official FDA label (\"what is metformin\"), show exercise videos and explain where everything is in the app.";
        }
        return engine.welcome();   // the sugar bot = the first bot of the app
    }

    // "Bots" button: move to another bot
    private void chooseBot() {
        final String[] modes = {MODE_GENERAL, MODE_SUGAR, MODE_FOOD, MODE_WORKOUT};
        String[] names = {"🤖 GluBot - everything", "🩸 Sugar Bot", "🍽 Food Bot", "💪 Workout Bot"};
        new AlertDialog.Builder(this)
                .setTitle("Choose a bot")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!modes[which].equals(mode)) {
                            open(ChatBotActivity.this, modes[which]);
                            finish();
                        }
                    }
                })
                .show();
    }

    // Send button + "send" key of the keyboard
    private void setupInput() {
        findViewById(R.id.btnChatSend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(etMessage.getText().toString());
            }
        });
        etMessage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send(etMessage.getText().toString());
                    return true;
                }
                return false;
            }
        });
    }

    // User message → bot answer (small delay so it feels like typing)
    private void send(final String text) {
        if (text.trim().isEmpty()) {
            return;
        }
        lastArabic = ChatBotEngine.isArabic(text);
        addMessage(text.trim(), true);
        etMessage.setText("");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!answerByMode(text)) {
                    // ================================================================
                    // 🤖 Gemini (ذكاء اصطناعي حقيقي) للأسئلة اللي البوت ما بعرفها
                    // لتفعيل: حط المفتاح بـ GeminiHelper.API_KEY، احذف السطر addMessage(engine.notUnderstood()...)
                    // وشيل الـ // من الأسطر اللي تحته
                    // ================================================================
                    addMessage(engine.notUnderstood(), false);
                    // GeminiHelper.ask(text, engine.getContextSummary(), new ApiHelper.ApiCallback() {
                    //     @Override
                    //     public void onSuccess(String response) {
                    //         addMessage(response, false);
                    //     }
                    //
                    //     @Override
                    //     public void onError(String error) {
                    //         addMessage(engine.notUnderstood(), false);
                    //     }
                    // });
                }
            }
        }, 400);
    }

    /**
     * Asks the bots in the right order for the current mode.
     * Returns false if no bot understood the message.
     */
    private boolean answerByMode(String text) {
        // 💊 Every bot: "add medication ..." → adds it, "what is metformin?" → official FDA label
        if (MedicationChatBot.isAddRequest(text)) {
            addMedicationFromText(text);
            return true;
        }
        if (MedicationChatBot.isInfoRequest(text)) {
            answerMedicationInfo(MedicationChatBot.nameOf(text));
            return true;
        }
        if (mode.equals(MODE_FOOD)) {
            if (foodBot.handle(text, true, foodCallback)) {
                return true;
            }
            // The food bot doesn't suggest foods - it adds / checks / deletes what the user ate
            if (ChatBotEngine.has(ChatBotEngine.clean(text.toLowerCase()), new String[]{"suggest", "what should i eat", "اقترح", "شو آكل", "شو اكل"})) {
                if (ChatBotEngine.isArabic(text)) {
                    return reply("أنا بسجّل الأكل اللي أكلته 🍽 احكيلي شو أكلت (مثلاً \"اكلت 150 غرام رز\") وأنا بضيفه وبفحص إذا مناسب للسكر.");
                }
                return reply("I log what you ate 🍽 Tell me what you ate (e.g. \"I ate 150g rice\") and I add it and check it for your sugar.");
            }
            return reply(engine.answer(text));
        }
        if (mode.equals(MODE_WORKOUT)) {
            ChatMessage m = workoutBot.answer(text);
            if (m != null) {
                addBotMessage(m);
                return true;
            }
            return reply(engine.answer(text));
        }
        if (mode.equals(MODE_SUGAR)) {
            return reply(engine.answer(text));
        }

        // GENERAL: food log / delete food → videos → the first bot → workout bot → "how do I ..." in the app
        if ((FoodChatBot.isLogRequest(text) || FoodChatBot.isDeleteRequest(text)) && foodBot.handle(text, false, foodCallback)) {
            return true;
        }
        if (ChatBotEngine.has(ChatBotEngine.clean(text.toLowerCase()), new String[]{"video", "youtube", "فيديو", "يوتيوب"})) {
            ChatMessage m = workoutBot.answer(text);
            if (m != null) {
                addBotMessage(m);
                return true;
            }
        }
        String guide = engine.appGuide(text);
        if (guide != null) {
            return reply(guide);
        }
        if (reply(engine.answer(text))) {
            return true;
        }
        ChatMessage m = workoutBot.answer(text);
        if (m != null) {
            addBotMessage(m);
            return true;
        }
        return foodBot.handle(text, false, foodCallback);
    }

    // Shows an answer if there is one
    private boolean reply(String answer) {
        if (answer == null) {
            return false;
        }
        addMessage(answer, false);
        return true;
    }

    // What the food bot sends back to the screen
    private final FoodChatBot.Callback foodCallback = new FoodChatBot.Callback() {
        @Override
        public void onReply(String text) {
            addMessage(text, false);
        }

        @Override
        public void onUnknownFood(String name, double grams) {
            CustomFoodDialog.show(ChatBotActivity.this, name, new CustomFoodDialog.Listener() {
                @Override
                public void onFoodCreated(FoodItem food, double eatenGrams) {
                    engine.addCustomFood(food);
                    ArrayList<FoodItem> one = new ArrayList<>();
                    ArrayList<Double> oneGrams = new ArrayList<>();
                    one.add(food);
                    oneGrams.add(eatenGrams);
                    addMessage(foodBot.addFoods(one, oneGrams, mealNow()), false);
                }
            });
        }

        @Override
        public void onPhotoFoods(final ArrayList<FoodItem> foods, final ArrayList<Double> grams, String summary) {
            // AI can be wrong → the user checks before the food is saved
            final String meal = mealNow();
            new AlertDialog.Builder(ChatBotActivity.this)
                    .setTitle("📷 Add to " + meal + "?")
                    .setMessage(summary)
                    .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            addMessage(foodBot.addFoods(foods, grams, meal), false);
                        }
                    })
                    .setNegativeButton("Not right", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            addMessage("OK - not added. Write what you ate, e.g. \"I ate 200g rice\".", false);
                        }
                    })
                    .show();
        }
    };

    // Meal by the hour now (for the food from the photo / the new food), or the meal the user chose
    private String mealNow() {
        if (foodBot.getDefaultMeal() != null) {
            return foodBot.getDefaultMeal();
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 11) {
            return "Breakfast";
        }
        if (hour >= 11 && hour < 16) {
            return "Lunch";
        }
        if (hour >= 16 && hour < 21) {
            return "Dinner";
        }
        return "Snacks";
    }

    // ===================== MEAL PHOTO (food bot + general bot) =====================

    // Registers camera permission, camera and gallery + shows the camera button
    private void setupCamera() {
        View btnCamera = findViewById(R.id.btnChatCamera);
        if (!mode.equals(MODE_FOOD) && !mode.equals(MODE_GENERAL)) {
            btnCamera.setVisibility(View.GONE);
            return;
        }
        btnCamera.setVisibility(View.VISIBLE);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted) {
                            cameraLauncher.launch(null);
                        } else {
                            toast("Camera permission is needed to take a photo");
                        }
                    }
                });
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                new ActivityResultCallback<Bitmap>() {
                    @Override
                    public void onActivityResult(Bitmap bitmap) {
                        if (bitmap != null) {
                            sendPhoto(bitmap);
                        }
                    }
                });
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            loadFromGallery(uri);
                        }
                    }
                });

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ChatBotActivity.this)
                        .setTitle("Meal photo")
                        .setItems(new String[]{"📷 Camera", "🖼 Gallery"}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    openCamera();
                                } else {
                                    galleryLauncher.launch("image/*");
                                }
                            }
                        })
                        .show();
            }
        });
    }

    // Runtime permission: asks for CAMERA only if it was not given before
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null);
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Reads the chosen picture from the gallery
    private void loadFromGallery(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (stream != null) {
                stream.close();
            }
            if (bitmap != null) {
                sendPhoto(ImageHelper.resize(bitmap));
            }
        } catch (Exception e) {
            toast("Could not open the picture");
        }
    }

    // Shows the photo in the chat and sends it to the food bot
    private void sendPhoto(Bitmap bitmap) {
        ChatMessage message = new ChatMessage("📷 My meal", true);
        message.setPhoto(bitmap);
        messages.add(message);
        adapter.notifyDataSetChanged();
        rvChat.scrollToPosition(messages.size() - 1);
        foodBot.handlePhoto(bitmap, lastArabic, foodCallback);
    }

    // ===================== DELETE A FOOD (🗑 under the answer) =====================

    // The user chooses which of the added foods to delete (all are checked at the start)
    private void chooseFoodsToDelete(final ChatMessage message) {
        final ArrayList<AddedFood> foods = message.getAddedFoods();
        String[] names = new String[foods.size()];
        final boolean[] checked = new boolean[foods.size()];
        for (int i = 0; i < foods.size(); i++) {
            names[i] = foods.get(i).getName() + " - " + foods.get(i).getKcal() + " kcal";
            checked[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("🗑 Delete from your diary")
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ArrayList<AddedFood> toDelete = new ArrayList<>();
                        ArrayList<AddedFood> stay = new ArrayList<>();
                        for (int i = 0; i < foods.size(); i++) {
                            if (checked[i]) {
                                toDelete.add(foods.get(i));
                            } else {
                                stay.add(foods.get(i));
                            }
                        }
                        if (toDelete.isEmpty()) {
                            return;
                        }
                        message.setAddedFoods(stay);   // deleted foods can't be deleted twice
                        addMessage(foodBot.deleteAdded(toDelete), false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ===================== MEDICATIONS =====================

    // "add medication Metformin 500mg morning" → checks what is missing → asks "Add?"
    private void addMedicationFromText(String text) {
        final MedicationChatBot.MedRequest r = MedicationChatBot.parse(text);
        if (r.name.isEmpty()) {
            addMessage(arabicOr("Which medication? Write: \"add medication Metformin 500mg morning\"",
                    "شو اسم الدوا؟ اكتب مثلاً: \"ضيف دوا متفورمين 500 ملغ الصبح\""), false);
            return;
        }
        if (!r.category.isEmpty()) {
            confirmMedication(r);
            return;
        }
        // The time is missing → the same 4 times as the "Add medication" screen
        String[] options = new String[MedicationChatBot.CATEGORIES.length];
        for (int i = 0; i < options.length; i++) {
            options[i] = MedicationChatBot.CATEGORIES[i] + " • " + MedicationChatBot.TIMES[i];
        }
        new AlertDialog.Builder(this)
                .setTitle(arabicOr("⏰ When do you take " + r.name + "?", "⏰ إمتى بتوخذ " + r.name + "؟"))
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        r.category = MedicationChatBot.CATEGORIES[which];
                        r.time = MedicationChatBot.TIMES[which];
                        confirmMedication(r);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Shows what will be saved - nothing is saved without the user's OK
    private void confirmMedication(final MedicationChatBot.MedRequest r) {
        String dose = r.dosage;
        if (dose.isEmpty()) {
            dose = "-";
        }
        new AlertDialog.Builder(this)
                .setTitle(arabicOr("💊 Add this medication?", "💊 أضيف هالدوا؟"))
                .setMessage(arabicOr("Name: ", "الاسم: ") + r.name + "\n" + arabicOr("Dose: ", "الجرعة: ") + dose + "\n"
                        + arabicOr("Time: ", "الوقت: ") + r.category + " • " + r.time + "\n" + arabicOr("Every day", "كل يوم"))
                .setPositiveButton(arabicOr("Add", "أضف"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveMedication(r);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Saves in Firebase (Users / uid / Medications) - the same place as the "Add medication" screen
    private void saveMedication(MedicationChatBot.MedRequest r) {
        final MedicationModel med = MedicationModel.createForCategory(r.name, r.dosage, r.category, r.time);
        Task<Void> task = FRef.saveMedication(med);
        if (task == null) {
            addMessage(arabicOr("Please log in first.", "لازم تسجّل دخول أول."), false);
            return;
        }
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                engine.addMedication(med);
                addMessage(arabicOr("✅ Added: " + med.getDisplayName() + " - " + med.getTimingCategory() + " " + med.getTime()
                                + "\nYou'll find it in 💊 My Medications (and get a reminder if reminders are on).",
                        "✅ انضاف: " + med.getDisplayName() + " - " + med.getTimingCategory() + " " + med.getTime()
                                + "\nبتلاقيه بـ 💊 My Medications (وبيوصلك تذكير إذا التذكيرات شغالة)."), false);
                answerMedicationInfo(med.getName());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                addMessage(arabicOr("❌ Not saved - check the internet and try again.", "❌ ما انحفظ - افحص الإنترنت وجرّب كمان مرة."), false);
            }
        });
    }

    // Official information about the medication (U.S. FDA drug label)
    private void answerMedicationInfo(String name) {
        MedicationChatBot.lookup(name, lastArabic, new MedicationChatBot.InfoCallback() {
            @Override
            public void onInfo(String text) {
                addMessage(text, false);
            }
        });
    }

    // English or Arabic, by the language of the last message
    private String arabicOr(String english, String arabicText) {
        if (lastArabic) {
            return arabicText;
        }
        return english;
    }

    // ===================== MESSAGES =====================

    // Adds a text message and scrolls to it
    private void addMessage(String text, boolean fromUser) {
        addBotMessageOrUser(new ChatMessage(text, fromUser));
    }

    // Adds a bot message that can have a link (YouTube)
    private void addBotMessage(ChatMessage message) {
        addBotMessageOrUser(message);
    }

    // Adds any message to the list and scrolls down (an answer that added foods gets the 🗑 button)
    private void addBotMessageOrUser(ChatMessage message) {
        if (!message.isFromUser() && foodBot != null) {
            ArrayList<AddedFood> added = foodBot.takeJustAdded();
            if (!added.isEmpty()) {
                message.setAddedFoods(added);
            }
        }
        messages.add(message);
        adapter.notifyDataSetChanged();
        rvChat.scrollToPosition(messages.size() - 1);
    }
}
