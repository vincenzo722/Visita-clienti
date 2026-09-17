package it.vincenzo.visitaclienti

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("visite", MODE_PRIVATE) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var info: TextView
    private var current: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title=TextView(this).apply { text="VISITA CLIENTI"; textSize=28f; setPadding(0,20,0,25) }
        root.addView(title)
        info=TextView(this).apply { text="GPS non acquisito"; textSize=17f; setPadding(0,0,0,20) }; root.addView(info)
        fun button(t:String, action:()->Unit) { root.addView(Button(this).apply { text=t; textSize=17f; setOnClickListener{action()} }) }
        button("📍 RICONOSCI CLIENTE GPS") { locate(true) }
        button("➕ NUOVO CLIENTE") { newClient() }
        button("📝 REGISTRA VISITA") { locate(false) }
        button("📚 STORICO VISITE") { history() }
        setContentView(ScrollView(this).apply { addView(root) })
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),10)
        else locate(true)
    }

    private fun locate(recognize:Boolean) {
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
        fused.lastLocation.addOnSuccessListener { l ->
            current=l
            if(l==null) info.text="Posizione non disponibile. Attiva il GPS e riprova."
            else { info.text="GPS: %.5f, %.5f".format(l.latitude,l.longitude); if(recognize) recognize(l) else visit(l) }
        }
    }

    private fun clients():JSONArray = try { JSONArray(prefs.getString("clients","[]")) } catch(_:Exception){ JSONArray() }
    private fun saveClients(a:JSONArray)=prefs.edit().putString("clients",a.toString()).apply()
    private fun visits():JSONArray = try { JSONArray(prefs.getString("visits","[]")) } catch(_:Exception){ JSONArray() }
    private fun saveVisits(a:JSONArray)=prefs.edit().putString("visits",a.toString()).apply()

    private fun newClient() {
        val l=current ?: run { Toast.makeText(this,"Prima acquisisci il GPS",Toast.LENGTH_SHORT).show(); locate(true); return }
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,0,30,0)}
        val name=EditText(this).apply{hint="Ragione sociale / cliente"}; val address=EditText(this).apply{hint="Indirizzo (facoltativo)"}
        box.addView(name);box.addView(address)
        android.app.AlertDialog.Builder(this).setTitle("Nuovo cliente").setView(box).setPositiveButton("SALVA"){_,_->
            if(name.text.isNotBlank()){ val a=clients(); a.put(JSONObject().put("name",name.text.toString()).put("address",address.text.toString()).put("lat",l.latitude).put("lon",l.longitude)); saveClients(a); Toast.makeText(this,"Cliente salvato con posizione GPS",Toast.LENGTH_LONG).show() }
        }.setNegativeButton("ANNULLA",null).show()
    }

    private fun nearest(l:Location):Pair<JSONObject,Float>? {
        val a=clients(); var best:JSONObject?=null; var dist=Float.MAX_VALUE
        for(i in 0 until a.length()){ val c=a.getJSONObject(i); val r=FloatArray(1); Location.distanceBetween(l.latitude,l.longitude,c.getDouble("lat"),c.getDouble("lon"),r); if(r[0]<dist){dist=r[0];best=c} }
        return if(best!=null) Pair(best!!,dist) else null
    }
    private fun recognize(l:Location){ val n=nearest(l); info.text= if(n==null) "Nessun cliente registrato" else if(n.second<=100) "Cliente riconosciuto: ${n.first.getString("name")} (${n.second.toInt()} m)" else "Nessun cliente entro 100 m. Più vicino: ${n.first.getString("name")} (${n.second.toInt()} m)" }
    private fun visit(l:Location){ val n=nearest(l); if(n==null || n.second>100){Toast.makeText(this,"Nessun cliente riconosciuto entro 100 m",Toast.LENGTH_LONG).show();return}; val c=n.first
        val outcomes=arrayOf("Interessato","Da richiamare","Preventivo da inviare","Contratto acquisito","Non interessato","Assente/Chiuso")
        var selected=outcomes[0]; val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,0,30,0)}
        val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,outcomes);sp.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){selected=outcomes[pos]}}
        val notes=EditText(this).apply{hint="Note visita";minLines=3};box.addView(sp);box.addView(notes)
        android.app.AlertDialog.Builder(this).setTitle("Visita: ${c.getString("name")}").setView(box).setPositiveButton("SALVA VISITA"){_,_-> val a=visits();a.put(JSONObject().put("client",c.getString("name")).put("outcome",selected).put("notes",notes.text.toString()).put("date",SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY).format(Date())).put("lat",l.latitude).put("lon",l.longitude));saveVisits(a);Toast.makeText(this,"Visita registrata",Toast.LENGTH_LONG).show()}.setNegativeButton("ANNULLA",null).show()
    }
    private fun history(){ val a=visits(); if(a.length()==0){Toast.makeText(this,"Nessuna visita registrata",Toast.LENGTH_SHORT).show();return}; val sb=StringBuilder();for(i in a.length()-1 downTo 0){val v=a.getJSONObject(i);sb.append(v.getString("date")).append(" — ").append(v.getString("client")).append("\n").append(v.getString("outcome")).append("\n").append(v.optString("notes")).append("\n\n")};android.app.AlertDialog.Builder(this).setTitle("Storico visite").setMessage(sb.toString()).setPositiveButton("CHIUDI",null).show() }
}
