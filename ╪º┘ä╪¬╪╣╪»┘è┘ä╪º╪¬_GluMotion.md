# GluMotion – شو تغيّر بالمشروع

## 1) الأخطاء اللي انصلحت
| المشكلة | السبب | الحل |
|---|---|---|
| أزرار صفحات الأسئلة (PQ3 → PQ4 → PQ5 → Summary) بتعلّق / الصفحات فوق بعض | كانت تنفتح بـ `android.R.id.content` بدل `R.id.fragment_container`، فالصفحة الجديدة تنرسم فوق القديمة | كل الصفحات صارت تنفتح بـ `R.id.fragment_container` |
| لما يختار **Bulking (تضخيم)** بيسأله عن الدايت | ما كان في فحص للهدف | الهدف بينتقل بـ Bundle من PQ1 لـ PQ4، وإذا Bulking بيختفي كرت الدايت وبيظهر بداله ملاحظة، وبينحفظ `Not needed (Bulking)` |
| إضافة أدوية بتحفظ **بس آخر دواء** | زر Add كان يعمل Toast بس، والـ Continue يحفظ اللي مكتوب بالخانة | كل دواء بينضاف لـ `ArrayList<MedicationModel>` وبينعرض بقائمة (مع زر ✕ للحذف)، والكل بينحفظ مرة وحدة |
| الأدوية محفوظة بأكثر من مكان وبأشكال مختلفة | PQ3 كان يحفظ بـ `HealthConditions`، والـ AddMedicationActivity بحقل `isTaken` | كل الأدوية صارت بمكان واحد: `Users/uid/Medications/medId` عن طريق `FRef.getRefMedications()` و `FRef.saveMedication()` |
| زر "Sign In" بصفحة إنشاء الحساب بيسكّر التطبيق | كان `finish()` والـ Splash أصلاً مسكّر | صار يفتح `LoginActivity` |
| Logout ما بيطلّع المستخدم | ما كان في `signOut()` | `FRef.getAuth().signOut()` + مسح كل الشاشات |
| زر Pills بصفحة Health إله listener-ين | الأول بيفتح صفحة فاضية | صار يفتح صفحة الأدوية بس |
| اختيار الهدف بـ PQ1 لازم تكبس على الدائرة بالزبط | الـ listener كان على الـ RadioButton بس | الكبسة على كل المربع بتختار |
| الـ Bottom Sheet دايماً "Breakfast" | ما كان يقرأ الـ arguments | صار يقرأ نوع الوجبة |
| لما الموبايل على Dark Mode النصوص بتختفي | الثيم كان DayNight | الثيم صار Light وأخضر حسب تصميمك |
| الكود فيه lambdas و `? :` و `String.format` | – | كل الكود صار anonymous classes و if/else بس |

## 2) قسم الجيم (حسب الصور)
- **WorkoutActivity**: تابين Routines / Explanation.
- **Explanation**: RecyclerView (`ExerciseAdapter`) فيه 18 تمرين: صورة + زر فيديو (بيفتح يوتيوب) + العضلات + السيتات + Form Cues + Progression + زر Start.
- **WorkoutSessionActivity**: جدول Set | Previous | kg | Reps | ✓ لكل تمرين، زر Add Set، Finish Workout، Back to Routines.
  - عمود Previous بيجي من Firebase (`ExerciseHistory`).
  - قبل التمرين بيسأل عن قراءة السكر وبيعطي نصيحة أمان (منخفض / ممتاز / عالي) وبيحفظها بسجل السكر.
  - بعد التمرين بيذكّر يفحص السكر.
- **RestTimerActivity**: CountDownTimer + ProgressBar + Restart / Pause / Skip + "Next: Set 2"، بينفتح بـ `registerForActivityResult`.

## 3) صفحة السكر (DM)
- زر + بيفتح Dialog لإضافة قراءة (رقم + وقت القراءة).
- أسهم التاريخ بتنقّل بين الأيام.
- القراءة الحالية + Normal/Elevated/High بالألوان + الفرق عن آخر قراءة + المؤشر على الشريط.
- معدل اليوم، نسبة In Range، عدد القراءات.
- الرسم البياني صار من بيانات حقيقية (Today / 7 / 14 / 30 يوم).
- كبسة طويلة على قراءة = حذف.

## 4) شكل قاعدة البيانات
```
Users/uid
 ├─ Goals, Fitness, Nutrition, HealthConditions, Allergies, DietPreference, SleepSchedule, WaterIntake, EmergencyContact, Status
 ├─ Medications/medId      {name, dosage, time, timingCategory, frequency, instruction, status}
 ├─ GlucoseReadings/id     {value, context, timestamp, date}
 ├─ Workouts/id            {routine, timestamp, date, durationMinutes, totalSets, totalVolumeKg, preWorkoutGlucose, exercises}
 ├─ ExerciseHistory/key    ["60kg × 8", ...]
 └─ DailyHealth/yyyy-MM-dd {steps, waterMl, sleepHour, ...}
```

## 5) توثيق الـ AI (شرط الوزارة)
| الجزء | مين عمله |
|---|---|
| تصميم الشاشات الأصلية (XML)، Splash/Login/CreateAccount، صفحات الأسئلة PQ1–PQ5، Health/Food/Home، User، FRef الأصلي | الطالب |
| تصليح الأخطاء بالجدول فوق، تحويل الكود لـ Java بسيطة | بمساعدة AI |
| BaseActivity, DateUtil, Exercise, Routine, WorkoutData, ExerciseAdapter, WorkoutSessionActivity, RestTimerActivity, GlucoseReading, منطق DMActivity، قائمة الأدوية بـ PQ3 | بمساعدة AI |
| ملفات XML الجديدة: activity_workout_session, activity_rest_timer, item_exercise_reference, item_session_exercise, item_set_row, item_pending_med, item_glucose_log, dialog_add_glucose + الـ drawables الجديدة | بمساعدة AI |


---

# النسخة الثانية - شو انضاف

## الأكل (Open Food Facts - مجاني)
- بحث عن أي أكلة بالعالم + قائمة أكلات جاهزة (بتشتغل بدون إنترنت).
- باركود بالكاميرا (مكتبة zxing) أو كتابة الرقم → القاعدة الإسرائيلية il.openfoodfacts.org.
- نافذة "كم غرام" والقيم بتتحسب وقت الكتابة.
- يوميات لكل يوم: Users/uid/FoodDiary/التاريخ + أسهم للأيام السابقة.
- مجموع السعرات + الكربوهيدرات + البروتين + الدهون + السكر مقابل هدف المستخدم.
- اقتراحات أكل قليل السكر تحت الوجبات بتناسب السعرات الباقية + صفحة وصفات.

## التمارين (زي Hevy + wger API مجاني)
- My Routines: روتين باسم (Push Day...) ويوم وتمارين وسيتات وعدات وراحة.
- اختيار تمارين من مكتبة جاهزة أو بحث wger أو تمرين خاص.
- Users/uid/Routines + تسجيل السيتات مع عمود Previous + تايمر.

## السكر
- صفحة Log Blood Sugar (حسب الصورة) + صفحة Glucose Trends (حسب الصورة).
- 🔗 Nightscout (Dexcom / Libre) - معطّل: شوف NightscoutHelper.java و DMActivity.java.

## الصحة
- 🚶 عداد الخطوات من حساس الهاتف - معطّل: شوف StepCounterHelper.java و HealthActivity.java.

## بروفايل + إعدادات + شات بوت
- بروفايل (حسب الصورة) مع صورة كاميرا/معرض و7 أقسام؛ تعديل الوزن/الهدف بيحسب السعرات من جديد.
- إعدادات أساسية بس: تذكيرات (AlarmManager + Notification)، وحدة السكر، الأدوية، الحساب، خروج.
- شات بوت بيجاوب عربي وإنجليزي من بيانات المستخدم. 🤖 Gemini اختياري معطّل: GeminiHelper.java.
- الصفحة الرئيسية كلها شخصية + أزرار رجوع بكل الصفحات الداخلية.

## الميزات المعطّلة (شيل الـ // بس)
| الميزة | الملف | شو تعمل |
|---|---|---|
| عداد الخطوات | HealthActivity.java | شيل // من 9 أسطر تحت "عدّاد الخطوات" + سطر stepCounter.stop() |
| Nightscout | DMActivity.java + NightscoutHelper.java | اكتب الرابط بـ NIGHTSCOUT_URL وشيل // من NightscoutHelper.importLatest |
| Gemini | ChatBotActivity.java + GeminiHelper.java | حط المفتاح بـ API_KEY، احذف سطر notUnderstood وشيل // من GeminiHelper.ask |

---

# التحديث (update) - شو انضاف

## أزرار الشات بوت
- صفحة السكر (DMActivity) وصفحة التمارين (WorkoutActivity): زر 🤖 بالشريط فوق بدل الزر العائم (BaseActivity بتدور على btnSectionBot).
- Food Bot صار خيار بنافذة "إضافة وجبة" بدل Recipes، وبيعرف الوجبة (Breakfast / Lunch...) اللي فتحته منها.

## الوصفات من TheMealDB (API مجاني)
- صفحة Recipes: تصنيفات + بحث + صور من الإنترنت (ImageLoader).
- صفحة RecipeDetailActivity: صورة، مكونات، طريقة التحضير خطوة خطوة، فيديو يوتيوب، المصدر.
- قيم غذائية تقديرية لحصة وحدة (RecipeNutrition) + نصيحة إذا مناسبة للسكر (FoodAdvisor).
- زر ➕ Add: بتختار الوجبة وبتراجع الأرقام وبتنحفظ بيوميات الأكل.

## المدرب (Coach)
- تسجيل حساب مدرب خاص بصفحة Create Account (Patient / Coach) + رابط بصفحة الدخول.
- Users/uid/role + CoachInfo/gym، والمدرب بيروح مباشرة للوحة المدرب.
- إصلاح خطأ "إضافة متدرب بالإيميل": البحث بـ EmailIndex وبعدين بـ Users/email، ورسالة نجاح/فشل حقيقية.
- قائمة "⏳ Waiting for approval" (SentRequests) وإلغاء الطلب بضغطة طويلة.
- لازم تحط database.rules.json الجديد بـ Firebase → Realtime Database → Rules.

| الجزء | بمساعدة AI؟ |
|---|---|
| ImageLoader, RecipeApi, RecipeNutrition, RecipeDetailActivity, Recipe, RecipeAdapter, SuggestedDishes, activity_recipe_detail, item_recipe | بمساعدة AI |
| تسجيل المدرب (CreateAccountActivity, User.role, SplashActivity, LoginActivity) + إصلاح CoachHelper + قائمة الانتظار بـ CoachActivity | بمساعدة AI |
| btnSectionBot بـ BaseActivity + Food Bot بـ AddMealBottomSheet | بمساعدة AI |

---

# التحديث الثاني (update2)

## المدرب والإشعارات
- الفايربيس: ما في EmailIndex. صار في `Coaches/uid` (الاسم، الإيميل، النادي، التلفون) وتحته `Trainees` (المتدربين اللي هو مسؤول عنهم)، `Sent` (طلبات بانتظار الموافقة) و`Alerts`.
- الطلب بينحفظ حسب إيميل المتدرب: `CoachRequests/email_key/coachUid`.
- المستخدم العادي ما بيشوف Coach Dashboard. مكانها بالقائمة 👤 صار 🔔 Notifications: قبول / رفض الطلب، الاتصال بالمدرب، وإيقاف المشاركة.
- "Later" بالرسالة ما بيمسح الطلب، بيضل بـ 🔔 Notifications.
- رقم التلفون صار مطلوب بإنشاء الحساب، وبيوصل للمدرب عشان يتصل.

## الشات بوت
- انشال زر الشات الدائري (تحت يمين) من كل الصفحات.
- صفحة الأدوية: زر 🤖 Ask فوق.
- انشالت أزرار الاقتراحات من كل البوتات، وبوت الأكل ما بيقترح أكلات.
- بوت الأكل بيحذف: "احذف الرز" / "delete the rice"، وزر 🗑 تحت كل أكل انضاف.

## الأكل
- البحث بأسماء إنجليزية (USDA FoodData Central، وإذا ما رد Open Food Facts بالإنجليزي أول).
- الباركود بالكتابة بس (انشالت الكاميرا ومكتبة zxing).
- منتج مش موجود ← المستخدم بيضيفه مرة وحدة وبينحفظ بـ `Products/barcode` لكل المستخدمين.
- زر 🗑 جنب كل أكلة بصفحة الأكل.

## التمارين
- 📅 History: الأيام اللي تدرب فيها، كم وقت، المجموعات والكيلوهات.
- التمارين الخاصة بتنحفظ بـ `Users/uid/CustomExercises` وبتطلع ⭐ كل مرة.

## انشال
- Health Score من الصفحة الرئيسية.

| الجزء | بمساعدة AI؟ |
|---|---|
| CoachHelper الجديد، NotificationsActivity، قوانين الأمان الجديدة | بمساعدة AI |
| WorkoutHistoryActivity، حفظ التمارين الخاصة | بمساعدة AI |
| حذف الأكل بالبوت (AddedFood، ChatAdapter)، البحث الإنجليزي (FoodApi)، الباركود بالكتابة | بمساعدة AI |

---

# التحديث الثالث (update3)

## ⌚ الساعة (Health Connect)
- الساعة مربوطة بالتلفون عن طريق تطبيقها (Samsung Health / Fitbit / Mi Fitness...) ← التطبيق بيكتب بـ Health Connect ← GluMotion بيقرأ (`devices/HealthConnectHelper`).
- الخطوات: مجموع واحد من Health Connect (بيشيل التكرار بين الساعة والتلفون) + حساس التلفون. بناخد الأكبر وما بنجمعهم (`devices/HealthSync`).
- ❤ دقات القلب بالـ Home فوق على الشمال (بس إذا الساعة بتقيسها).
- حساس خطوات التلفون صار شغّال (`StepCounterHelper`).

## 🩸 حساس السكر
- `devices/GlucoseSync`: Health Connect ← xDrip+ / Juggluco (سيرفر محلي 127.0.0.1:17580) ← Nightscout. كل 15 دقيقة لحاله.
- صفحة 📡 My Devices: الساعة + حساس FreeStyle Libre بالـ NFC (قرّب التلفون، كم يوم ضايل، تبديل الحساس).

## 📁 ترتيب الملفات
- الجافا بمجلدات: start, home, glucose, devices, food, recipes, workout, health, medications, profile, chat, coach, common.
- الـ XML بنفس المجلدات: `app/src/main/res-screens/<القسم>/layout` (بـ build.gradle.kts → sourceSets).

| الجزء | بمساعدة AI؟ |
|---|---|
| HealthConnectHelper، HealthSync، GlucoseSync، DevicesActivity، HealthPrivacyActivity | بمساعدة AI |
| ترتيب الملفات بمجلدات (packages + res-screens) | بمساعدة AI |

---

# التحديث الرابع (update4)

## 🩸 حساس السكر رجع زي ما كان
- انشال `GlucoseSync` وصفحة الحساس بالـ NFC وإذن قراءة السكر من Health Connect.
- رجع `glucose/NightscoutHelper` القديم (معلّق - مش شغّال)، و`scheduleSensorCheck` معلّق بـ `ReminderScheduler`.
- صفحة الأجهزة صارت **⌚ My Watch** بس (الخطوات + دقات القلب). كل اشي تاني بالمشروع زي ما هو.

## 💊 البوت بيضيف دوا
- `chat/MedicationChatBot`: "add medication Metformin 500mg morning" / "ضيف دوا متفورمين 500 ملغ الصبح".
- بيطلّع الاسم + الجرعة + الوقت (صبح / ظهر / مسا / قبل النوم). إذا الوقت ناقص بيسأل. ما بينحفظ اشي بدون "Add".
- بينحفظ بنفس مكان شاشة إضافة الدوا (Users/uid/Medications).

## 📚 معلومات من مصادر موثوقة
- "what is metformin" / "معلومات عن دوا اكامول" ← نشرة الدوا الرسمية من **FDA** (openFDA - مجاني، بدون مفتاح): لشو بيستعمل + التحذير + المصدر.
- إجابات السكر الواطي / العالي / التراكمي صار مكتوب تحتها المصدر: **ADA** (جمعية السكري الأمريكية).
- البوت ما بيألّف معلومات طبية، ودايماً بيذكّر: "خذ الدوا متل ما حكالك الدكتور".

| الجزء | بمساعدة AI؟ |
|---|---|
| MedicationChatBot (فهم الجملة + openFDA) | بمساعدة AI |
| إرجاع حساس السكر لوضعه القديم | بمساعدة AI |
