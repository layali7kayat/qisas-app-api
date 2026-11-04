# إعداد تسجيل الدخول Google في Dramio

## ما تمت إضافته إلى المصدر
- Android Credential Manager لاختيار حساب Google والحصول على Google ID Token.
- استخدام OAuth Web Client ID في طلب Credential Manager.
- مطالبة المستخدم باسم عرض بعد اختيار حساب Google.
- إرسال ID Token واسم العرض إلى `/api/auth/google`.
- يتحقق Cloudflare Worker من الرمز عبر Google tokeninfo، ويتأكد من issuer وaudience ووقت الانتهاء وبريد موثّق.
- إنشاء/تحديث المستخدم في Cloudflare D1، مع صورة افتراضية ثابتة `dramio-default` وعدم استخدام صورة Google.

## إعدادات OAuth المستخدمة
- Android package: `ap4221554.cyq`
- Android OAuth Client ID: `384580739745-03mdpsd1obun2gvs7m3va6nj90f3jau1.apps.googleusercontent.com`
- Web OAuth Client ID: `384580739745-stahpkl2nsf26nj3l88lpi4lebf1o7t7.apps.googleusercontent.com`
- SHA-1 وSHA-256 تبقى إعدادات في Google Cloud Console لعميل Android؛ لا توضع داخل Kotlin.

## نشر قاعدة البيانات والخادم
1. افتح Cloudflare Dashboard ثم Workers & Pages ثم Worker المستخدم في التطبيق.
2. افتح D1 Database المرتبطة باسم `DB`.
3. نفّذ محتوى `backend/cloudflare/migration_users.sql` مرة واحدة في D1 Console. الأمر آمن لإعادة التشغيل.
4. انشر الملف المحدّث `backend/cloudflare/worker.js` إلى نفس Worker الحالي، مع الإبقاء على ربط D1 باسم `DB`.
5. اختبر `GET /api/health` ثم جرّب تسجيل الدخول من نسخة Android.

## ملاحظات أمان وتشغيل
- لا تضع أسرارًا أو كلمات مرور في التطبيق.
- الخادم لا يثق بالبريد أو الاسم المرسل من التطبيق؛ البريد وGoogle subject يؤخذان من الرمز الذي تحقّق منه Google.
- يستخدم المسار tokeninfo للتحقق من الرمز، وهو مناسب لبدء التكامل واختباره؛ للإنتاج عالي الحجم يُفضّل التحقق محليًا من توقيع JWT باستخدام مفاتيح Google العامة مع التخزين المؤقت.
- لا يصدر هذا التحديث جلسة خادم طويلة الأجل؛ يحتفظ التطبيق ببيانات العرض محليًا فقط، ويمكن إضافة جلسات موقعة/تجديد لاحقًا قبل ربط المفضلة والتعليقات بحسابات المستخدمين.
- يجب أن يكون OAuth Web Client ID من المشروع نفسه الذي أنشأ Android Client ID، وأن تكون Google Sign-In مفعلة للمستخدمين المقصودين.
- لم يتم تنفيذ Gradle Build في بيئة الإنشاء الحالية؛ يلزم Gradle Sync وBuild في Android Studio قبل التوزيع.
