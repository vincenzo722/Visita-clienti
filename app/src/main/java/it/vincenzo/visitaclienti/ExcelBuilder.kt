package it.vincenzo.visitaclienti

import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelBuilder {
    fun build(a: JSONArray): ByteArray {
        val rows=mutableListOf<List<String>>()
        rows.add(listOf("Nominativo","Esito","Data","Ora","Nuovo appuntamento","Latitudine","Longitudine","Nota vocale"))
        for(i in 0 until a.length()){
            val v=a.getJSONObject(i)
            rows.add(listOf(v.optString("client"),v.optString("outcome"),v.optString("date"),v.optString("time"),v.optString("next"),if(v.has("lat"))v.optDouble("lat").toString()else"",if(v.has("lon"))v.optDouble("lon").toString()else"",if(v.optString("voice").isNotBlank())"Sì" else "No"))
        }
        val sheet=buildString{
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed{r,row->append("<row r=\"${r+1}\">");row.forEachIndexed{c,value->append("<c r=\"${col(c+1)}${r+1}\" t=\"inlineStr\"><is><t>${esc(value)}</t></is></c>")};append("</row>")}
            append("</sheetData></worksheet>")
        }
        val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->
            fun add(n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray());z.closeEntry()}
            add("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            add("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Visite" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            add("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            add("xl/worksheets/sheet1.xml",sheet)
        };return out.toByteArray()
    }
    private fun col(i:Int):String{var n=i;val s=StringBuilder();while(n>0){n--;s.append(('A'.code+n%26).toChar());n/=26};return s.reverse().toString()}
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
