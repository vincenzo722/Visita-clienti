package it.vincenzo.visitaclienti

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

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
    private var pendingLocation: ((Location) -> Unit)? = null
    private var pendingPhoto: ((String) -> Unit)? = null
    private var pendingAudio: (() -> Unit)? = null
    private var recorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF006B48.toInt()
        showSplash()
        requestLocationIfNeeded()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)

    private fun rounded(color: Int = Color.WHITE, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0xFFE3E6EA.toInt())
    }

    private fun txt(value: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(0xFF202124.toInt())
        if (bold) setTypeface(typeface, 1)
    }

    private fun btn(value: String, color: Int = GREEN2) = Button(this).apply {
        text = value
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded()
    }

    private fun field(hintText: String, lines: Int = 1) = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded()
        if (lines > 1) {
            minLines = lines
            gravity = Gravity.TOP
        }
    }

    private fun page(title: String, back: Boolean = true): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val bar = TextView(this).apply {
            text = (if (back) "‹   " else "☰   ") + title
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, 1)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(GREEN)
            if (back) setOnClickListener { showHome() }
        }
        root.addView(bar, lp(h = dp(58)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(18)) }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        return body
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(45), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0C4A6E.toInt(), 0xFF17324F.toInt()))
        }
        root.addView(txt("◉", 86f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER }, lp(h = dp(145)))
        root.addView(txt("Visita Clienti", 40f, true).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(txt("La tua rete sul territorio", 18f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(
            txt("\n✓  Registra i tuoi clienti\n✓  Trova i clienti vicini\n✓  Registra le visite\n✓  Prossima visita\n✓  Appunti vocali e foto\n✓  Storico completo", 17f).apply {
                setTextColor(Color.WHITE)
                setLineSpacing(dp(7).toFloat(), 1f)
            },
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(btn("INIZIA", 0xFF00B853.toInt()).apply { setOnClickListener { showHome() } }, lp(h = dp(58)))
        root.addView(txt("“Più visite, più opportunità”", 16f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
        setContentView(root)
    }

    private fun showHome() {
        val body = page("Visita Clienti", false)
        fun tile(icon: String, title: String, click: () -> Unit): View = card().apply {
            gravity = Gravity.CENTER
            addView(txt(icon, 34f, true).apply { setTextColor(BLUE); gravity = Gravity.CENTER })
            addView(txt(title, 16f, true).apply { gravity = Gravity.CENTER })
            setOnClickListener { click() }
        }
        fun addRow(a: View, b: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(a, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            row.addView(b, LinearLayout.LayoutParams(0, dp(132), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            body.addView(row)
        }
        addRow(tile("👤+", "Nuovo Cliente") { showNewClient() }, tile("👥", "Elenco Clienti") { showClientList() })
        addRow(tile("📍", "Clienti Vicini") { showNearby() }, tile("▤", "Registra Visita") { showRegisterVisit() })
        addRow(tile("▥", "Statistiche") { showStats() }, tile("⚙", "Impostazioni") { showSettings() })
        body.addView(btn("📅  PROSSIME VISITE", PURPLE).apply { setOnClickListener { showUpcomingVisits() } }, lp(h = dp(54)).apply { setMargins(dp(4), dp(10), dp(4), 0) })
    }

    private fun clients(): JSONArray = try { JSONArray(prefs.getString("clients", "[]")) } catch (_: Exception) { JSONArray() }
    private fun visits(): JSONArray = try { JSONArray(prefs.getString("visits", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveClients(a: JSONArray) = prefs.edit().putString("clients", a.toString()).apply()
    private fun saveVisits(a: JSONArray) = prefs.edit().putString("visits", a.toString()).apply()

    private fun nextId(a: JSONArray): Long {
        var m = 0L
        for (i in 0 until a.length()) m = max(m, a.optJSONObject(i)?.optLong("id") ?: 0L)
        return m + 1
    }

    private fun clientById(id: Long): JSONObject? {
        val a = clients()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            if (c.optLong("id") == id) return c
        }
        return null
    }

    private fun showNewClient(existing: JSONObject? = null) {
        val body = page(if (existing == null) "Nuovo Cliente" else "Modifica Cliente")
        val name = field("Ragione Sociale *")
        val category = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Cliente", "Potenziale", "Fornitore", "Altro")) }
        val phone = field("Telefono")
        val address = field("Indirizzo")
        val notes = field("Note", 3)
        var lat = existing?.optDouble("lat", 0.0) ?: 0.0
        var lon = existing?.optDouble("lon", 0.0) ?: 0.0

        if (existing != null) {
            name.setText(existing.optString("name"))
            phone.setText(existing.optString("phone"))
            address.setText(existing.optString("address"))
            notes.setText(existing.optString("notes"))
            val cats = arrayOf("Cliente", "Potenziale", "Fornitore", "Altro")
            category.setSelection(cats.indexOf(existing.optString("category", "Cliente")).coerceAtLeast(0))
        }

        listOf<View>(name, category, phone, address, notes).forEach {
            body.addView(it, lp().apply { setMargins(0, 0, 0, dp(10)) })
        }

        val gpsLabel = txt(if (lat != 0.0 || lon != 0.0) "📍 %.5f, %.5f".format(Locale.ITALY, lat, lon) else "📍 Posizione non acquisita", 15f, true).apply { setTextColor(BLUE) }
        body.addView(gpsLabel)
        body.addView(btn("◎  USA POSIZIONE ATTUALE", BLUE).apply {
            setOnClickListener {
                getLocation { l ->
                    lat = l.latitude
                    lon = l.longitude
                    gpsLabel.text = "📍 %.5f, %.5f".format(Locale.ITALY, lat, lon)
                }
            }
        }, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, dp(8)) })

        body.addView(btn(if (existing == null) "SALVA CLIENTE" else "SALVA MODIFICHE").apply {
            setOnClickListener {
                if (name.text.isBlank()) {
                    name.error = "Inserisci la ragione sociale"
                    return@setOnClickListener
                }
                val a = clients()
                val id = existing?.optLong("id") ?: nextId(a)
                val obj = JSONObject()
                    .put("id", id)
                    .put("name", name.text.toString().trim())
                    .put("category", category.selectedItem.toString())
                    .put("phone", phone.text.toString().trim())
                    .put("address", address.text.toString().trim())
                    .put("notes", notes.text.toString().trim())
                    .put("lat", lat)
                    .put("lon", lon)
                    .put("created", existing?.optLong("created") ?: System.currentTimeMillis())
                if (existing == null) a.put(obj) else {
                    for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("id") == id) a.put(i, obj)
                }
                saveClients(a)
                android.widget.Toast.makeText(this@V2Activity, "Cliente salvato", android.widget.Toast.LENGTH_SHORT).show()
                showClientDetail(id)
            }
        }, lp(h = dp(56)))
    }

    private fun showClientList() {
        val body = page("Elenco Clienti")
        val search = field("⌕  Cerca cliente...")
        body.addView(search, lp(h = dp(54)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        fun refresh() {
            list.removeAllViews()
            val q = search.text.toString().trim().lowercase()
            val a = clients()
            var count = 0
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                if (q.isNotEmpty() && !c.optString("name").lowercase().contains(q) && !c.optString("address").lowercase().contains(q)) continue
                list.addView(clientRow(c))
                count++
            }
            if (count == 0) list.addView(txt("Nessun cliente trovato.", 16f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(130)))
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        body.addView(btn("＋  NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, 0) })
        refresh()
    }

    private fun clientRow(c: JSONObject): View {
        val box = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(txt("▦", 28f, true).apply { setTextColor(GREEN2) }, lp(dp(46), dp(58)))
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt(c.optString("name"), 16f, true))
            addView(txt(c.optString("address", "Nessun indirizzo"), 13f).apply { setTextColor(TEXT2) })
        }
        row.addView(center, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt(c.optString("category", "Cliente"), 13f, true).apply { setTextColor(GREEN2) })
        box.addView(row)
        box.setOnClickListener { showClientDetail(c.optLong("id")) }
        box.layoutParams = lp().apply { setMargins(0, dp(6), 0, 0) }
        return box
    }

    private fun showClientDetail(id: Long) {
        val c = clientById(id) ?: run { showClientList(); return }
        val body = page("Dettaglio Cliente")
        body.addView(card().apply {
            addView(txt("▦  ${c.optString("name")}", 21f, true))
            addView(txt(c.optString("category", "Cliente"), 13f, true).apply { setTextColor(GREEN2) })
            addView(txt("☎  ${c.optString("phone", "—")}", 15f))
            addView(txt("📍  ${c.optString("address", "—")}", 15f))
            addView(txt("▤  ${c.optString("notes", "—")}", 15f))
        })

        val next = nearestFutureVisit(id)
        if (next != null) {
            body.addView(card().apply {
                addView(txt("📅 Prossima visita", 16f, true).apply { setTextColor(PURPLE) })
                addView(txt(formatDate(next), 18f, true))
            }, lp().apply { setMargins(0, dp(10), 0, 0) })
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(btn("➤  NAVIGA", BLUE).apply { setOnClickListener { navigate(c) } }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(0, dp(10), dp(4), dp(10)) })
        actions.addView(btn("☎  CHIAMA", GREEN2).apply {
            setOnClickListener {
                val p = c.optString("phone")
                if (p.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(p)}")))
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(4), dp(10), 0, dp(10)) })
        body.addView(actions)
        body.addView(btn("＋  REGISTRA VISITA").apply { setOnClickListener { showRegisterVisit(id) } }, lp(h = dp(54)))
        body.addView(btn("✎  MODIFICA CLIENTE", BLUE).apply { setOnClickListener { showNewClient(c) } }, lp(h = dp(50)).apply { setMargins(0, dp(8), 0, 0) })
        body.addView(txt("Ultime visite", 18f, true).apply { setPadding(0, dp(18), 0, dp(8)) })

        val v = visits()
        var shown = 0
        for (i in v.length() - 1 downTo 0) {
            val item = v.getJSONObject(i)
            if (item.optLong("clientId") != id) continue
            body.addView(visitRow(item))
            shown++
        }
        if (shown == 0) body.addView(txt("Nessuna visita registrata.", 14f).apply { setTextColor(TEXT2) })
    }

    private fun visitRow(v: JSONObject): View {
        val box = card()
        box.addView(txt("▣  ${formatDate(v.optLong("time"))}", 15f, true))
        val outcome = v.optString("outcome")
        val outcomeColor = when (outcome) { "Da ricontattare" -> ORANGE; "Negativo" -> RED; "Informativo" -> BLUE; else -> GREEN2 }
        box.addView(txt("Esito: $outcome", 14f, true).apply { setTextColor(outcomeColor) })
        if (v.optString("notes").isNotBlank()) box.addView(txt(v.optString("notes"), 13f).apply { setTextColor(TEXT2) })
        val nextVisit = v.optLong("nextVisit")
        if (nextVisit > 0) box.addView(txt("📅 Prossima visita: ${formatDate(nextVisit)}", 14f, true).apply { setTextColor(PURPLE) })
        val voicePath = v.optString("voicePath")
        if (voicePath.isNotBlank()) {
            box.addView(btn("▶  ASCOLTA APPUNTO VOCALE", PURPLE).apply { setOnClickListener { playVoice(voicePath) } }, lp(h = dp(44)).apply { setMargins(0, dp(8), 0, 0) })
        }
        box.layoutParams = lp().apply { setMargins(0, 0, 0, dp(8)) }
        return box
    }

    private fun showRegisterVisit(presetId: Long = 0L) {
        val body = page("Registra Visita")
        val a = clients()
        if (a.length() == 0) {
            body.addView(txt("Prima inserisci almeno un cliente.", 16f).apply { setTextColor(TEXT2) })
            body.addView(btn("NUOVO CLIENTE").apply { setOnClickListener { showNewClient() } }, lp(h = dp(54)))
            return
        }

        val clientList = (0 until a.length()).map { a.getJSONObject(it) }
        val clientSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, clientList.map { it.optString("name") }) }
        if (presetId > 0) {
            val idx = clientList.indexOfFirst { it.optLong("id") == presetId }
            if (idx >= 0) clientSpinner.setSelection(idx)
        }
        body.addView(txt("Cliente", 14f, true))
        body.addView(clientSpinner, lp().apply { setMargins(0, dp(4), 0, dp(12)) })

        val outcomes = arrayOf("Positivo", "Da ricontattare", "Negativo", "Informativo", "Contratto")
        val outcomeSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, outcomes) }
        body.addView(txt("Esito visita", 14f, true))
        body.addView(outcomeSpinner, lp().apply { setMargins(0, dp(4), 0, dp(12)) })

        val notes = field("Inserisci note sulla visita", 4)
        body.addView(txt("Note", 14f, true))
        body.addView(notes, lp().apply { setMargins(0, dp(4), 0, dp(8)) })

        var voicePath = ""
        var recording = false
        val voiceStatus = txt("Nessun appunto vocale", 13f).apply { setTextColor(TEXT2) }
        val voiceButton = btn("🎙  REGISTRA APPUNTO VOCALE", PURPLE)
        voiceButton.setOnClickListener {
            if (!recording) {
                startVoiceRecording { path ->
                    voicePath = path
                    recording = true
                    voiceButton.text = "■  FERMA REGISTRAZIONE"
                    voiceButton.background = GradientDrawable().apply { setColor(RED); cornerRadius = dp(10).toFloat() }
                    voiceStatus.text = "Registrazione in corso…"
                    voiceStatus.setTextColor(RED)
                }
            } else {
                stopVoiceRecording()
                recording = false
                voiceButton.text = "🎙  REGISTRA DI NUOVO"
                voiceButton.background = GradientDrawable().apply { setColor(PURPLE); cornerRadius = dp(10).toFloat() }
                voiceStatus.text = "Appunto vocale registrato"
                voiceStatus.setTextColor(GREEN2)
            }
        }
        body.addView(voiceButton, lp(h = dp(54)))
        body.addView(voiceStatus, lp().apply { setMargins(0, dp(4), 0, dp(10)) })

        var nextVisit = 0L
        val nextLabel = txt("Nessuna prossima visita", 15f, true).apply { setTextColor(PURPLE) }
        body.addView(txt("Prossima visita", 14f, true))
        body.addView(nextLabel, lp().apply { setMargins(0, dp(5), 0, dp(5)) })
        val nextRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nextRow.addView(btn("📅  SCEGLI DATA E ORA", PURPLE).apply {
            setOnClickListener { chooseDateTime { t -> nextVisit = t; nextLabel.text = formatDate(t) } }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(0, 0, dp(4), 0) })
        nextRow.addView(btn("AZZERA", 0xFF777777.toInt()).apply { setOnClickListener { nextVisit = 0L; nextLabel.text = "Nessuna prossima visita" } }, lp(dp(95), dp(50)))
        body.addView(nextRow, lp().apply { setMargins(0, 0, 0, dp(10)) })

        var photo64 = ""
        val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        body.addView(btn("📷  AGGIUNGI FOTO", 0xFF444444.toInt()).apply {
            setOnClickListener {
                openCamera { b64 ->
                    photo64 = b64
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            }
        }, lp(h = dp(54)))
        body.addView(preview, lp(h = dp(140)))

        body.addView(btn("SALVA VISITA").apply {
            setOnClickListener {
                if (recording) stopVoiceRecording()
                val client = clientList[clientSpinner.selectedItemPosition]
                val v = visits()
                v.put(
                    JSONObject()
                        .put("id", nextId(v))
                        .put("clientId", client.optLong("id"))
                        .put("time", System.currentTimeMillis())
                        .put("outcome", outcomeSpinner.selectedItem.toString())
                        .put("notes", notes.text.toString().trim())
                        .put("photo", photo64)
                        .put("voicePath", voicePath)
                        .put("nextVisit", nextVisit)
                )
                saveVisits(v)
                android.widget.Toast.makeText(this@V2Activity, "Visita salvata", android.widget.Toast.LENGTH_SHORT).show()
                showClientDetail(client.optLong("id"))
            }
        }, lp(h = dp(56)))
    }

    private fun showUpcomingVisits() {
        val body = page("Prossime Visite")
        data class Appointment(val time: Long, val client: JSONObject)
        val items = mutableListOf<Appointment>()
        val now = System.currentTimeMillis()
        val v = visits()
        for (i in 0 until v.length()) {
            val item = v.getJSONObject(i)
            val t = item.optLong("nextVisit")
            if (t <= now) continue
            val c = clientById(item.optLong("clientId")) ?: continue
            items.add(Appointment(t, c))
        }
        items.sortBy { it.time }
        if (items.isEmpty()) {
            body.addView(txt("Nessuna prossima visita programmata.", 16f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(150)))
        } else {
            items.forEach { ap ->
                body.addView(card().apply {
                    addView(txt("📅 ${formatDate(ap.time)}", 17f, true).apply { setTextColor(PURPLE) })
                    addView(txt(ap.client.optString("name"), 17f, true))
                    addView(txt(ap.client.optString("address"), 13f).apply { setTextColor(TEXT2) })
                    setOnClickListener { showClientDetail(ap.client.optLong("id")) }
                }, lp().apply { setMargins(0, 0, 0, dp(8)) })
            }
        }
    }

    private fun nearestFutureVisit(clientId: Long): Long? {
        val now = System.currentTimeMillis()
        var best = Long.MAX_VALUE
        val v = visits()
        for (i in 0 until v.length()) {
            val item = v.getJSONObject(i)
            if (item.optLong("clientId") != clientId) continue
            val t = item.optLong("nextVisit")
            if (t > now && t < best) best = t
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    private fun chooseDateTime(cb: (Long) -> Unit) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { set(year, month, day) }
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                cb(selected.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showNearby() {
        val body = page("Clienti Vicini")
        val radiusSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@V2Activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("500 m", "1 km", "3 km", "5 km", "10 km")); setSelection(3) }
        body.addView(radiusSpinner, lp(h = dp(52)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        val search = btn("CERCA CLIENTI VICINI", BLUE)
        body.addView(search, lp(h = dp(54)).apply { setMargins(0, dp(8), 0, dp(8)) })

        fun maxMeters(): Float = when (radiusSpinner.selectedItemPosition) { 0 -> 500f; 1 -> 1000f; 2 -> 3000f; 4 -> 10000f; else -> 5000f }

        fun refresh(location: Location) {
            list.removeAllViews()
            data class Near(val client: JSONObject, val meters: Float)
            val found = mutableListOf<Near>()
            val a = clients()
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                val lat = c.optDouble("lat")
                val lon = c.optDouble("lon")
                if (lat == 0.0 && lon == 0.0) continue
                val out = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, lat, lon, out)
                if (out[0] <= maxMeters()) found.add(Near(c, out[0]))
            }
            found.sortBy { it.meters }
            if (found.isEmpty()) {
                list.addView(txt("Nessun cliente nel raggio selezionato.", 15f).apply { setTextColor(TEXT2); gravity = Gravity.CENTER }, lp(h = dp(120)))
            } else {
                found.forEach { n ->
                    val box = card()
                    box.addView(txt("📍 ${n.client.optString("name")}", 16f, true))
                    box.addView(txt(n.client.optString("address"), 13f).apply { setTextColor(TEXT2) })
                    box.addView(txt(if (n.meters < 1000) "${n.meters.roundToInt()} m" else "%.1f km".format(Locale.ITALY, n.meters / 1000f), 14f, true).apply { setTextColor(GREEN2) })
                    box.setOnClickListener { showClientDetail(n.client.optLong("id")) }
                    list.addView(box, lp().apply { setMargins(0, 0, 0, dp(8)) })
                }
            }
        }

        search.setOnClickListener { getLocation { refresh(it) } }
        getLocation { refresh(it) }
    }

    private fun showStats() {
        val body = page("Statistiche")
        val v = visits()
        var rec = 0
        var contracts = 0
        var positive = 0
        for (i in 0 until v.length()) {
            when (v.getJSONObject(i).optString("outcome")) {
                "Da ricontattare" -> rec++
                "Contratto" -> { contracts++; positive++ }
                "Positivo" -> positive++
            }
        }
        body.addView(card().apply {
            addView(txt("${v.length()}  Visite effettuate", 22f, true).apply { setTextColor(BLUE) })
            addView(txt("${clients().length()}  Clienti", 22f, true).apply { setTextColor(GREEN2) })
            addView(txt("$rec  Da ricontattare", 22f, true).apply { setTextColor(ORANGE) })
            addView(txt("$contracts  Contratti", 22f, true).apply { setTextColor(PURPLE) })
            addView(txt("$positive  Esiti positivi", 22f, true).apply { setTextColor(GREEN2) })
        })
    }

    private fun showSettings() {
        val body = page("Impostazioni")
        body.addView(txt("Visita Clienti v2.1", 21f, true))
        body.addView(txt("Ora include prossima visita e appunti vocali registrabili direttamente nelle note della visita.", 14f).apply { setTextColor(TEXT2); setPadding(0, dp(8), 0, dp(18)) })
        body.addView(btn("ESPORTA CLIENTI IN CSV", BLUE).apply {
            setOnClickListener {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "visita_clienti.csv")
                }
                startActivityForResult(i, REQ_EXPORT)
            }
        }, lp(h = dp(56)))
    }

    private fun formatDate(t: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(t))

    private fun navigate(c: JSONObject) {
        val lat = c.optDouble("lat")
        val lon = c.optDouble("lon")
        if (lat == 0.0 && lon == 0.0) {
            android.widget.Toast.makeText(this, "Posizione GPS non registrata", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        var intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).apply { setPackage("com.google.android.apps.maps") }
        if (intent.resolveActivity(packageManager) == null) intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
        startActivity(intent)
    }

    private fun startVoiceRecording(started: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudio = { startVoiceRecording(started) }
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO_PERMISSION)
            return
        }
        try {
            val dir = File(filesDir, "voice_notes").apply { mkdirs() }
            val file = File(dir, "nota_${System.currentTimeMillis()}.m4a")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            started(file.absolutePath)
        } catch (e: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            android.widget.Toast.makeText(this, "Registrazione non disponibile", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVoiceRecording() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun playVoice(path: String) {
        val file = File(path)
        if (!file.exists()) {
            android.widget.Toast.makeText(this, "Appunto vocale non trovato", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val player = MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            player.start()
            player.setOnCompletionListener { it.release() }
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "Impossibile riprodurre l'audio", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocationIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    private fun getLocation(cb: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocation = cb
            requestLocationIfNeeded()
            return
        }
        fused.lastLocation.addOnSuccessListener { last ->
            if (last != null) cb(last) else {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).addOnSuccessListener { current ->
                    if (current != null) cb(current) else android.widget.Toast.makeText(this, "Posizione non disponibile. Attiva il GPS.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openCamera(cb: (String) -> Unit) {
        pendingPhoto = cb
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            return
        }
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (i.resolveActivity(packageManager) != null) startActivityForResult(i, REQ_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            val cb = pendingLocation
            pendingLocation = null
            if (cb != null) getLocation(cb)
        }
        if (requestCode == REQ_CAMERA_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingPhoto?.let { openCamera(it) }
        }
        if (requestCode == REQ_AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            val a = pendingAudio
            pendingAudio = null
            a?.invoke()
        }
    }

    private fun csvQuote(s: String): String = "\"${s.replace("\"", "\"\"").replace("\n", " ")}\""

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                pendingPhoto?.invoke(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            }
        }
        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { out ->
                    val sb = StringBuilder("Ragione sociale;Categoria;Telefono;Indirizzo;Note;Latitudine;Longitudine\n")
                    val a = clients()
                    for (i in 0 until a.length()) {
                        val c = a.getJSONObject(i)
                        sb.append(csvQuote(c.optString("name"))).append(';')
                            .append(csvQuote(c.optString("category"))).append(';')
                            .append(csvQuote(c.optString("phone"))).append(';')
                            .append(csvQuote(c.optString("address"))).append(';')
                            .append(csvQuote(c.optString("notes"))).append(';')
                            .append(c.optDouble("lat")).append(';')
                            .append(c.optDouble("lon")).append('\n')
                    }
                    out.write(sb.toString().toByteArray())
                }
                android.widget.Toast.makeText(this, "Esportazione completata", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "Errore esportazione", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        stopVoiceRecording()
        showHome()
    }

    override fun onDestroy() {
        stopVoiceRecording()
        super.onDestroy()
    }
}
