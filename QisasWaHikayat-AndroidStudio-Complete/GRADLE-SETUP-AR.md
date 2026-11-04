# إصلاح إعداد Gradle

تم ضبط ملف `gradle/wrapper/gradle-wrapper.properties` على Gradle 8.13، وهو الإصدار المطلوب مع إعداد Android Gradle Plugin 8.13.2 في هذا المشروع.

**ملاحظة مهمة:** النسخة الأصلية المرفوعة لا تحتوي على ملفات تشغيل Gradle Wrapper (`gradlew` و`gradlew.bat` و`gradle-wrapper.jar`). لذلك لا يكفي ملف الخصائص وحده لتشغيل Wrapper. استخدم Gradle 8.13 من إعدادات Android Studio كما يلي:

1. نزّل Gradle 8.13 من الموقع الرسمي: https://gradle.org/releases/ ثم اختر Gradle 8.13 (Binary-only).
2. فك ضغط الملف إلى مجلد ثابت، مثل `C:\Gradle\gradle-8.13`.
3. في Android Studio افتح `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`.
4. في `Gradle distribution` اختر `Specified location` وحدد مجلد `C:\Gradle\gradle-8.13`.
5. اجعل `Gradle JDK` على JDK 17، ثم اضغط `Sync Project with Gradle Files`.

لا تغيّر كود التطبيق أو ملفات Kotlin أثناء هذه الخطوة.
