package com.qisaswahikayat.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private suspend fun uploadAdminFile(uri: Uri, audio: Boolean, email: String, password: String, context: Context): String = withContext(Dispatchers.IO) {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else "file"
    } ?: "file"
    val mime = context.contentResolver.getType(uri) ?: if (audio) "audio/mpeg" else "image/jpeg"
    require(if (audio) name.endsWith(".mp3", true) else mime in listOf("image/jpeg", "image/png", "image/webp")) {
        "اختاري ملف MP3 أو صورة JPG/PNG/WebP"
    }
    val kind = if (audio) "audio" else "story"
    val url = "$API_BASE/api/upload-url?kind=$kind&type=${URLEncoder.encode(mime, "UTF-8")}&filename=${URLEncoder.encode(name, "UTF-8")}"
    val keyResponse = adminRequest(url.removePrefix(API_BASE), email, password, "POST", JSONObject().put("filename", name).put("kind", kind).put("type", mime).toString())
    val key = keyResponse.getString("key")
    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    require(size <= if (audio) 100L * 1024 * 1024 else 8L * 1024 * 1024) { "حجم الملف أكبر من الحد المسموح" }
    val connection = URL("$API_BASE/api/upload/${URLEncoder.encode(key, "UTF-8")}").openConnection() as HttpURLConnection
    connection.requestMethod = "PUT"
    connection.doOutput = true
    connection.connectTimeout = 30000
    connection.readTimeout = 120000
    connection.setRequestProperty("X-Admin-Email", email)
    connection.setRequestProperty("X-Admin-Password", password)
    connection.setRequestProperty("Content-Type", mime)
    if (size > 0L) connection.setFixedLengthStreamingMode(size)
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("تعذر قراءة الملف")
        input.use { source -> connection.outputStream.use { target -> source.copyTo(target, 64 * 1024) } }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        if (code !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "فشل رفع الملف"))
    } finally { connection.disconnect() }
    key
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

@Composable
fun AdminPanelScreen(onClose: () -> Unit, onCatalogChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("القصص") }
    var categories by remember { mutableStateOf(emptyList<JSONObject>()) }
    var stories by remember { mutableStateOf(emptyList<JSONObject>()) }
    var comments by remember { mutableStateOf(emptyList<JSONObject>()) }
    var editingStory by remember { mutableStateOf<JSONObject?>(null) }
    var storyDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<JSONObject?>(null) }
    var categoryDialog by remember { mutableStateOf(false) }
    var editingPart by remember { mutableStateOf<JSONObject?>(null) }
    var partStory by remember { mutableStateOf<JSONObject?>(null) }
    var parts by remember { mutableStateOf(emptyList<JSONObject>()) }
    var partDialog by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var categoryIds by remember { mutableStateOf(setOf<String>()) }
    var status by remember { mutableStateOf("ongoing") }
    var totalEpisodes by remember { mutableStateOf("1") }
    var audioPath by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf("") }
    var wideImagePath by remember { mutableStateOf("") }
    var suggestedImagePath by remember { mutableStateOf("") }
    var characterOneName by remember { mutableStateOf("البطلة") }
    var characterTwoName by remember { mutableStateOf("البطل") }
    var characterThreeName by remember { mutableStateOf("شخصية محورية") }
    var characterOneImage by remember { mutableStateOf("") }
    var characterTwoImage by remember { mutableStateOf("") }
    var characterThreeImage by remember { mutableStateOf("") }
    var categoryTitle by remember { mutableStateOf("") }
    var categoryImage by remember { mutableStateOf("") }
    var partTitle by remember { mutableStateOf("") }
    var partAudio by remember { mutableStateOf("") }
    var partImage by remember { mutableStateOf("") }
    var pickerTarget by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            busy = true
            try {
                val catalog = adminRequest("/api/admin/catalog", email, password)
                categories = catalog.optJSONArray("categories")?.objects() ?: emptyList()
                stories = catalog.optJSONArray("stories")?.objects() ?: emptyList()
                comments = adminRequest("/api/admin/comments", email, password).optJSONArray("comments")?.objects() ?: emptyList()
                message = "تم تحديث البيانات"
            } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث البيانات" }
            busy = false
        }
    }

    fun resetStory() {
        editingStory = null; title = ""; description = ""; categoryId = categories.firstOrNull()?.optString("id") ?: ""; categoryIds = emptySet()
        status = "ongoing"; totalEpisodes = "1"; audioPath = ""; imagePath = ""; wideImagePath = ""; suggestedImagePath = ""
        characterOneName = "البطلة"; characterTwoName = "البطل"; characterThreeName = "شخصية محورية"
        characterOneImage = ""; characterTwoImage = ""; characterThreeImage = ""
    }
    fun editStory(story: JSONObject?) {
        resetStory(); editingStory = story
        if (story != null) {
            title = story.optString("title"); description = story.optString("description")
            categoryId = story.optString("category_id"); categoryIds = story.optJSONArray("category_ids")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.toSet() } ?: setOf(categoryId); status = story.optString("status", "ongoing")
            totalEpisodes = story.optString("total_episodes", "1"); audioPath = story.optString("audio_path")
            imagePath = story.optString("image_path"); wideImagePath = story.optString("wide_image_path"); suggestedImagePath = story.optString("suggested_image_path")
            characterOneName = story.optString("character_one_name", "البطلة"); characterTwoName = story.optString("character_two_name", "البطل")
            characterThreeName = story.optString("character_three_name", "شخصية محورية")
            characterOneImage = story.optString("character_one_image_path"); characterTwoImage = story.optString("character_two_image_path")
            characterThreeImage = story.optString("character_three_image_path")
        }
        storyDialog = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val key = uploadAdminFile(uri, pickerTarget == "audio" || pickerTarget == "partAudio", email, password, context)
                when (pickerTarget) {
                    "audio" -> audioPath = key
                    "image" -> imagePath = key
                    "wide" -> wideImagePath = key
                    "suggested" -> suggestedImagePath = key
                    "characterOne" -> characterOneImage = key
                    "characterTwo" -> characterTwoImage = key
                    "characterThree" -> characterThreeImage = key
                    "category" -> categoryImage = key
                    "partAudio" -> partAudio = key
                    "partImage" -> partImage = key
                }
                message = "تم رفع الملف بنجاح"
            } catch (e: Exception) { message = e.localizedMessage ?: "فشل رفع الملف" }
            busy = false
        }
    }

    fun pick(target: String, audio: Boolean = false) {
        pickerTarget = target
        picker.launch(if (audio) arrayOf("audio/mpeg") else arrayOf("image/*"))
    }

    if (!loggedIn) {
        Column(Modifier.fillMaxSize().background(Ink).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("دخول لوحة الإدارة", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true)
            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(onClick = {
                scope.launch {
                    busy = true
                    try {
                        adminRequest("/api/admin/login", email, password, "POST", JSONObject().put("email", email).put("password", password).toString())
                        loggedIn = true; refresh()
                    } catch (e: Exception) { message = e.localizedMessage ?: "فشل تسجيل الدخول" }
                    busy = false
                }
            }, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جارٍ التحقق…" else "تسجيل الدخول") }
            if (message.isNotBlank()) Text(message, color = Color(0xFFFF8A80))
            TextButton(onClick = onClose) { Text("عودة", color = Gold) }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("إدارة التطبيق", color = Gold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refresh() }) { Text("تحديث", color = Gold) }
            TextButton(onClick = { loggedIn = false; password = "" }) { Text("خروج", color = Gold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("القصص", "التصنيفات", "التعليقات").forEach { label ->
                TextButton(onClick = { tab = label; partStory = null }) { Text(label, color = if (tab == label) Gold else Color.White) }
            }
        }
        when (tab) {
            "القصص" -> {
                if (partStory != null) {
                    Column(Modifier.weight(1f).padding(12.dp)) {
                        Text("فصول: ${partStory?.optString("title")}", color = Gold, fontSize = 19.sp)
                        Button(onClick = { editingPart = null; partTitle = ""; partAudio = ""; partImage = ""; partDialog = true }) { Text("إضافة فصل") }
                        LazyColumn {
                            items(parts, key = { it.optString("id") }) { part ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Panel, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(part.optString("title"), color = Color.White, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editingPart = part; partTitle = part.optString("title"); partAudio = part.optString("audio_path"); partImage = part.optString("image_path"); partDialog = true }) { Text("تعديل", color = Gold) }
                                    TextButton(onClick = {
                                        scope.launch { runCatching { adminRequest("/api/admin/part/${part.optString("id")}", email, password, "DELETE"); parts = loadParts(partStory!!, email, password) }.onFailure { message = it.localizedMessage ?: "تعذر الحذف" } }
                                    }) { Text("حذف", color = Color(0xFFFF8A80)) }
                                }
                            }
                        }
                        TextButton(onClick = { partStory = null }) { Text("رجوع للقصص", color = Gold) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("القصص (${stories.size})", color = Gold); Spacer(Modifier.weight(1f))
                        Button(onClick = { editStory(null) }) { Text("إضافة قصة") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(stories, key = { it.optString("id") }) { story ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).background(Panel, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(story.optString("title"), color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(if (story.optString("status") == "completed") "مكتملة" else "مستمرة", color = Gold, fontSize = 12.sp)
                                }
                                TextButton(onClick = { partStory = story; scope.launch { runCatching { parts = loadParts(story, email, password) }.onFailure { message = it.localizedMessage ?: "تعذر تحميل الفصول" } } }) { Text("الفصول", color = Gold) }
                                TextButton(onClick = { editStory(story) }) { Text("تعديل", color = Gold) }
                                TextButton(onClick = { scope.launch { runCatching { adminRequest("/api/admin/story/${story.optString("id")}", email, password, "DELETE"); refresh() }.onFailure { message = it.localizedMessage ?: "تعذر الحذف" } } }) { Text("حذف", color = Color(0xFFFF8A80)) }
                            }
                        }
                    }
                }
            }
            "التصنيفات" -> {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text("التصنيفات (${categories.size})", color = Gold); Spacer(Modifier.weight(1f)); Button(onClick = { editingCategory = null; categoryTitle = ""; categoryImage = ""; categoryDialog = true }) { Text("إضافة") } }
                LazyColumn(Modifier.weight(1f)) {
                    items(categories, key = { it.optString("id") }) { category ->
                        Row(Modifier.fillMaxWidth().padding(8.dp).background(Panel, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.optString("title"), color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editingCategory = category; categoryTitle = category.optString("title"); categoryImage = category.optString("image_path"); categoryDialog = true }) { Text("تعديل", color = Gold) }
                            TextButton(onClick = { scope.launch { runCatching { adminRequest("/api/admin/category/${category.optString("id")}", email, password, "DELETE"); refresh() }.onFailure { message = it.localizedMessage ?: "تعذر الحذف" } } }) { Text("حذف", color = Color(0xFFFF8A80)) }
                        }
                    }
                }
            }
            else -> {
                Text("التعليقات (${comments.size})", color = Gold, modifier = Modifier.padding(12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(comments, key = { it.optString("id") }) { comment ->
                        Column(Modifier.fillMaxWidth().padding(8.dp).background(Panel, RoundedCornerShape(10.dp)).padding(12.dp)) {
                            Text(comment.optString("display_name", "زائر"), color = Gold)
                            Text(comment.optString("content"), color = Color.White)
                            Text(comment.optString("story_title"), color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { scope.launch { runCatching { adminRequest("/api/admin/comment/${comment.optString("id")}", email, password, "DELETE"); refresh() }.onFailure { message = it.localizedMessage ?: "تعذر الحذف" } } }) { Text("حذف التعليق", color = Color(0xFFFF8A80)) }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = if (message.contains("تعذر") || message.contains("فشل")) Color(0xFFFF8A80) else Gold, modifier = Modifier.padding(10.dp))
    }

    if (storyDialog) AlertDialog(
        onDismissRequest = { storyDialog = false },
        title = { Text(if (editingStory == null) "إضافة قصة" else "تعديل القصة") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان القصة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("الوصف") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(totalEpisodes, { totalEpisodes = it }, label = { Text("عدد الحلقات") }, modifier = Modifier.fillMaxWidth())
                    Text("التصنيف", color = Gold)
                    categories.forEach { category ->
                        val id = category.optString("id")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in categoryIds, onCheckedChange = { checked -> categoryIds = if (checked) categoryIds + id else categoryIds - id })
                            Text(category.optString("title"), color = Color.White)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(status == "ongoing", { status = "ongoing" }); Text("مستمرة", color = Color.White); RadioButton(status == "completed", { status = "completed" }); Text("مكتملة", color = Color.White) }
                }
                item { UploadRow("ملف الصوت MP3", audioPath, { pick("audio", true) }) }
                item { UploadRow("صورة الغلاف", imagePath, { pick("image") }) }
                item { UploadRow("الصورة العريضة", wideImagePath, { pick("wide") }) }
                item { UploadRow("صورة القصص المقترحة (16:9)", suggestedImagePath, { pick("suggested") }) }
                item { OutlinedTextField(characterOneName, { characterOneName = it }, label = { Text("اسم البطلة") }); UploadRow("صورة البطلة", characterOneImage, { pick("characterOne") }) }
                item { OutlinedTextField(characterTwoName, { characterTwoName = it }, label = { Text("اسم البطل") }); UploadRow("صورة البطل", characterTwoImage, { pick("characterTwo") }) }
                item { OutlinedTextField(characterThreeName, { characterThreeName = it }, label = { Text("اسم الشخصية المحورية") }); UploadRow("صورة الشخصية المحورية", characterThreeImage, { pick("characterThree") }) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && title.isNotBlank() && audioPath.startsWith("audio/") && imagePath.startsWith("images/"), onClick = {
                scope.launch {
                    busy = true
                    try {
                        val chosenCategories = if (categoryIds.isNotEmpty()) categoryIds else setOf(categoryId).filter { it.isNotBlank() }.toSet()
                        require(chosenCategories.isNotEmpty()) { "اختاري تصنيفًا واحدًا على الأقل" }
                        val body = JSONObject().put("title", title).put("description", description).put("category_id", chosenCategories.first()).put("category_ids", JSONArray(chosenCategories.toList()))
                            .put("audio_path", audioPath).put("image_path", imagePath).put("wide_image_path", wideImagePath).put("suggested_image_path", suggestedImagePath)
                            .put("total_episodes", totalEpisodes.toIntOrNull() ?: 1).put("status", status)
                            .put("character_one_name", characterOneName).put("character_two_name", characterTwoName).put("character_three_name", characterThreeName)
                            .put("character_one_image_path", characterOneImage).put("character_two_image_path", characterTwoImage).put("character_three_image_path", characterThreeImage)
                        if (editingStory == null) adminRequest("/api/admin/story", email, password, "POST", body.toString())
                        else adminRequest("/api/admin/story/${editingStory!!.optString("id")}", email, password, "PUT", body.toString())
                        storyDialog = false; refresh(); onCatalogChanged()
                    } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حفظ القصة" }
                    busy = false
                }
            }) { Text("حفظ") }
        }, dismissButton = { TextButton(onClick = { storyDialog = false }) { Text("إلغاء") } }
    )

    if (categoryDialog) AlertDialog(
        onDismissRequest = { categoryDialog = false }, title = { Text(if (editingCategory == null) "إضافة تصنيف" else "تعديل التصنيف") },
        text = { Column { OutlinedTextField(categoryTitle, { categoryTitle = it }, label = { Text("اسم التصنيف") }); UploadRow("صورة التصنيف", categoryImage, { pick("category") }) } },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                busy = true
                try {
                    val body = JSONObject().put("title", categoryTitle).put("image_path", categoryImage)
                    if (editingCategory == null) adminRequest("/api/admin/category", email, password, "POST", body.toString())
                    else adminRequest("/api/admin/category/${editingCategory!!.optString("id")}", email, password, "PUT", body.toString())
                    categoryDialog = false; refresh()
                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حفظ التصنيف" }
                busy = false
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text("إلغاء") } }
    )

    if (partDialog) AlertDialog(
        onDismissRequest = { partDialog = false }, title = { Text(if (editingPart == null) "إضافة فصل" else "تعديل الفصل") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(partTitle, { partTitle = it }, label = { Text("عنوان الفصل") }, modifier = Modifier.fillMaxWidth()); UploadRow("ملف MP3", partAudio, { pick("partAudio", true) }); UploadRow("صورة الفصل", partImage, { pick("partImage") }) } },
        confirmButton = { TextButton(enabled = !busy && partTitle.isNotBlank() && (partAudio.startsWith("audio/") || editingPart != null), onClick = {
            scope.launch {
                busy = true
                try {
                    val body = JSONObject().put("title", partTitle).put("audio_path", partAudio).put("image_path", partImage)
                    if (editingPart == null) adminRequest("/api/admin/story/${partStory!!.optString("id")}/parts", email, password, "POST", body.toString())
                    else adminRequest("/api/admin/part/${editingPart!!.optString("id")}", email, password, "PUT", body.toString())
                    partDialog = false; editingPart = null; parts = loadParts(partStory!!, email, password)
                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حفظ الفصل" }
                busy = false
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { partDialog = false }) { Text("إلغاء") } }
    )
}

private suspend fun loadParts(story: JSONObject, email: String, password: String): List<JSONObject> {
    return adminRequest("/api/admin/story/${story.optString("id")}/parts", email, password).optJSONArray("parts")?.objects() ?: emptyList()
}

@Composable
private fun UploadRow(label: String, value: String, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick) { Text(label) }
        Spacer(Modifier.width(8.dp))
        Text(if (value.isBlank()) "لم يتم الاختيار" else value.substringAfterLast('/'), color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}
