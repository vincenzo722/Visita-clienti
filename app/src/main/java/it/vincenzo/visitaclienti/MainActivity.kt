package it.vincenzo.visitaclienti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQ_LOCATION = 10
        private const val REQ_CAMERA = 20
        private const val REQ_CAMERA_PERMISSION = 21
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
    private var pendingLocationAction: ((Location) -> Unit)? = null
    private var pendingPhotoCallback: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF006B48.toInt()
        showSplash()
        ensureLocationPermission()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)
    private fun cardBg(color: Int = Color.WHITE, radius: Int = 12, stroke: Int = 0xFFE3E6EA.toInt()) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun buttonBg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
    private fun label(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(0xFF202124.toInt()); if (bold) setTypeface(typeface, 1)
    }
    private fun action(text: String, color: Int = GREEN2) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 15f; isAllCaps = false; background = buttonBg(color)
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = cardBg()
    }
    private fun page(title: String, back: Boolean = true): Pair<LinearLayout, LinearLayout> {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val bar = TextView(this).apply {
            text = (if (back) "‹   " else "☰   ") + title; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, 1); setPadding(dp(16), 0, dp(16), 0); setBackgroundColor(GREEN); if (back) setOnClickListener { showHome() }
        }
        root.addView(bar, lp(h = dp(58)))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(18)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        return Pair(root, body)
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(45), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0C4A6E.toInt(), 0xFF17324F.toInt()))
        }
        root.addView(label("◉", 86f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER }, lp(h = dp(150)))
        root.addView(label("Visita Clienti", 40f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(label("La tua rete sul territorio", 18f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(label("\n✓  Registra i tuoi clienti\n✓  Trova i clienti vicini\n✓  Registra le visite\n✓  Note, foto e documenti\n✓  Storico completo", 17f).apply {
            setTextColor(Color.WHITE); setLineSpacing(dp(7).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(action("INIZIA", 0xFF00B853.toInt()).apply { setTypeface(typeface, 1); setOnClickListener { showHome() } }, lp(h = dp(58)).apply { setMargins(0, dp(8), 0, dp(12)) })
        root.addView(label("“Più visite, più opportunità”", 16f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, 2) })
        setContentView(root)
    }

    private fun showHome() {
        val (_, body) = page("Visita Clienti", false)
        fun tile(icon: String, text: String, click: () -> Unit) = card().apply {
            gravity = Gravity.CENTER; addView(label(icon, 34f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER }); addView(label(text, 16f, true).apply { gravity = Gravity.CENTER }); setOnClickListener { click() }
        }
        fun row(a: View, b: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            addView(b, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        body.addView(row(tile("👤+", "Nuovo Cliente") { showNewClient() }, tile("👥", "Elenco Clienti") { showClientList() }))
        body.addView(row(tile("📍", "Clienti Vicini") { showNearby() }, tile("▤", "Registra Visita") { showRegisterVisit() }))
        body.addView(row(tile("▥", "Statistiche") { showStats() }, tile("⚙", "Impostazioni") { showSettings() }))
        val loc = card(); val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        h.addView(label("📍", 28f), lp(dp(46), dp(62))); val txt = label("La tua posizione\nTocca per aggiornare", 15f, true).apply { setTextColor(BLUE) }
        h.addView(txt, LinearLayout.LayoutParams(0, -2, 1f)); h.addView(label("◎", 34f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER }, lp(dp(55), dp(55))); loc.addView(h)
        loc.setOnClickListener { getLocation { l -> current = l; txt.text = "La tua posizione\n${"%.5f".format(Locale.ITALY, l.latitude)}, ${"%.5f".format(Locale.ITALY, l.longitude)}" } }
        body.addView(loc, lp().apply { setMargins(dp(4), dp(12), dp(4), 0) })
        getLocation { l -> current = l; txt.text = "La tua posizione\n${"%.5f".format(Locale.ITALY, l.latitude)}, ${"%.5f".format(Locale.ITALY, l.longitude)}" }
    }

    private fun clients(): JSONArray = try { JSONArray(prefs.getString("clients", "[]")) } catch (_: Exception) { JSONArray() }
    private fun visits(): JSONArray = try { JSONArray(prefs.getString("visits", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveClients(a: JSONArray) = prefs.edit().putString("clients", a.toString()).apply()
    private fun saveVisits(a: JSONArray) = prefs.edit().putString("visits", a.toString()).apply()
    private fun nextId(a: JSONArray): Long { var m = 0L; for (i in 0 until a.length()) m = max(m, a.optJSONObject(i)?.optLong("id") ?: 0); return m + 1 }
    private fun clientById(id: Long): JSONObject? { val a = clients(); for (i in 0 until a.length()) { val c = a.optJSONObject(i) ?: continue; if (c.optLong("id") == id) return c }; return null }
    private fun editField(hint: String, lines: Int = 1) = EditText(this).apply {
        this.hint = hint; textSize = 16f; setPadding(dp(14), dp(10), dp(14), dp(10)); background = cardBg(); if (lines > 1) { minLines = lines; gravity = Gravity.TOP }
    }

    private fun showNewClient(existing: JSONObject? = null) {
        val (_, body) = page(if (existing == null) "Nuovo Cliente" else "Modifica Cliente")
        val name = editField("Ragione Sociale *\nNome azienda o cliente")
        val cat = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Cliente", "Potenziale", "Fornitore", "Altro")) }
        val phone = editField("Telefono\nInserisci telefono"); val address = editField("Indirizzo\nInserisci indirizzo"); val notes = editField("Note\nNote aggiuntive", 3)
        var lat = existing?.optDouble("lat", 0.0) ?: 0.0; var lon = existing?.optDouble("lon", 0.0) ?: 0.0
        if (existing != null) {
            name.setText(existing.optString("name")); phone.setText(existing.optString("phone")); address.setText(existing.optString("address")); notes.setText(existing.optString("notes"))
            val values = arrayOf("Cliente", "Potenziale", "Fornitore", "Altro"); cat.setSelection(values.indexOf(existing.optString("category", "Cliente")).coerceAtLeast(0))
        }
        listOf<View>(name, cat, phone, address, notes).forEach { body.addView(it, lp().apply { setMargins(0, 0, 0, dp(10)) }) }
        val coords = label(if (lat != 0.0 || lon != 0.0) "📍 Posizione attuale\n${"%.5f".format(Locale.ITALY, lat)}, ${"%.5f".format(Locale.ITALY, lon)}" else "📍 Posizione attuale\nNon acquisita", 15f, true).apply { setTextColor(BLUE) }
        body.addView(coords, lp().apply { setMargins(0, 0, 0, dp(8)) })
        body.addView(action("◎  USA POSIZIONE ATTUALE", BLUE).apply {
            setOnClickListener { getLocation { l ->
                lat = l.latitude; lon = l.longitude; coords.text = "📍 Posizione attuale\n${"%.5f".format(Locale.ITALY, lat)}, ${"%.5f".format(Locale.ITALY, lon)}"
                if (address.text.isBlank()) try { val r = Geocoder(this@MainActivity, Locale.ITALY).getFromLocation(lat, lon, 1); if (!r.isNullOrEmpty()) address.setText(r[0].getAddressLine(0)) } catch (_: Exception) {}
            } }
        }, lp(h = dp(54)).apply { setMargins(0, 0, 0, dp(8)) })
        body.addView(action(if (existing == null) "SALVA CLIENTE" else "SALVA MODIFICHE").apply {
            setOnClickListener {
                if (name.text.isBlank()) { name.error = "Inserisci la ragione sociale"; return@setOnClickListener }
                val a = clients(); val obj = JSONObject().put("id", existing?.optLong("id") ?: nextId(a)).put("name", name.text.toString().trim()).put("category", cat.selectedItem.toString()).put("phone", phone.text.toString().trim()).put("address", address.text.toString().trim()).put("notes", notes.text.toString().trim()).put("lat", lat).put("lon", lon).put("created", existing?.optLong("created") ?: System.currentTimeMillis())
                if (existing == null) a.put(obj) else for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("id") == obj.optLong("id")) a.put(i, obj)
                saveClients(a); Toast.makeText(this, "Cliente salvato", Toast.LENGTH_SHORT).show(); showClientDetail(obj.optLong("id"))
            }
        }, lp(h = dp(56)))
    }

    private fun showClientList() {
        val (_, body) = page("Elenco Clienti"); val search = editField("⌕  Cerca cliente..."); body.addView(search, lp(h = dp(54)))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; body.addView(HorizontalScrollView(this).apply { addView(chips) }, lp(h = dp(56)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list); var filter = "Tutti"
        fun refresh() {
            list.removeAllViews(); val q = search.text.toString().trim().lowercase(); val a = clients(); var found = 0
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i); val cat = c.optString("category", "Cliente"); if (filter != "Tutti" && cat != filter) continue
                if (q.isNotEmpty() && !c.optString("name").lowercase().contains(q) && !c.optString("address").lowercase().contains(q) && !c.optString("phone").contains(q)) continue
                found++; list.addView(clientRow(c))
            }
            if (found == 0) list.addView(label("Nessun cliente trovato.\nTocca “Nuovo Cliente” per iniziare.", 16f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(150)))
        }
        for (f in arrayOf("Tutti", "Cliente", "Potenziale", "Fornitore")) chips.addView(TextView(this).apply {
            text = f; textSize = 14f; setTextColor(BLUE); gravity = Gravity.CENTER; setTypeface(typeface, 1); background = cardBg(); setPadding(dp(16), 0, dp(16), 0); setOnClickListener { filter = f; refresh() }
        }, LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(dp(2), dp(7), dp(4), dp(7)) })
        search.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}; override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { refresh() }; override fun afterTextChanged(e: android.text.Editable?) {} })
        body.addView(action("＋  NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, 0) }); refresh()
    }

    private fun clientRow(c: JSONObject): View {
        val box = card(); val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val cat = c.optString("category", "Cliente")
        val col = when (cat) { "Potenziale" -> ORANGE; "Fornitore" -> BLUE; else -> GREEN2 }
        h.addView(label("▦", 28f, true).apply { setTextColor(col) }, lp(dp(46), dp(58)))
        val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(c.optString("name"), 16f, true)); addView(label(c.optString("address", "Nessun indirizzo"), 13f).apply { setTextColor(TEXT2) }) }
        h.addView(txt, LinearLayout.LayoutParams(0, -2, 1f)); h.addView(label(cat, 13f, true).apply { setTextColor(col) }); box.addView(h); box.setOnClickListener { showClientDetail(c.optLong("id")) }; box.layoutParams = lp().apply { setMargins(0, dp(6), 0, 0) }; return box
    }

    private fun showClientDetail(id: Long) {
        val c = clientById(id) ?: return showClientList(); val (_, body) = page("Dettaglio Cliente"); val box = card()
        box.addView(label("▦  ${c.optString("name")}", 21f, true)); box.addView(label(c.optString("category", "Cliente"), 13f, true).apply { setTextColor(GREEN2) })
        box.addView(label("☎  ${c.optString("phone", "—")}", 15f)); box.addView(label("📍  ${c.optString("address", "—")}", 15f)); box.addView(label("⌖  ${if (c.optDouble("lat") != 0.0 || c.optDouble("lon") != 0.0) "%.5f, %.5f".format(Locale.ITALY, c.optDouble("lat"), c.optDouble("lon")) else "Posizione non registrata"}", 15f)); box.addView(label("▤  ${c.optString("notes", "—")}", 15f)); body.addView(box)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(action("➤  NAVIGA", BLUE).apply { setOnClickListener { navigate(c) } }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), dp(10), dp(3), dp(10)) })
        row.addView(action("☎  CHIAMA", GREEN2).apply { setOnClickListener { val p = c.optString("phone"); if (p.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(p)}"))) } }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(3), dp(10), dp(2), dp(10)) }); body.addView(row)
        body.addView(action("✎  MODIFICA CLIENTE", BLUE).apply { setOnClickListener { showNewClient(c) } }, lp(h = dp(50))); body.addView(action("＋  REGISTRA VISITA").apply { setOnClickListener { showRegisterVisit(id) } }, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, 0) })
        body.addView(label("Ultime visite", 18f, true).apply { setPadding(0, dp(18), 0, dp(8)) }); val v = visits(); var n = 0
        for (i in v.length() - 1 downTo 0) { val x = v.getJSONObject(i); if (x.optLong("clientId") != id) continue; n++; body.addView(visitRow(x)) }
        if (n == 0) body.addView(label("Nessuna visita registrata.", 14f).apply { setTextColor(TEXT2) })
    }

    private fun visitRow(v: JSONObject): View {
        val c = card(); c.addView(label("▣  ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(v.optLong("time")))}", 15f, true)); val o = v.optString("outcome")
        c.addView(label("Esito: $o", 14f, true).apply { setTextColor(when (o) { "Da ricontattare" -> ORANGE; "Negativo" -> RED; "Informativo" -> BLUE; else -> GREEN2 }) })
        if (v.optString("notes").isNotBlank()) c.addView(label(v.optString("notes"), 13f).apply { setTextColor(TEXT2) }); c.layoutParams = lp().apply { setMargins(0, 0, 0, dp(8)) }; return c
    }

    private fun showRegisterVisit(presetId: Long = 0L) {
        val (_, body) = page("Registra Visita"); val a = clients(); if (a.length() == 0) { body.addView(label("Prima inserisci almeno un cliente.", 16f).apply { setTextColor(TEXT2) }); body.addView(action("NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54))); return }
        val clientList = (0 until a.length()).map { a.getJSONObject(it) }; val sp = Spinner(this); sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, clientList.map { it.optString("name") }); if (presetId > 0) { val idx = clientList.indexOfFirst { it.optLong("id") == presetId }; if (idx >= 0) sp.setSelection(idx) }
        body.addView(label("Cliente", 14f, true)); body.addView(sp, lp().apply { setMargins(0, dp(4), 0, dp(12)) }); body.addView(label("Data e ora", 14f, true)); body.addView(label(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date()), 16f).apply { background = cardBg(); setPadding(dp(12), dp(12), dp(12), dp(12)) }, lp().apply { setMargins(0, dp(4), 0, dp(12)) })
        val outcomes = arrayOf("Positivo", "Da ricontattare", "Negativo", "Informativo", "Contratto"); val out = Spinner(this); out.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, outcomes); body.addView(label("Esito visita", 14f, true)); body.addView(out, lp().apply { setMargins(0, dp(4), 0, dp(12)) })
        val notes = editField("Inserisci note sulla visita", 4); body.addView(label("Note", 14f, true)); body.addView(notes, lp().apply { setMargins(0, dp(4), 0, dp(12)) }); var photo64 = ""; val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        body.addView(action("📷  AGGIUNGI FOTO", 0xFF444444.toInt()).apply { setOnClickListener { openCamera { b64 -> photo64 = b64; val bytes = Base64.decode(b64, Base64.DEFAULT); preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) } } }, lp(h = dp(54))); body.addView(preview, lp(h = dp(170)))
        body.addView(action("SALVA VISITA").apply { setOnClickListener { val c = clientList[sp.selectedItemPosition]; val vs = visits(); vs.put(JSONObject().put("id", nextId(vs)).put("clientId", c.optLong("id")).put("time", System.currentTimeMillis()).put("outcome", out.selectedItem.toString()).put("notes", notes.text.toString().trim()).put("photo", photo64)); saveVisits(vs); Toast.makeText(this, "Visita salvata", Toast.LENGTH_SHORT).show(); showClientDetail(c.optLong("id")) } }, lp(h = dp(56)).apply { setMargins(0, dp(10), 0, 0) })
    }

    private data class Near(val c: JSONObject, val meters: Float)
    private fun showNearby() {
        val (_, body) = page("Clienti Vicini"); val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(label("Raggio di ricerca", 14f, true), LinearLayout.LayoutParams(0, dp(50), 1f)); val radius = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("500 m", "1 km", "3 km", "5 km", "10 km")); setSelection(3) }; controls.addView(radius, lp(dp(105), dp(50))); val search = action("CERCA", BLUE); controls.addView(search, lp(dp(100), dp(48))); body.addView(controls)
        val map = MapPlotView(); body.addView(map, lp(h = dp(260))); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(list)
        fun radiusM(): Double = when (radius.selectedItemPosition) { 0 -> 500.0; 1 -> 1000.0; 2 -> 3000.0; 4 -> 10000.0; else -> 5000.0 }
        fun refreshNearby(location: Location) {
            list.removeAllViews(); val found = mutableListOf<Near>(); val pts = mutableListOf<PlotPoint>(); pts.add(PlotPoint(0.0, 0.0, true)); val a = clients()
            for (i in 0 until a.length()) { val c = a.getJSONObject(i); val lat = c.optDouble("lat"); val lon = c.optDouble("lon"); if (lat == 0.0 && lon == 0.0) continue; val d = FloatArray(1); Location.distanceBetween(location.latitude, location.longitude, lat, lon, d); if (d[0] <= radiusM()) { found.add(Near(c, d[0])); val dx = (lon - location.longitude) * 111320 * cos(Math.toRadians(location.latitude)); val dy = (lat - location.latitude) * 110540; pts.add(PlotPoint(dx, dy, false)) } }
            map.points = pts; map.invalidate(); found.sortBy { it.meters }
            if (found.isEmpty()) list.addView(label("Nessun cliente con posizione GPS nel raggio selezionato.", 15f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(120))) else found.forEach { near ->
                val c = card(); val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; h.addView(label("📍", 25f).apply { setTextColor(GREEN2) }, lp(dp(42), dp(60))); val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label(near.c.optString("name"), 15f, true)); addView(label(near.c.optString("address"), 12f).apply { setTextColor(TEXT2) }) }; h.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); h.addView(label(if (near.meters < 1000) "${near.meters.roundToInt()} m" else "%.1f km".format(Locale.ITALY, near.meters / 1000f), 14f, true)); h.addView(label("  ➤", 26f, true).apply { setTextColor(GREEN2); setOnClickListener { navigate(near.c) } }); c.addView(h); c.setOnClickListener { showClientDetail(near.c.optLong("id")) }; list.addView(c, lp().apply { setMargins(0, dp(5), 0, 0) })
            }
        }
        search.setOnClickListener { getLocation { l -> current = l; refreshNearby(l) } }; getLocation { l -> current = l; refreshNearby(l) }
    }

    private data class PlotPoint(val dx: Double, val dy: Double, val current: Boolean)
    private inner class MapPlotView : View(this) {
        var points: List<PlotPoint> = emptyList(); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) { super.onDraw(c); c.drawColor(0xFFEEF4EF.toInt()); p.color = 0xFFD7E2D8.toInt(); p.strokeWidth = 3f; for (i in 1..5) { val y = height * i / 6f; c.drawLine(0f, y, width.toFloat(), y, p); val x = width * i / 6f; c.drawLine(x, 0f, x, height.toFloat(), p) }; p.textSize = 22f; p.typeface = Typeface.DEFAULT_BOLD; p.color = 0xFF5F6E64.toInt(); c.drawText("MAPPA CLIENTI VICINI", 18f, 32f, p); var m = 1.0; points.forEach { m = max(m, max(abs(it.dx), abs(it.dy))) }; points.forEach { val x = (width / 2.0 + (it.dx / m) * (width * .38)).toFloat(); val y = (height / 2.0 - (it.dy / m) * (height * .38)).toFloat(); p.color = if (it.current) BLUE else GREEN2; c.drawCircle(x, y, dp(11).toFloat(), p); p.color = Color.WHITE; c.drawCircle(x, y, dp(4).toFloat(), p) } }
    }

    private fun showStats() {
        val (_, body) = page("Statistiche"); var period = "MESE"; val cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val pie = PieView(); val legend = label("", 14f)
        fun range(): LongArray { val c = Calendar.getInstance(); val end = System.currentTimeMillis() + 1; if (period == "SETTIMANA") { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.add(Calendar.DAY_OF_YEAR, -6) } else if (period == "ANNO") { c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) } else { c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) }; return longArrayOf(c.timeInMillis, end) }
        fun stat(n: Int, text: String, col: Int): View = card().apply { gravity = Gravity.CENTER; addView(label(n.toString(), 28f, true).apply { setTextColor(col); gravity = Gravity.CENTER }); addView(label(text, 13f, true).apply { gravity = Gravity.CENTER }) }
        fun refreshStats() {
            cards.removeAllViews(); val rr = range(); val vs = visits(); val cs = clients(); var visitN = 0; var newC = 0; var rec = 0; var contract = 0; var pos = 0; var neg = 0; var inf = 0
            for (i in 0 until cs.length()) { val t = cs.getJSONObject(i).optLong("created"); if (t in rr[0] until rr[1]) newC++ }
            for (i in 0 until vs.length()) { val v = vs.getJSONObject(i); val t = v.optLong("time"); if (t !in rr[0] until rr[1]) continue; visitN++; when (v.optString("outcome")) { "Da ricontattare" -> rec++; "Contratto" -> { contract++; pos++ }; "Positivo" -> pos++; "Negativo" -> neg++; "Informativo" -> inf++ } }
            val a = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(stat(visitN, "Visite effettuate", BLUE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }); addView(stat(newC, "Clienti nuovi", GREEN2), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(stat(rec, "Da ricontattare", ORANGE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }); addView(stat(contract, "Contratti", PURPLE), LinearLayout.LayoutParams(0, dp(105), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            cards.addView(a); cards.addView(b); pie.values = intArrayOf(pos, rec, neg, inf); pie.invalidate(); val tot = max(1, pos + rec + neg + inf); legend.text = "● Positivo  ${100 * pos / tot}%\n● Da ricontattare  ${100 * rec / tot}%\n● Negativo  ${100 * neg / tot}%\n● Informativo  ${100 * inf / tot}%"
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; for (p in arrayOf("SETTIMANA", "MESE", "ANNO")) tabs.addView(label(p, 13f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER; setOnClickListener { period = p; refreshStats() } }, LinearLayout.LayoutParams(0, dp(45), 1f))
        body.addView(tabs); body.addView(cards); body.addView(label("Esiti visite", 18f, true).apply { setPadding(0, dp(18), 0, 0) }); body.addView(pie, lp(h = dp(230))); legend.setLineSpacing(dp(5).toFloat(), 1f); body.addView(legend); refreshStats()
    }

    private inner class PieView : View(this) {
        var values = intArrayOf(0, 0, 0, 0); val cols = intArrayOf(GREEN2, ORANGE, RED, BLUE); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) { super.onDraw(c); val total = values.sum(); val sz = min(width, height) * .82f; val r = RectF((width - sz) / 2, (height - sz) / 2, (width + sz) / 2, (height + sz) / 2); if (total == 0) { p.color = Color.LTGRAY; c.drawArc(r, 0f, 360f, true, p); return }; var st = -90f; for (i in values.indices) { val sw = 360f * values[i] / total; p.color = cols[i]; c.drawArc(r, st, sw, true, p); st += sw } }
    }

    private fun showSettings() {
        val (_, body) = page("Impostazioni"); body.addView(label("Visita Clienti v2.0", 21f, true)); body.addView(label("Dati salvati sul telefono. GPS, clienti e storico visite restano disponibili anche senza account.", 14f).apply { setTextColor(TEXT2); setPadding(0, dp(8), 0, dp(18)) })
        body.addView(action("ESPORTA CLIENTI IN CSV", BLUE).apply { setOnClickListener { val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "visita_clienti.csv") }; startActivityForResult(i, REQ_EXPORT) } }, lp(h = dp(56)))
        body.addView(label("Il CSV contiene ragione sociale, categoria, telefono, indirizzo, note e coordinate GPS.", 13f).apply { setTextColor(TEXT2); setPadding(0, dp(10), 0, 0) })
    }

    private fun navigate(c: JSONObject) {
        val lat = c.optDouble("lat"); val lon = c.optDouble("lon"); if (lat == 0.0 && lon == 0.0) { Toast.makeText(this, "Posizione GPS non registrata", Toast.LENGTH_SHORT).show(); return }
        var i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).apply { setPackage("com.google.android.apps.maps") }
        if (i.resolveActivity(packageManager) == null) i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(c.optString("name"))})")); startActivity(i)
    }

    private fun ensureLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
    }
    private fun getLocation(cb: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { pendingLocationAction = cb; ensureLocationPermission(); return }
        fused.lastLocation.addOnSuccessListener { last -> if (last != null) { current = last; cb(last) } else fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).addOnSuccessListener { l -> if (l != null) { current = l; cb(l) } else Toast.makeText(this, "Posizione non disponibile. Attiva il GPS.", Toast.LENGTH_LONG).show() } }
    }
    private fun openCamera(cb: (String) -> Unit) {
        pendingPhotoCallback = cb; if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION); return }
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE); if (i.resolveActivity(packageManager) != null) startActivityForResult(i, REQ_CAMERA) else Toast.makeText(this, "Fotocamera non disponibile", Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) { val cb = pendingLocationAction; pendingLocationAction = null; if (cb != null) getLocation(cb) }; if (requestCode == REQ_CAMERA_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { val cb = pendingPhotoCallback; if (cb != null) openCamera(cb) }
    }
    private fun csvQuote(s: String): String = "\"${s.replace("\"", "\"\"").replace("\n", " ")}\""
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) { val bm = data?.extras?.get("data") as? Bitmap; if (bm != null) { val out = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 75, out); pendingPhotoCallback?.invoke(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)) } }
        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data?.data != null) {
            try { contentResolver.openOutputStream(data.data!!)?.use { out -> val sb = StringBuilder("Ragione sociale;Categoria;Telefono;Indirizzo;Note;Latitudine;Longitudine\n"); val a = clients(); for (i in 0 until a.length()) { val c = a.getJSONObject(i); sb.append(csvQuote(c.optString("name"))).append(';').append(csvQuote(c.optString("category"))).append(';').append(csvQuote(c.optString("phone"))).append(';').append(csvQuote(c.optString("address"))).append(';').append(csvQuote(c.optString("notes"))).append(';').append(c.optDouble("lat")).append(';').append(c.optDouble("lon")).append('\n') }; out.write(sb.toString().toByteArray()) }; Toast.makeText(this, "Esportazione completata", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(this, "Errore esportazione: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    override fun onBackPressed() { showHome() }
}
