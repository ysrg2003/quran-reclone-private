package com.example.quranoffline.data

import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject

/** Runtime audio metadata only. No Quran text or speech-recognition behavior is replaced. */
object QuranComAudio {
 const val AYAH_RECITATION_ID=6 // API catalog: Mahmoud Khalil Al-Husary, Murattal.
 const val API="https://api.quran.com/api/v4"
 fun ayah(surah:Int,ayah:Int):String {require(surah in 1..114&&ayah>0);return "quranapi://ayah/$surah/$ayah"}
 fun word(surah:Int,ayah:Int,index:Int,text:String,count:Int):String {require(index>=0);return "quranapi://word/$surah/$ayah/${index+1}?count=$count&text="+java.net.URLEncoder.encode(text,"UTF-8")}
 // Waqf/pause marks and the spaces the API puts before them are not part of the word identity.
 fun normalizedWord(text:String)=normalized(waqfMarks.replace(text,"")).replace(Regex("\\s"),"")
  .replace('\u0649','\u064A') // alif maqsura and ya are the same sound; the asset and the API disagree
  .replace('\u0629','\u0647') // ta marbuta and ha are the same sound
 private val waqfMarks=Regex("[\\u0615\\u06D6-\\u06ED]")
 fun normalized(text:String)=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKD).replace(Regex("[\\p{M}ـ]"),"").replace('ٱ','ا').replace('أ','ا').replace('إ','ا').replace('آ','ا')
 fun isReference(source:String)=source.startsWith("quranapi://")
 private fun read(path:String):JSONObject {
  val c=URI(API+path).toURL().openConnection()as HttpURLConnection
  try{c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("Accept","application/json");check(c.responseCode==200){"Quran.com audio metadata HTTP ${c.responseCode}"};val raw=c.inputStream.bufferedReader().use{it.readText()};require(raw.length<=2000000);return JSONObject(raw)}finally{c.disconnect()}
 }
 fun media(value:String,base:String):String {
  val uri=URI(if(value.startsWith("//"))"https:$value"else value).let{if(it.isAbsolute)it else URI(base).resolve(it)}
  require(uri.scheme=="https"&&uri.host in setOf("audio.qurancdn.com","verses.quran.com","verses.quran.foundation","mirrors.quranicaudio.com","download.quranicaudio.com")&&uri.userInfo==null&&uri.port in setOf(-1,443)&&uri.fragment==null)
  return uri.toASCIIString()
 }
 fun resolve(source:String):String {
  if(!isReference(source))return source
  val u=URI(source);val parts=u.path.trim('/').split('/');val surah=parts[0].toInt();val ayah=parts[1].toInt();require(surah in 1..114&&ayah>0)
  val key="$surah:$ayah"
  return when(u.host){
   "ayah"->{require(parts.size==2);val root=read("/recitations/${Reciters.currentId}/by_ayah/$key");val rows=root.getJSONArray("audio_files");require(rows.length()==1);val row=rows.getJSONObject(0);require(row.getString("verse_key")==key);media(row.getString("url"),"https://verses.quran.com/")}
   "word"->{require(parts.size==3);val position=parts[2].toInt();val verse=read("/verses/by_key/$key?words=true&word_fields=audio_url,text_uthmani,text_imlaei").getJSONObject("verse");require(verse.getString("verse_key")==key);val words=verse.getJSONArray("words");val query=u.rawQuery.split("&").associate{val pair=it.split("=",limit=2);pair[0] to java.net.URLDecoder.decode(pair[1],"UTF-8")};require((0 until words.length()).count{words.getJSONObject(it).getString("char_type_name")=="word"}==query.getValue("count").toInt()){ "Word count differs; refusing guessed alignment" };val matches=(0 until words.length()).map{words.getJSONObject(it)}.filter{it.getInt("position")==position&&it.getString("char_type_name")=="word"};require(matches.size==1){"Exact word audio unavailable"};val row=matches.single();require(normalizedWord(query.getValue("text")) in listOf(normalizedWord(row.getString("text_uthmani")),normalizedWord(row.getString("text_imlaei")))){"Word identity differs; refusing guessed alignment"};media(matches.single().getString("audio_url"),"https://audio.qurancdn.com/")}
   else->error("Unknown audio reference")
  }
 }
}
