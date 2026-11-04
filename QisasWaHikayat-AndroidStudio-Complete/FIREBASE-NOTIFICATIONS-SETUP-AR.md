# إعداد إشعارات Dramio عبر Firebase وCloudflare

## 1) إعداد Firebase لتطبيق Android
1. افتح Firebase Console وأنشئ مشروعًا أو استخدم مشروع Firebase المرتبط بتطبيقك.
2. أضف تطبيق Android بمعرّف الحزمة `ap4221554.cyq` (يجب أن يطابق `applicationId` في Gradle).
3. نزّل ملف `google-services.json` وضعه داخل مجلد `app/` بجانب `app/build.gradle.kts`.
4. من Firebase Console افتح **Messaging** وتأكد من تفعيل Firebase Cloud Messaging.
5. افتح المشروع في Android Studio ثم نفّذ Gradle Sync وابنِ التطبيق.

## 2) إعداد Cloudflare Worker لإرسال FCM
أنشئ مفتاح Service Account من Google Cloud للمشروع نفسه، وامنح حساب الخدمة صلاحية إرسال رسائل Firebase Cloud Messaging API. فعّل Firebase Cloud Messaging API في Google Cloud.

أضف هذه القيم كـ Worker Secrets/Variables في إعدادات Worker `qisas-app-api`:
- `FCM_PROJECT_ID`: معرّف مشروع Firebase (ليس اسم العرض).
- `FCM_CLIENT_EMAIL`: بريد حساب الخدمة.
- `FCM_PRIVATE_KEY`: المفتاح الخاص كاملًا بصيغة PEM. خزّنه كـ Secret، ولا تضعه في التطبيق أو المستودع.

## 3) قاعدة بيانات D1
نفّذ ملف `backend/cloudflare/migration_story_notifications.sql` على قاعدة D1 المرتبطة بالـ Worker. ينشئ التطبيق الجدول تلقائيًا عند أول اشتراك أيضًا، لكن يفضّل تشغيل الترحيل مسبقًا.

## 4) سلوك النظام
- الجرس يطلب إذن الإشعارات على Android 13 فأحدث عند الحاجة.
- بعد الموافقة، يجلب التطبيق FCM token ويرسل الاشتراك إلى `/api/notifications/subscribe`.
- يحفظ التطبيق الاختيار محليًا، ويحفظ Worker الاشتراك في D1.
- عند إضافة حلقة من لوحة الإدارة عبر مسار إنشاء أجزاء القصة، يرسل Worker إشعارًا للمشتركين المفعّلين فقط.
- إلغاء المتابعة يعطّل سجل الاشتراك في D1، فلا تُرسل إليه إشعارات القصة.

ملاحظة: لا يمكن إرسال إشعارات حقيقية قبل إضافة `google-services.json` وإعداد أسرار FCM الثلاثة في Cloudflare. لا تشارك ملف Service Account أو المفتاح الخاص.
