package com.kamalesh.docvault

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var category: Spinner
    private val categories = listOf("Aadhaar", "PAN", "Bank", "Education", "Medical", "Bills", "Other")
    private var pendingPhoto: File? = null
    private val docsDir get() = File(filesDir, "documents")
    private val dateFmt = SimpleDateFormat("yyyy/MMMM", Locale.ENGLISH)

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val f = pendingPhoto
            if (f != null && f.exists()) {
                val folder = File(docsDir, "${dateFmt.format(Date())}/${safe(category.selectedItem.toString())}")
                folder.mkdirs()
                val dest = File(folder, "${stamp()}_${f.name}")
                f.copyTo(dest, overwrite = true); f.delete()
                Toast.makeText(this, "Saved: ${dest.name}", Toast.LENGTH_LONG).show(); refreshList()
            }
        } else pendingPhoto?.delete()
    }

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { out -> backupZip(out) } }
            .onSuccess { toast("Backup created. You can save it in Google Drive.") }
            .onFailure { toast("Backup failed: ${it.message}") }
    }
    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { contentResolver.openInputStream(uri)?.use { restoreZip(it) } }
            .onSuccess { toast("Restore completed"); refreshList() }
            .onFailure { toast("Restore failed: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        docsDir.mkdirs()
        authenticate { showHome() }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            AlertDialog.Builder(this).setTitle("Fingerprint setup required")
                .setMessage("DocVault is locked until a strong fingerprint is enrolled on this phone. Add a fingerprint in Android Settings, then reopen the app.")
                .setPositiveButton("Open Settings") { _, _ ->
                    runCatching { startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
                    finish()
                }.setOnCancelListener { finish() }.show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock DocVault").setSubtitle("Verify your fingerprint to access personal documents")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build())
    }

    private fun showHome() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 26, 22, 18); setBackgroundColor(Color.rgb(246,248,252)) }
        val title = TextView(this).apply { text = "DocVault"; textSize = 28f; setTextColor(Color.rgb(24,42,72)); paint.isFakeBoldText = true }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Your private document cabinet"; textSize = 14f; setTextColor(Color.DKGRAY) })
        category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories) }
        root.addView(label("Save new scan in category")); root.addView(category)
        addButton("📷  Scan / Capture document", Color.rgb(32,103,190)) { capture() }
        addButton("📁  Browse saved documents", Color.rgb(52,83,122)) { refreshList() }
        addButton("📄  Create PDF from selected images", Color.rgb(52,122,92)) { chooseImagesForPdf() }
        addButton("☁  Create manual backup (Google Drive)", Color.rgb(103,83,160)) { createBackup.launch("DocVault_Backup_${stamp()}.zip") }
        addButton("↩  Restore backup from Google Drive", Color.rgb(103,83,160)) { restoreBackup.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
        addButton("ⓘ  Backup & security help", Color.GRAY) { help() }
        val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll)
        refreshList()
    }

    private fun label(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0,18,0,4) }
    private fun addButton(text: String, color: Int, action: () -> Unit) {
        val b = Button(this).apply { this.text = text; setTextColor(Color.WHITE); setBackgroundColor(color); isAllCaps = false; textSize = 15f }
        root.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7; bottomMargin = 3 }); b.setOnClickListener { action() }
    }
    private fun capture() {
        val dir = File(cacheDir, "capture").apply { mkdirs() }
        pendingPhoto = File(dir, "scan_${stamp()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", pendingPhoto!!)
        takePhoto.launch(uri)
    }
    private fun refreshList() {
        if (!::root.isInitialized) return
        root.findViewWithTag<LinearLayout>("doc_list")?.let { root.removeView(it) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "doc_list"; setPadding(0,14,0,0) }
        list.addView(label("RECENT DOCUMENTS"))
        val files = docsDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") }.sortedByDescending { it.lastModified() }.take(30).toList()
        if (files.isEmpty()) list.addView(TextView(this).apply { text = "No documents yet. Tap Scan to capture your first document."; setPadding(0,12,0,12); setTextColor(Color.DKGRAY) })
        files.forEach { f ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10,9,10,9); setBackgroundColor(Color.WHITE) }
            row.addView(TextView(this).apply { text = f.name; textSize = 15f; setTextColor(Color.BLACK) })
            row.addView(TextView(this).apply { text = f.parentFile?.relativeTo(docsDir)?.invariantSeparatorsPath ?: ""; textSize = 12f; setTextColor(Color.GRAY) })
            row.setOnClickListener { openImage(f) }; list.addView(row, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = 6 })
        }
        root.addView(list)
    }
    private fun openImage(f: File) {
        AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Create PDF", "Share image", "Delete")) { _, which ->
            when(which) { 0 -> makePdf(listOf(f)); 1 -> shareFile(f, "image/jpeg"); 2 -> AlertDialog.Builder(this).setMessage("Delete this document?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> f.delete(); refreshList() }.show() }
        }.show()
    }
    private fun chooseImagesForPdf() {
        val all = docsDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in listOf("jpg","jpeg","png") }.toList()
        if (all.isEmpty()) { toast("Capture a document first"); return }
        val checked = BooleanArray(all.size) { false }
        AlertDialog.Builder(this).setTitle("Select pages for PDF").setMultiChoiceItems(all.map { it.name }.toTypedArray(), checked) { _, i, yes -> checked[i] = yes }
            .setNegativeButton("Cancel", null).setPositiveButton("Create PDF") { _, _ -> val selected = all.filterIndexed { i, _ -> checked[i] }; if (selected.isNotEmpty()) makePdf(selected) else toast("No pages selected") }.show()
    }
    private fun makePdf(files: List<File>) {
        try {
            val pdf = PdfDocument()
            files.forEachIndexed { index, file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(595,842,index+1).create(); val page = pdf.startPage(pageInfo)
                val margin = 24f; val maxW = 547f; val maxH = 794f
                val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                val w = bitmap.width * scale; val h = bitmap.height * scale
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                page.canvas.drawBitmap(bitmap, null, android.graphics.RectF((595-w)/2, (842-h)/2, (595+w)/2, (842+h)/2), paint)
                pdf.finishPage(page); bitmap.recycle()
            }
            val out = File(cacheDir, "DocVault_${stamp()}.pdf"); FileOutputStream(out).use { pdf.writeTo(it) }; pdf.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", out)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { startActivity(Intent.createChooser(intent,"Open PDF / Print")) } catch (_: Exception) { shareFile(out,"application/pdf") }
        } catch (e: Exception) { toast("PDF error: ${e.message}") }
    }
    private fun shareFile(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share document"))
    }
    private fun backupZip(output: OutputStream) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            docsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(docsDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("documents/$name")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("BACKUP_INFO.txt")); zip.write("DocVault manual backup\nCreated: ${Date()}\n".toByteArray()); zip.closeEntry()
        }
    }
    private fun restoreZip(input: InputStream) {
        val base = docsDir.canonicalFile
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.startsWith("documents/")) {
                    val target = File(base, entry.name.removePrefix("documents/")).canonicalFile
                    require(target.path.startsWith(base.path + File.separator)) { "Invalid backup path" }
                    target.parentFile?.mkdirs(); FileOutputStream(target).use { zip.copyTo(it) }
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
    }
    private fun help() {
        AlertDialog.Builder(this).setTitle("Manual backup & security")
            .setMessage("Backup: tap Create manual backup, then choose Google Drive in the Android file picker and save the ZIP. Restore: choose that ZIP from Google Drive. Backups are manual; remember to create one before changing phones or uninstalling.\n\nFingerprint: the app asks for biometric/device credential verification when opened. Keep your phone's screen lock enabled. App data is stored in private app storage.")
            .setPositiveButton("OK",null).show()
    }
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"),"_")
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun toast(s: String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
