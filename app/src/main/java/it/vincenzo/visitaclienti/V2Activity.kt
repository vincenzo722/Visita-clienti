package it.vincenzo.visitaclienti

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class V2Activity : AppCompatActivity() {
    companion object {
        private const val REQ_LOCATION = 10
        private const val REQ_CAMERA = 20
        private const val REQ_CAMERA_PERMISSION = 21
        private const val REQ_AUDIO_PERMISSION = 22
        private const val REQ_EXPORT = 30
        private const val GREEN = 0xFF007A52.toInt()
        private const val GREEN2 = 0xFF00A84F.toInt()
        private const val BLUE = 0xFF2F80ED.toInt()
        private const val ORANGE = 0xFFFF9800.toInt()
        private const val RED = 0xFFE53935.toInt()
        private const val PURPLE = 0xFF7E57C2.toInt()
        private const val BG = 0xFFF6F7F8.toInt()
        private const val TEXT2 = 0xFF6B7280.toInt()
    }

    private val prefs by lazy { getSharedPreferences("visita_clienti_v2", MODE_PRIVATE) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var current: Location? = null
    private var pendingLocation: ((Location) -> Unit)? = null
    private var pendingPhoto: ((String) -> Unit)? = null
    private var pendingAudioStart: (() -> Unit)? = null
    private var recorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF006B48.toInt()
        showSplash()
        ensureLocationPermission()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)
    private fun bg(color: Int = Color.WHITE, radius: Int = 12, stroke: Int = 0xFFE3E6EA.toInt()) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke) }
    private fun buttonBg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
    private fun text(s: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply { this.text = s; textSize = size; setTextColor(0xFF202124.toInt()); if (bold) setTypeface(typeface, 1) }
    private fun action(s: String, color: Int = GREEN2) = Button(this).apply { text = s; setTextColor(Color.WHITE); textSize = 15f; isAllCaps = false; background = buttonBg(color) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = bg() }
    private fun edit(hint: String, lines: Int = 1) = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(14), dp(10), dp(14), dp(10)); background = bg(); if (lines > 1) { minLines = lines; gravity = Gravity.TOP } }

    private fun page(title: String, back: Boolean = true): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val bar = TextView(this).apply {
            text = (if (back) "‹   " else "☰   ") + title; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL; setTypeface(typeface, 1); setPadding(dp(16), 0, dp(16), 0); setBackgroundColor(GREEN)
            if (back) setOnClickListener { showHome() }
        }
        root.addView(bar, lp(h = dp(58)))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(18)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root); return body
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(45), dp(24), dp(24)); background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0C4A6E.toInt(), 0xFF17324F.toInt()))
        }
        root.addView(text("◉", 86f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER }, lp(h = dp(145)))
        root.addView(text("Visita Clienti", 40f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(text("La tua rete sul territorio", 18f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(text("\n✓  Registra i tuoi clienti\n✓  Trova i clienti vicini\n✓  Registra le visite\n✓  Prossima visita\n✓  Note vocali, foto e documenti\n✓  Storico completo", 17f).apply { setTextColor(Color.WHITE); setLineSpacing(dp(7).toFloat(), 1f) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(action("INIZIA", 0xFF00B853.toInt()).apply { setTypeface(typeface, 1); setOnClickListener { showHome() } }, lp(h = dp(58)).apply { setMargins(0, dp(8), 0, dp(12)) })
        root.addView(text("“Più visite, più opportunità”", 16f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, 2) })
        setContentView(root)
    }

    private fun showHome() {
        val body = page("Visita Clienti", false)
        fun tile(icon: String, title: String, click: () -> Unit) = card().apply { gravity = Gravity.CENTER; addView(text(icon, 34f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER }); addView(text(title, 16f, true).apply { gravity = Gravity.CENTER }); setOnClickListener { click() } }
        fun row(a: View, b: View) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(a, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }); addView(b, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
        body.addView(row(tile("👤+", "Nuovo Cliente") { showNewClient() }, tile("👥", "Elenco Clienti") { showClientList() }))
        body.addView(row(tile("📍", "Clienti Vicini") { showNearby() }, tile("▤", "Registra Visita") { showRegisterVisit() }))
        body.addView(row(tile("▥", "Statistiche") { showStats() }, tile("⚙", "Impostazioni") { showSettings() }))
        body.addView(action("📅  PROSSIME VISITE", PURPLE).apply { setOnClickListener { showUpcomingVisits() } }, lp(h = dp(54)).apply { setMargins(dp(4), dp(10), dp(4), 0) })
        val loc = card(); val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; h.addView(text("📍", 28f), lp(dp(46), dp(62))); val t = text("La tua posizione\nTocca per aggiornare", 15f, true).apply { setTextColor(BLUE) }; h.addView(t, LinearLayout.LayoutParams(0, -2, 1f)); h.addView(text("◎", 34f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER }, lp(dp(55), dp(55))); loc.addView(h); loc.setOnClickListener { getLocation { l -> current = l; t.text = "La tua posizione\n${"%.5f".format(Locale.ITALY, l.latitude)}, ${"%.5f".format(Locale.ITALY, l.longitude)}" } }; body.addView(loc, lp().apply { setMargins(dp(4), dp(12), dp(4), 0) })
        getLocation { l -> current = l; t.text = "La tua posizione\n${"%.5f".format(Locale.ITALY, l.latitude)}, ${"%.5f".format(Locale.ITALY, l.longitude)}" }
    }

    private fun clients(): JSONArray = try { JSONArray(prefs.getString("clients", "[]")) } catch (_: Exception) { JSONArray() }
    private fun visits(): JSONArray = try { JSONArray(prefs.getString("visits", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveClients(a: JSONArray) = prefs.edit().putString("clients", a.toString()).apply()
    private fun saveVisits(a: JSONArray) = prefs.edit().putString("visits", a.toString()).apply()
    private fun nextId(a: JSONArray): Long { var m = 0L; for (i in 0 until a.length()) m = max(m, a.optJSONObject(i)?.optLong("id") ?: 0); return m + 1 }
    private fun clientById(id: Long): JSONObject? { val a = clients(); for (i in 0 until a.length()) { val c = a.optJSONObject(i) ?: continue; if (c.optLong("id") == id) return c }; return null }

    private fun showNewClient(existing: JSONObject? = null) {
        val body = page(if (existing == null) "Nuovo Cliente" else "Modifica Cliente")
        val name = edit("Ragione Sociale *\nNome azienda o cliente"); val cat = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Cliente", "Potenziale", "Fornitore", "Altro")) }; val phone = edit("Telefono\nInserisci telefono"); val address = edit("Indirizzo\nInserisci indirizzo"); val notes = edit("Note\nNote aggiuntive", 3)
        var lat = existing?.optDouble("lat", 0.0) ?: 0.0; var lon = existing?.optDouble("lon", 0.0) ?: 0.0
        if (existing != null) { name.setText(existing.optString("name")); phone.setText(existing.optString("phone")); address.setText(existing.optString("address")); notes.setText(existing.optString("notes")); val values = arrayOf("Cliente", "Potenziale", "Fornitore", "Altro"); cat.setSelection(values.indexOf(existing.optString("category", "Cliente")).coerceAtLeast(0)) }
        listOf<View>(name, cat, phone, address, notes).forEach { body.addView(it, lp().apply { setMargins(0, 0, 0, dp(10)) }) }
        val coords = text(if (lat != 0.0 || lon != 0.0) "📍 Posizione attuale\n${"%.5f".format(Locale.ITALY, lat)}, ${"%.5f".format(Locale.ITALY, lon)}" else "📍 Posizione attuale\nNon acquisita", 15f, true).apply { setTextColor(BLUE) }; body.addView(coords)
        body.addView(action("◎  USA POSIZIONE ATTUALE", BLUE).apply { setOnClickListener { getLocation { l -> lat = l.latitude; lon = l.longitude; coords.text = "📍 Posizione attuale\n${"%.5f".format(Locale.ITALY, lat)}, ${"%.5f".format(Locale.ITALY, lon)}" } } }, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, dp(8)) })
        body.addView(action(if (existing == null) "SALVA CLIENTE" else "SALVA MODIFICHE").apply { setOnClickListener {
            if (name.text.isBlank()) { name.error = "Inserisci la ragione sociale"; return@setOnClickListener }
            val a = clients(); val obj = JSONObject().put("id", existing?.optLong("id") ?: nextId(a)).put("name", name.text.toString().trim()).put("category", cat.selectedItem.toString()).put("phone", phone.text.toString().trim()).put("address", address.text.toString().trim()).put("notes", notes.text.toString().trim()).put("lat", lat).put("lon", lon).put("created", existing?.optLong("created") ?: System.currentTimeMillis())
            if (existing == null) a.put(obj) else for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("id") == obj.optLong("id")) a.put(i, obj); saveClients(a); android.widget.Toast.makeText(this@V2Activity, "Cliente salvato", android.widget.Toast.LENGTH_SHORT).show(); showClientDetail(obj.optLong("id"))
        } }, lp(h = dp(56)))
    }

    private fun showClientList() {
        val body = page("Elenco Clienti"); val search = edit("⌕  Cerca cliente..."); body.addView(search, lp(h = dp(54))); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list); var filter = "Tutti"
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; body.addView(HorizontalScrollView(this).apply { addView(chips) }, 1, lp(h = dp(56)))
        fun refresh() { list.removeAllViews(); val q = search.text.toString().trim().lowercase(); val a = clients(); var n = 0; for (i in 0 until a.length()) { val c = a.getJSONObject(i); val cat = c.optString("category", "Cliente"); if (filter != "Tutti" && cat != filter) continue; if (q.isNotEmpty() && !c.optString("name").lowercase().contains(q) && !c.optString("address").lowercase().contains(q)) continue; list.addView(clientRow(c)); n++ }; if (n == 0) list.addView(text("Nessun cliente trovato.", 16f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(130))) }
        for (f in arrayOf("Tutti", "Cliente", "Potenziale", "Fornitore")) chips.addView(text(f, 14f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER; background = bg(); setPadding(dp(16), 0, dp(16), 0); setOnClickListener { filter = f; refresh() } }, LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(dp(2), dp(7), dp(4), dp(7)) })
        search.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}; override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { refresh() }; override fun afterTextChanged(e: android.text.Editable?) {} }); body.addView(action("＋  NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54))); refresh()
    }

    private fun clientRow(c: JSONObject): View {
        val box = card(); val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val cat = c.optString("category", "Cliente"); val col = when (cat) { "Potenziale" -> ORANGE; "Fornitore" -> BLUE; else -> GREEN2 }; h.addView(text("▦", 28f, true).apply { setTextColor(col) }, lp(dp(46), dp(58))); val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(c.optString("name"), 16f, true)); addView(text(c.optString("address", "Nessun indirizzo"), 13f).apply { setTextColor(TEXT2) }) }; h.addView(mid, LinearLayout.LayoutParams(0, -2, 1f)); h.addView(text(cat, 13f, true).apply { setTextColor(col) }); box.addView(h); box.setOnClickListener { showClientDetail(c.optLong("id")) }; box.layoutParams = lp().apply { setMargins(0, dp(6), 0, 0) }; return box
    }

    private fun showClientDetail(id: Long) {
        val c = clientById(id) ?: return showClientList(); val body = page("Dettaglio Cliente"); val box = card(); box.addView(text("▦  ${c.optString("name")}", 21f, true)); box.addView(text(c.optString("category", "Cliente"), 13f, true).apply { setTextColor(GREEN2) }); box.addView(text("☎  ${c.optString("phone", "—")}", 15f)); box.addView(text("📍  ${c.optString("address", "—")}", 15f)); box.addView(text("▤  ${c.optString("notes", "—")}", 15f)); body.addView(box)
        val next = nearestFutureVisitForClient(id); if (next != null) body.addView(card().apply { addView(text("📅 Prossima visita", 16f, true).apply { setTextColor(PURPLE) }); addView(text(formatDate(next.first), 18f, true)); if (next.second.isNotBlank()) addView(text(next.second, 14f).apply { setTextColor(TEXT2) }) }, lp().apply { setMargins(0, dp(10), 0, 0) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; row.addView(action("➤  NAVIGA", BLUE).apply { setOnClickListener { navigate(c) } }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), dp(10), dp(3), dp(10)) }); row.addView(action("☎  CHIAMA", GREEN2).apply { setOnClickListener { val p = c.optString("phone"); if (p.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(p)}"))) } }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(3), dp(10), dp(2), dp(10)) }); body.addView(row)
        body.addView(action("＋  REGISTRA VISITA").apply { setOnClickListener { showRegisterVisit(id) } }, lp(h = dp(54))); body.addView(action("✎  MODIFICA CLIENTE", BLUE).apply { setOnClickListener { showNewClient(c) } }, lp(h = dp(50)).apply { setMargins(0, dp(8), 0, 0) }); body.addView(text("Ultime visite", 18f, true).apply { setPadding(0, dp(18), 0, dp(8)) }); val v = visits(); var n = 0; for (i in v.length() - 1 downTo 0) { val x = v.getJSONObject(i); if (x.optLong("clientId") != id) continue; body.addView(visitRow(x)); n++ }; if (n == 0) body.addView(text("Nessuna visita registrata.", 14f).apply { setTextColor(TEXT2) })
    }

    private fun visitRow(v: JSONObject): View {
        val c = card(); c.addView(text("▣  ${formatDate(v.optLong("time"))}", 15f, true)); val o = v.optString("outcome"); c.addView(text("Esito: $o", 14f, true).apply { setTextColor(when (o) { "Da ricontattare" -> ORANGE; "Negativo" -> RED; "Informativo" -> BLUE; else -> GREEN2 }) }); if (v.optString("notes").isNotBlank()) c.addView(text(v.optString("notes"), 13f).apply { setTextColor(TEXT2) }); val nv = v.optLong("nextVisit"); if (nv > 0) c.addView(text("📅 Prossima visita: ${formatDate(nv)}", 14f, true).apply { setTextColor(PURPLE) }); val vp = v.optString("voicePath"); if (vp.isNotBlank()) c.addView(action("▶  ASCOLTA APPUNTO VOCALE", PURPLE).apply { setOnClickListener { playVoice(vp) } }, lp(h = dp(44)).apply { setMargins(0, dp(8), 0, 0) }); c.layoutParams = lp().apply { setMargins(0, 0, 0, dp(8)) }; return c
    }

    private fun showRegisterVisit(presetId: Long = 0L) {
        val body = page("Registra Visita"); val a = clients(); if (a.length() == 0) { body.addView(text("Prima inserisci almeno un cliente.", 16f).apply { setTextColor(TEXT2) }); body.addView(action("NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54))); return }
        val clientList = (0 until a.length()).map { a.getJSONObject(it) }; val sp = Spinner(this); sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, clientList.map { it.optString("name") }); if (presetId > 0) { val idx = clientList.indexOfFirst { it.optLong("id") == presetId }; if (idx >= 0) sp.setSelection(idx) }
        body.addView(text("Cliente", 14f, true)); body.addView(sp, lp().apply { setMargins(0, dp(4), 0, dp(12)) }); body.addView(text("Data e ora", 14f, true)); body.addView(text(formatDate(System.currentTimeMillis()), 16f).apply { background = bg(); setPadding(dp(12), dp(12), dp(12), dp(12)) }, lp().apply { setMargins(0, dp(4), 0, dp(12)) })
        val outcomes = arrayOf("Positivo", "Da ricontattare", "Negativo", "Informativo", "Contratto"); val out = Spinner(this); out.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, outcomes); body.addView(text("Esito visita", 14f, true)); body.addView(out, lp().apply { setMargins(0, dp(4), 0, dp(12)) })
        val notes = edit("Inserisci note sulla visita", 4); body.addView(text("Note", 14f, true)); body.addView(notes, lp().apply { setMargins(0, dp(4), 0, dp(8)) })

        var voicePath = ""; var recording = false; val voiceStatus = text("Nessun appunto vocale", 13f).apply { setTextColor(TEXT2) }; val voiceButton = action("🎙  REGISTRA APPUNTO VOCALE", PURPLE)
        voiceButton.setOnClickListener {
            if (!recording) {
                startVoiceRecording { path -> voicePath = path; recording = true; voiceButton.text = "■  FERMA REGISTRAZIONE"; voiceButton.background = buttonBg(RED); voiceStatus.text = "Registrazione in corso…"; voiceStatus.setTextColor(RED) }
            } else {
                stopVoiceRecording(); recording = false; voiceButton.text = "🎙  REGISTRA DI NUOVO"; voiceButton.background = buttonBg(PURPLE); voiceStatus.text = "Appunto vocale registrato"; voiceStatus.setTextColor(GREEN2)
            }
        }
        body.addView(voiceButton, lp(h = dp(54))); body.addView(voiceStatus, lp().apply { setMargins(0, dp(4), 0, dp(10)) })

        var nextVisit = 0L; val nextLabel = text("Nessuna prossima visita", 15f, true).apply { setTextColor(PURPLE) }; body.addView(text("Prossima visita", 14f, true)); body.addView(nextLabel, lp().apply { setMargins(0, dp(5), 0, dp(5)) }); val nextRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; nextRow.addView(action("📅  SCEGLI DATA E ORA", PURPLE).apply { setOnClickListener { chooseDateTime { t -> nextVisit = t; nextLabel.text = formatDate(t) } } }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(0, 0, dp(4), 0) }); nextRow.addView(action("AZZERA", 0xFF777777.toInt()).apply { setOnClickListener { nextVisit = 0L; nextLabel.text = "Nessuna prossima visita" } }, lp(dp(95), dp(50))); body.addView(nextRow, lp().apply { setMargins(0, 0, 0, dp(10)) })

        var photo64 = ""; val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }; body.addView(action("📷  AGGIUNGI FOTO", 0xFF444444.toInt()).apply { setOnClickListener { openCamera { b64 -> photo64 = b64; val bytes = Base64.decode(b64, Base64.DEFAULT); preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) } } }, lp(h = dp(54))); body.addView(preview, lp(h = dp(150)))
        body.addView(action("SALVA VISITA").apply { setOnClickListener {
            if (recording) { stopVoiceRecording(); recording = false }
            val cli = clientList[sp.selectedItemPosition]; val vs = visits(); vs.put(JSONObject().put("id", nextId(vs)).put("clientId", cli.optLong("id")).put("time", System.currentTimeMillis()).put("outcome", out.selectedItem.toString()).put("notes", notes.text.toString().trim()).put("photo", photo64).put("voicePath", voicePath).put("nextVisit", nextVisit)); saveVisits(vs); android.widget.Toast.makeText(this@V2Activity, "Visita salvata", android.widget.Toast.LENGTH_SHORT).show(); showClientDetail(cli.optLong("id"))
        } }, lp(h = dp(56)).apply { setMargins(0, dp(10), 0, 0) })
    }

    private fun showUpcomingVisits() {
        val body = page("Prossime Visite"); val items = mutableListOf<Triple<Long, JSONObject, String>>(); val now = System.currentTimeMillis(); val v = visits(); for (i in 0 until v.length()) { val x = v.getJSONObject(i); val t = x.optLong("nextVisit"); if (t > now) { val c = clientById(x.optLong("clientId")) ?: continue; items.add(Triple(t, c, x.optString("notes"))) } }; items.sortBy { it.first }
        if (items.isEmpty()) body.addView(text("Nessuna prossima visita programmata.", 16f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(150))) else items.forEach { item -> body.addView(card().apply { addView(text("📅 ${formatDate(item.first)}", 17f, true).apply { setTextColor(PURPLE) }); addView(text(item.second.optString("name"), 17f, true)); addView(text(item.second.optString("address"), 13f).apply { setTextColor(TEXT2) }); if (item.third.isNotBlank()) addView(text(item.third, 13f).apply { setTextColor(TEXT2) }); setOnClickListener { showClientDetail(item.second.optLong("id")) } }, lp().apply { setMargins(0, 0, 0, dp(8)) }) }
    }

    private fun nearestFutureVisitForClient(id: Long): Pair<Long, String>? { val now = System.currentTimeMillis(); var best = Long.MAX_VALUE; var note = ""; val v = visits(); for (i in 0 until v.length()) { val x = v.getJSONObject(i); if (x.optLong("clientId") != id) continue; val t = x.optLong("nextVisit"); if (t > now && t < best) { best = t; note = x.optString("notes") } }; return if (best == Long.MAX_VALUE) null else Pair(best, note) }

    private fun chooseDateTime(cb: (Long) -> Unit) {
        val cal = Calendar.getInstance(); DatePickerDialog(this, { _, y, m, d -> val temp = Calendar.getInstance().apply { set(y, m, d) }; TimePickerDialog(this, { _, hh, mm -> temp.set(Calendar.HOUR_OF_DAY, hh); temp.set(Calendar.MINUTE, mm); temp.set(Calendar.SECOND, 0); temp.set(Calendar.MILLISECOND, 0); cb(temp.timeInMillis) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private data class Near(val c: JSONObject, val meters: Float)
    private fun showNearby() {
        val body = page("Clienti Vicini"); val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; controls.addView(text("Raggio", 14f, true), LinearLayout.LayoutParams(0, dp(50), 1f)); val radius = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("500 m", "1 km", "3 km", "5 km", "10 km")); setSelection(3) }; controls.addView(radius, lp(dp(105), dp(50))); val search = action("CERCA", BLUE); controls.addView(search, lp(dp(100), dp(48))); body.addView(controls); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list)
        fun r(): Double = when (radius.selectedItemPosition) { 0 -> 500.0; 1 -> 1000.0; 2 -> 3000.0; 4 -> 10000.0; else -> 5000.0 }
        fun refresh(l: Location) { list.removeAllViews(); val found = mutableListOf<Near>(); val a = clients(); for (i in 0 until a.length()) { val c = a.getJSONObject(i); val lat = c.optDouble("lat"); val lon = c.optDouble("lon"); if (lat == 0.0 && lon == 0.0) continue; val d = FloatArray(1); Location.distanceBetween(l.latitude, l.longitude, lat, lon, d); if (d[0] <= r()) found.add(Near(c, d[0])) }; found.sortBy { it.meters }; if (found.isEmpty()) list.addView(text("Nessun cliente nel raggio selezionato.", 15f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(120))) else found.forEach { n -> list.addView(card().apply { val h = LinearLayout(this@V2Activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; h.addView(text("📍", 25f), lp(dp(42), dp(60))); val mid = LinearLayout(this@V2Activity).apply { orientation = LinearLayout.VERTICAL; addView(text(n.c.optString("name"), 15f, true)); addView(text(n.c.optString("address"), 12f).apply { setTextColor(TEXT2) }) }; h.addView(mid, LinearLayout.LayoutParams(0, -2, 1f)); h.addView(text(if (n.meters < 1000) "${n.meters.roundToInt()} m" else "%.1f km".format(Locale.ITALY, n.meters / 1000f), 14f, true)); addView(h); setOnClickListener { showClientDetail(n.c.optLong("id")) } }, lp().apply { setMargins(0, dp(5), 0, 0) }) } }
        search.setOnClickListener { getLocation { l -> current = l; refresh(l) } }; getLocation { l -> current = l; refresh(l) }
    }

    private fun showStats() {
        val body = page("Statistiche"); val v = visits(); val c = clients(); var rec = 0; var contracts = 0; var pos = 0; var neg = 0; for (i in 0 until v.length()) when (v.getJSONObject(i).optString("outcome")) { "Da ricontattare" -> rec++; "Contratto" -> { contracts++; pos++ }; "Positivo" -> pos++; "Negativo" -> neg++ }; fun stat(n: Int, s: String, col: Int) = card().apply { gravity = Gravity.CENTER; addView(text(n.toString(), 28f, true).apply { setTextColor(col); gravity = Gravity.CENTER }); addView(text(s, 13f, true).apply { gravity = Gravity.CENTER }) }; val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(stat(v.length(), "Visite effettuate", BLUE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }); addView(stat(c.length(), "Clienti", GREEN2), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }; val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(stat(rec, "Da ricontattare", ORANGE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }); addView(stat(contracts, "Contratti", PURPLE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }; body.addView(r1); body.addView(r2); body.addView(text("Esiti visite\n\nPositivi: $pos\nDa ricontattare: $rec\nNegativi: $neg\nContratti: $contracts", 16f, true).apply { setPadding(dp(10), dp(18), dp(10), dp(10)) })
    }

    private fun showSettings() {
        val body = page("Impostazioni"); body.addView(text("Visita Clienti v2.1", 21f, true)); body.addView(text("Include prossime visite e appunti vocali registrati direttamente dalla scheda visita.", 14f).apply { setTextColor(TEXT2); setPadding(0, dp(8), 0, dp(18)) }); body.addView(action("ESPORTA CLIENTI IN CSV", BLUE).apply { setOnClickListener { val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "visita_clienti.csv") }; startActivityForResult(i, REQ_EXPORT) } }, lp(h = dp(56)))
    }

    private fun formatDate(t: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(t))
    private fun navigate(c: JSONObject) { val lat = c.optDouble("lat"); val lon = c.optDouble("lon"); if (lat == 0.0 && lon == 0.0) { android.widget.Toast.makeText(this, "Posizione GPS non registrata", android.widget.Toast.LENGTH_SHORT).show(); return }; var i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).apply { setPackage("com.google.android.apps.maps") }; if (i.resolveActivity(packageManager) == null) i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(c.optString("name"))})")); startActivity(i) }

    private fun startVoiceRecording(started: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { pendingAudioStart = { startVoiceRecording(started) }; ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO_PERMISSION); return }
        try { val dir = File(filesDir, "voice_notes").apply { mkdirs() }; val file = File(dir, "nota_${System.currentTimeMillis()}.m4a"); recorder = MediaRecorder().apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }; started(file.absolutePath) } catch (e: Exception) { recorder?.release(); recorder = null; android.widget.Toast.makeText(this, "Registrazione non disponibile: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
    }
    private fun stopVoiceRecording() { try { recorder?.stop() } catch (_: Exception) {}; try { recorder?.release() } catch (_: Exception) {}; recorder = null }
    private fun playVoice(path: String) { val f = File(path); if (!f.exists()) { android.widget.Toast.makeText(this, "Appunto vocale non trovato", android.widget.Toast.LENGTH_SHORT).show(); return }; try { val mp = MediaPlayer(); mp.setDataSource(path); mp.prepare(); mp.start(); mp.setOnCompletionListener { it.release() } } catch (e: Exception) { android.widget.Toast.makeText(this, "Impossibile riprodurre l'audio", android.widget.Toast.LENGTH_SHORT).show() } }

    private fun ensureLocationPermission() { if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION) }
    private fun getLocation(cb: (Location) -> Unit) { if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { pendingLocation = cb; ensureLocationPermission(); return }; fused.lastLocation.addOnSuccessListener { last -> if (last != null) cb(last) else fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).addOnSuccessListener { l -> if (l != null) cb(l) else android.widget.Toast.makeText(this, "Posizione non disponibile. Attiva il GPS.", android.widget.Toast.LENGTH_LONG).show() } } }
    private fun openCamera(cb: (String) -> Unit) { pendingPhoto = cb; if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION); return }; val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE); if (i.resolveActivity(packageManager) != null) startActivityForResult(i, REQ_CAMERA) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) { val cb = pendingLocation; pendingLocation = null; if (cb != null) getLocation(cb) }; if (requestCode == REQ_CAMERA_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pendingPhoto?.let { openCamera(it) }; if (requestCode == REQ_AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { val a = pendingAudioStart; pendingAudioStart = null; a?.invoke() } }
    private fun csvQuote(s: String) = "\"${s.replace("\"", "\"\"").replace("\n", " ")}\""
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) { val bm = data?.extras?.get("data") as? Bitmap; if (bm != null) { val out = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 75, out); pendingPhoto?.invoke(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)) } }; if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data?.data != null) try { contentResolver.openOutputStream(data.data!!)?.use { out -> val sb = StringBuilder("Ragione sociale;Categoria;Telefono;Indirizzo;Note;Latitudine;Longitudine\n"); val a = clients(); for (i in 0 until a.length()) { val c = a.getJSONObject(i); sb.append(csvQuote(c.optString("name"))).append(';').append(csvQuote(c.optString("category"))).append(';').append(csvQuote(c.optString("phone"))).append(';').append(csvQuote(c.optString("address"))).append(';').append(csvQuote(c.optString("notes"))).append(';').append(c.optDouble("lat")).append(';').append(c.optDouble("lon")).append('\n') }; out.write(sb.toString().toByteArray()) }; android.widget.Toast.makeText(this, "Esportazione completata", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) { android.widget.Toast.makeText(this, "Errore esportazione", android.widget.Toast.LENGTH_SHORT).show() } }
    override fun onBackPressed() { stopVoiceRecording(); showHome() }
    override fun onDestroy() { stopVoiceRecording(); super.onDestroy() }
}
