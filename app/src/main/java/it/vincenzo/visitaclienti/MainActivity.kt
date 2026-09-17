package it.vincenzo.visitaclienti

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("registro_visite", MODE_PRIVATE) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var name: EditText
    private lateinit var outcome: Spinner
    private lateinit var gpsInfo: TextView
    private lateinit var voiceInfo: TextView
    private lateinit var nextInfo: TextView
    private lateinit var stopBtn: Button
    private lateinit var playBtn: Button
    private var location: Location? = null
    private var nextAppointment: Calendar? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var voiceFile: File? = null
    private var recording = false
    private var pendingLocation: (() -> Unit)? = null
    private var pendingMic: (() -> Unit)? = null
    private var pendingXlsx: ByteArray? = null

    private val saveXlsx = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val data = pendingXlsx.also { pendingXlsx = null }
        if (uri != null && data != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
            toast("Excel salvato")
        } catch (_: Exception) { toast("Errore salvataggio Excel") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Registro Visite"
        setContentView(makeUi())
    }

    private fun makeUi(): ScrollView {
        val d = resources.displayMetrics.density
        fun dp(n: Int) = (n * d).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(28)) }
        fun title(t: String) = TextView(this).apply { text=t; textSize=17f; setPadding(0,dp(20),0,dp(5)) }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text=t; textSize=16f; setOnClickListener { f() } }

        box.addView(TextView(this).apply { text="REGISTRO VISITE"; textSize=26f; gravity=Gravity.CENTER; setPadding(0,dp(6),0,dp(14)) })
        name = EditText(this).apply { hint="Nominativo / Ragione sociale"; textSize=18f; isSingleLine=true }
        box.addView(name)
        outcome = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Positivo","Da ricontattare","Negativo","Non trovato","Altro")) }
        box.addView(outcome, LinearLayout.LayoutParams(-1,dp(54)))
        box.addView(TextView(this).apply { text="Data e ora vengono salvate automaticamente"; setPadding(0,dp(8),0,0) })

        box.addView(title("📍 GPS"))
        gpsInfo = TextView(this).apply { text="Nessuna posizione acquisita"; setPadding(0,0,0,dp(6)) }
        box.addView(gpsInfo)
        box.addView(button("RICONOSCI CLIENTE") { needLocation { recognize() } })
        box.addView(button("MEMORIZZA POSIZIONE") { needLocation { rememberPosition() } })

        box.addView(title("🎙 NOTA VOCALE"))
        voiceInfo = TextView(this).apply { text="Nessuna nota vocale"; setPadding(0,0,0,dp(6)) }
        box.addView(voiceInfo)
        box.addView(button("REGISTRA") { needMic { startRecording() } })
        stopBtn = button("STOP") { stopRecording() }.apply { isEnabled=false }
        playBtn = button("ASCOLTA") { playVoice() }.apply { isEnabled=false }
        box.addView(stopBtn); box.addView(playBtn)

        box.addView(title("📅 NUOVO APPUNTAMENTO"))
        nextInfo = TextView(this).apply { text="Nessun nuovo appuntamento"; setPadding(0,0,0,dp(6)) }
        box.addView(nextInfo)
        box.addView(button("SCEGLI DATA E ORA") { pickAppointment() })
        box.addView(button("TOGLI APPUNTAMENTO") { nextAppointment=null; nextInfo.text="Nessun nuovo appuntamento" })

        box.addView(button("SALVA VISITA") { saveVisit() }, LinearLayout.LayoutParams(-1,dp(62)).apply { topMargin=dp(18) })
        box.addView(button("STORICO VISITE") { history() })
        box.addView(button("ESPORTA EXCEL") { exportExcel() })
        return ScrollView(this).apply { addView(box) }
    }

    private fun clients() = try { JSONArray(prefs.getString("clients","[]")) } catch (_:Exception) { JSONArray() }
    private fun visits() = try { JSONArray(prefs.getString("visits","[]")) } catch (_:Exception) { JSONArray() }
    private fun saveClients(a: JSONArray) = prefs.edit().putString("clients",a.toString()).apply()
    private fun saveVisits(a: JSONArray) = prefs.edit().putString("visits",a.toString()).apply()

    private fun needLocation(action: () -> Unit) {
        val ok = ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if (ok) action() else { pendingLocation=action; ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),10) }
    }
    private fun needMic(action: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) action()
        else { pendingMic=action; ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),11) }
    }
    override fun onRequestPermissionsResult(code:Int,p:Array<out String>,r:IntArray) {
        super.onRequestPermissionsResult(code,p,r)
        if(code==10){ if(r.any{it==PackageManager.PERMISSION_GRANTED}) pendingLocation?.invoke() else toast("Permesso GPS negato"); pendingLocation=null }
        if(code==11){ if(r.firstOrNull()==PackageManager.PERMISSION_GRANTED) pendingMic?.invoke() else toast("Permesso microfono negato"); pendingMic=null }
    }

    private fun currentLocation(done:(Location)->Unit) {
        val fine=ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse) return
        gpsInfo.text="Rilevamento GPS…"
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,null).addOnSuccessListener { l ->
            if(l==null) gpsInfo.text="Posizione non disponibile. Attiva il GPS." else { location=l; gpsInfo.text="GPS acquisito • precisione ${l.accuracy.toInt()} m"; done(l) }
        }.addOnFailureListener { gpsInfo.text="Errore GPS" }
    }

    private fun rememberPosition() {
        val n=name.text.toString().trim(); if(n.isBlank()){toast("Scrivi prima il nominativo");return}
        currentLocation { l ->
            val a=clients(); var found=false
            for(i in 0 until a.length()){ val c=a.getJSONObject(i); if(c.optString("name").equals(n,true)){ c.put("name",n).put("lat",l.latitude).put("lon",l.longitude); found=true; break } }
            if(!found) a.put(JSONObject().put("name",n).put("lat",l.latitude).put("lon",l.longitude))
            saveClients(a); gpsInfo.text="Posizione memorizzata per $n"; toast("Posizione salvata")
        }
    }

    private fun recognize() {
        currentLocation { l ->
            val found=mutableListOf<Pair<JSONObject,Float>>(); val a=clients()
            for(i in 0 until a.length()){ val c=a.getJSONObject(i); val x=FloatArray(1); Location.distanceBetween(l.latitude,l.longitude,c.optDouble("lat"),c.optDouble("lon"),x); if(x[0]<=50) found.add(c to x[0]) }
            found.sortBy{it.second}
            if(found.isEmpty()) gpsInfo.text="Nessun cliente memorizzato entro 50 m"
            else if(found.size==1) choose(found[0])
            else AlertDialog.Builder(this).setTitle("Clienti vicini").setItems(found.map{"${it.first.optString("name")} — ${it.second.toInt()} m"}.toTypedArray()){_,i->choose(found[i])}.show()
        }
    }
    private fun choose(p:Pair<JSONObject,Float>){ val n=p.first.optString("name"); name.setText(n); gpsInfo.text="Cliente riconosciuto: $n • ${p.second.toInt()} m" }

    private fun pickAppointment() {
        val c=nextAppointment ?: Calendar.getInstance().apply{add(Calendar.DAY_OF_MONTH,1)}
        DatePickerDialog(this,{_,y,m,d-> TimePickerDialog(this,{_,h,min-> c.set(y,m,d,h,min,0); nextAppointment=c; nextInfo.text=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY).format(c.time) },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show() },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder() = if(Build.VERSION.SDK_INT>=31) MediaRecorder(this) else MediaRecorder()
    private fun startRecording() {
        stopPlayer(); voiceFile?.delete(); voiceFile=File(File(filesDir,"voice").apply{mkdirs()},"nota_${System.currentTimeMillis()}.m4a")
        try { recorder=newRecorder().apply{ setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(voiceFile!!.absolutePath); prepare(); start() }; recording=true; voiceInfo.text="Registrazione in corso…"; stopBtn.isEnabled=true; playBtn.isEnabled=false }
        catch(_:Exception){ recorder?.release();recorder=null;voiceFile=null;toast("Errore registrazione") }
    }
    private fun stopRecording() {
        if(!recording)return
        try{recorder?.stop()}catch(_:Exception){voiceFile?.delete();voiceFile=null}; recorder?.release();recorder=null;recording=false; stopBtn.isEnabled=false; playBtn.isEnabled=voiceFile?.exists()==true; voiceInfo.text=if(playBtn.isEnabled)"Nota vocale pronta" else "Nessuna nota vocale"
    }
    private fun playVoice(){ voiceFile?.takeIf{it.exists()}?.let{play(it)} ?: toast("Nessuna nota") }
    private fun play(f:File){ stopPlayer(); try{player=MediaPlayer().apply{setDataSource(f.absolutePath);prepare();start();setOnCompletionListener{stopPlayer()}}}catch(_:Exception){toast("Errore riproduzione")} }
    private fun stopPlayer(){ try{player?.stop()}catch(_:Exception){}; player?.release();player=null }

    private fun saveVisit() {
        val n=name.text.toString().trim(); if(n.isBlank()){toast("Inserisci il nominativo");return}; if(recording)stopRecording()
        val now=Date(); val v=JSONObject().put("client",n).put("outcome",outcome.selectedItem.toString()).put("date",SimpleDateFormat("dd/MM/yyyy",Locale.ITALY).format(now)).put("time",SimpleDateFormat("HH:mm",Locale.ITALY).format(now)).put("next",nextAppointment?.let{SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY).format(it.time)}?:"").put("voice",voiceFile?.takeIf{it.exists()}?.absolutePath?:"")
        location?.let{v.put("lat",it.latitude).put("lon",it.longitude)}; val a=visits();a.put(v);saveVisits(a);toast("Visita salvata");voiceFile=null;reset()
    }
    private fun reset(){ name.setText("");outcome.setSelection(0);location=null;nextAppointment=null;gpsInfo.text="Nessuna posizione acquisita";voiceInfo.text="Nessuna nota vocale";nextInfo.text="Nessun nuovo appuntamento";stopBtn.isEnabled=false;playBtn.isEnabled=false }

    private fun history() {
        val a=visits(); if(a.length()==0){toast("Nessuna visita registrata");return}
        val items=Array(a.length()){k->val v=a.getJSONObject(a.length()-1-k);"${v.optString("date")} ${v.optString("time")} — ${v.optString("client")}\n${v.optString("outcome")}${v.optString("next").takeIf{it.isNotBlank()}?.let{" • App. $it"}?:""}"}
        AlertDialog.Builder(this).setTitle("Storico visite").setItems(items){_,k-> val v=a.getJSONObject(a.length()-1-k); val path=v.optString("voice"); val msg="${v.optString("client")}\nEsito: ${v.optString("outcome")}\nData: ${v.optString("date")} ${v.optString("time")}\nNuovo appuntamento: ${v.optString("next").ifBlank{"—"}}\nNota vocale: ${if(path.isNotBlank())"Sì" else "No"}"; val b=AlertDialog.Builder(this).setTitle("Dettaglio").setMessage(msg).setNegativeButton("Chiudi",null); if(path.isNotBlank()&&File(path).exists())b.setPositiveButton("Ascolta"){_,_->play(File(path))};b.show() }.setNegativeButton("Chiudi",null).show()
    }

    private fun exportExcel(){ pendingXlsx=ExcelBuilder.build(visits()); saveXlsx.launch("Registro_Visite_${SimpleDateFormat("yyyyMMdd",Locale.ITALY).format(Date())}.xlsx") }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    override fun onDestroy(){ if(recording)try{recorder?.stop()}catch(_:Exception){}; recorder?.release();stopPlayer();super.onDestroy() }
}
