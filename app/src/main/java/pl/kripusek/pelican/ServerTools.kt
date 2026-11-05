package pl.kripusek.pelican

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

suspend fun nativeDownload(context:Context,url:String,name:String)=withContext(Dispatchers.IO){
 val u=url.toHttpUrl();require(u.scheme=="https"&&u.username.isBlank()&&u.password.isBlank()){"Pobieranie wymaga bezpiecznego adresu HTTPS."}
 val filename=name.replace(Regex("[\\\\/\\r\\n]"),"_").take(150).ifBlank{"pelican-download"}
 val req=DownloadManager.Request(Uri.parse(url)).setTitle(filename).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,filename)
 (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
}
@Composable fun FilesScreen(s:ServerInfo,api:PelicanApi,back:()->Unit,permissions:Set<String>){
 val ctx=LocalContext.current;val scope=rememberCoroutineScope();val p=serverPath(s)
 var dir by remember{mutableStateOf("/")};var rows by remember{mutableStateOf<List<JSONObject>>(emptyList())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};var revision by remember{mutableIntStateOf(0)}
 var selected by remember{mutableStateOf<JSONObject?>(null)};var op by remember{mutableStateOf<Operation?>(null)};var editor by remember{mutableStateOf<String?>(null)};var content by remember{mutableStateOf("")}
 fun can(key:String)=key in permissions||"*" in permissions
 fun path(name:String)=dir.trimEnd('/')+"/"+name
 fun task(block:suspend ()->Unit){scope.launch{busy=true;error="";try{block()}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}}
 val upload=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)task{
  withContext(Dispatchers.IO){
   val name=ctx.contentResolver.query(uri,null,null,null,null)?.use{c->val column=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&column>=0)c.getString(column)else null}?:"upload.bin"
   require(!name.contains('/')&&!name.contains('\\'))
   val bytes=ctx.contentResolver.openInputStream(uri)!!.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=25*1024*1024){"W aplikacji można wysłać pliki do 25 MB."};out.write(buffer,0,count)};out.toByteArray()}
   val url=api.json("$p/files/upload").getJSONObject("attributes").getString("url").toHttpUrl();require(url.scheme=="https")
   val req=Request.Builder().url(url.newBuilder().addQueryParameter("directory",dir).build()).post(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("files",name,bytes.toRequestBody()).build()).build()
   api.client.newCall(req).execute().use{require(it.isSuccessful){"Wysyłanie nie powiodło się: HTTP ${it.code}."}}
  };result="Plik wysłany.";revision++
 }}
 LaunchedEffect(dir,revision,api){busy=true;error="";selected=null;try{rows=api.json("$p/files/list?directory=${enc(dir)}").objects().map{it.attrs()}.sortedWith(compareBy<JSONObject>{it.optBoolean("is_file")}.thenBy{it.optString("name")})}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}
 if(op!=null){OperationForm(op!!,api,{op=null},{result=it;op=null;selected=null;revision++});return}
 if(editor!=null){Page(editor!!.substringAfterLast('/'),"Edytor tekstu",back={editor=null}){
  if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result)
  OutlinedTextField(content,{content=it},modifier=Modifier.fillMaxWidth().heightIn(min=400.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),readOnly=!can("file.update"))
  if(can("file.update"))Button(enabled=!busy,onClick={task{api.raw("$p/files/write?file=${enc(editor!!)}","POST",content,contentType="text/plain");result="Zapisano plik."}},modifier=Modifier.fillMaxWidth()){Text("Zapisz plik")}
 };return}
 if(selected!=null){val f=selected!!;val name=f.getString("name");val full=path(name);val isFile=f.optBoolean("is_file")
  Page(name,full,back={selected=null}){
   if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   Metric("Rozmiar",bytes(f.optLong("size")));Metric("Zmodyfikowano",f.optString("modified_at"))
   if(isFile&&can("file.read-content")&&f.optLong("size")<=1048576)Button(enabled=!busy,onClick={task{content=api.raw("$p/files/contents?file=${enc(full)}");editor=full}},modifier=Modifier.fillMaxWidth()){Text("Otwórz w edytorze")}
   if(isFile&&can("file.read-content"))OutlinedButton(enabled=!busy,onClick={task{nativeDownload(ctx,api.json("$p/files/download?file=${enc(full)}").getJSONObject("attributes").getString("url"),name);result="Rozpoczęto pobieranie do folderu Pobrane."}},modifier=Modifier.fillMaxWidth()){Text("Pobierz plik")}
   if(can("file.update"))OutlinedButton(onClick={op=Operation("Zmień nazwę","$p/files/rename","PUT",fields=listOf(Field("name","Nowa nazwa lub ścieżka",value=name,required=true)),body=JSONObject().put("root",dir).put("from",name))},modifier=Modifier.fillMaxWidth()){Text("Zmień nazwę / przenieś")}
   if(can("file.archive")){OutlinedButton(onClick={op=Operation("Spakuj","$p/files/compress",body=JSONObject().put("root",dir).put("files",JSONArray().put(name)))},modifier=Modifier.fillMaxWidth()){Text("Utwórz archiwum")};if(isFile)OutlinedButton(onClick={op=Operation("Rozpakuj","$p/files/decompress",body=JSONObject().put("root",dir).put("file",name),confirmation="Archiwum zostanie rozpakowane w bieżącym katalogu.")},modifier=Modifier.fillMaxWidth()){Text("Rozpakuj archiwum")}}
   if(can("file.delete"))OutlinedButton(onClick={op=Operation("Usuń","$p/files/delete",body=JSONObject().put("root",dir).put("files",JSONArray().put(name)),confirmation="Trwale usunie $name oraz zawartość, jeśli jest katalogiem.")},modifier=Modifier.fillMaxWidth()){Text("Usuń")}
  };return
 }
 Page("Pliki",dir,back={if(dir=="/")back()else dir=dir.substringBeforeLast('/').ifBlank{"/"}},actions={IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Outlined.Refresh,"Odśwież")}}){
  if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(can("file.create"))Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={upload.launch(arrayOf("*/*"))}){Text("Wyślij plik")};OutlinedButton(onClick={op=Operation("Nowy katalog","$p/files/create-folder",fields=listOf(Field("name","Nazwa katalogu",required=true)),body=JSONObject().put("root",dir))}){Text("Nowy katalog")}}
  if(rows.isEmpty()&&!busy&&error.isBlank())EmptyState("Pusty katalog","Dodaj pliki lub utwórz katalog.")
  rows.forEach{f->val isFile=f.optBoolean("is_file");Shortcut(f.getString("name"),if(isFile)bytes(f.optLong("size"))else "Katalog",if(isFile)Icons.Outlined.Description else Icons.Outlined.Folder){if(isFile)selected=f else dir=path(f.getString("name"))};if(!isFile)TextButton(onClick={selected=f}){Text("Opcje katalogu ${f.getString("name")}")}}
 }
}
@Composable fun StartupScreen(s:ServerInfo,api:PelicanApi,back:()->Unit,editable:Boolean){
 var variables by remember{mutableStateOf<List<JSONObject>>(emptyList())};var error by remember{mutableStateOf("")};var op by remember{mutableStateOf<Operation?>(null)};var revision by remember{mutableIntStateOf(0)};var result by remember{mutableStateOf("")}
 val path=serverPath(s)+"/startup"
 LaunchedEffect(revision,api){try{variables=api.json(path).objects().map{it.attrs()}}catch(e:Exception){error=e.message.orEmpty()}}
 if(op!=null){OperationForm(op!!,api,{op=null},{op=null;result=it;revision++});return}
 Page("Zmienne startowe","Zmiany zadziałają po ponownym uruchomieniu",back){
  if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result)
  variables.forEach{v->Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(v.optString("name"));Info(v.optString("description"));val key=v.optString("env_variable");val secret=Regex("(?i)token|pass|secret|key|gsl").containsMatchIn(key);Text(if(secret)"••••••••" else v.optString("server_value"))
   if(editable&&v.optBoolean("is_editable"))TextButton(onClick={op=Operation("Zapisz zmienną",path+"/variable","PUT",listOf(Field("value",v.optString("name"),if(secret)"password" else "text",v.optString("server_value"))),JSONObject().put("key",key))}){Text("Edytuj")}
  }}}
 }
}
fun scheduleFields(row:JSONObject=JSONObject())=listOf(Field("name","Nazwa",value=row.optString("name"),required=true),Field("minute","Minuta","text",row.optJSONObject("cron")?.optString("minute")?:"*/5",required=true),Field("hour","Godzina",value=row.optJSONObject("cron")?.optString("hour")?:"*",required=true),Field("day_of_month","Dzień miesiąca",value=row.optJSONObject("cron")?.optString("day_of_month")?:"*",required=true),Field("month","Miesiąc",value=row.optJSONObject("cron")?.optString("month")?:"*",required=true),Field("day_of_week","Dzień tygodnia",value=row.optJSONObject("cron")?.optString("day_of_week")?:"*",required=true),Field("is_active","Aktywny","bool",row.optBoolean("is_active",true)),Field("only_when_online","Tylko gdy serwer działa","bool",row.optBoolean("only_when_online",true)))
@Composable fun SchedulesScreen(s:ServerInfo,api:PelicanApi,back:()->Unit,permissions:Set<String>){
 fun can(p:String)=p in permissions||"*" in permissions
 val p=serverPath(s)+"/schedules"
 ResourceList(api,ResourceSpec("Harmonogramy",p,create=if(can("schedule.create")){{Operation("Utwórz harmonogram",p,fields=scheduleFields())}}else null,detail={"Następne: "+it.optString("next_run_at")},actions={r->buildList{val path="$p/${r.getInt("id")}";if(can("schedule.update")){
  add(Operation("Edytuj harmonogram",path,fields=scheduleFields(r)));add(Operation("Wykonaj teraz","$path/execute",confirmation="Wykona wszystkie zadania harmonogramu na serwerze."));add(Operation("Dodaj zadanie","$path/tasks",fields=listOf(Field("action","Akcja","select","command",mapOf("command" to "Komenda","power" to "Zasilanie","backup" to "Kopia")),Field("payload","Komenda / start, stop, restart / pomijane pliki",required=true),Field("time_offset","Opóźnienie (sekundy)","integer",0),Field("continue_on_failure","Kontynuuj po błędzie","bool",false))))
  r.optJSONObject("relationships")?.optJSONObject("tasks")?.objects()?.forEach{t->val task=t.attrs();add(Operation("Usuń zadanie #${task.optInt("id")}: ${task.optString("action")}","$path/tasks/${task.optInt("id")}","DELETE",confirmation="Usunie zadanie z harmonogramu."))}
 };if(can("schedule.delete"))add(Operation("Usuń harmonogram",path,"DELETE",confirmation="Usunie harmonogram i jego zadania."))}}),back)
}
@Composable fun ServerSettingsScreen(s:ServerInfo,api:PelicanApi,back:()->Unit,permissions:Set<String>){
 var op by remember{mutableStateOf<Operation?>(null)};var result by remember{mutableStateOf("")};fun can(p:String)=p in permissions||"*" in permissions
 if(op!=null){OperationForm(op!!,api,{op=null},{result=it;op=null});return}
 Page("Ustawienia serwera",s.name,back){
  if(result.isNotBlank())ResultBanner(result)
  if(can("settings.rename"))Shortcut("Zmień nazwę","Nazwa wyświetlana w panelu",Icons.Outlined.Edit){op=Operation("Zapisz nazwę",serverPath(s)+"/settings/rename",fields=listOf(Field("name","Nazwa",value=s.name,required=true)))}
  if(can("settings.description"))Shortcut("Opis serwera","Notatka w panelu",Icons.Outlined.Description){op=Operation("Zapisz opis",serverPath(s)+"/settings/description",fields=listOf(Field("description","Opis","multiline")))}
  if(can("settings.reinstall"))OutlinedButton(onClick={op=Operation("Przeinstaluj serwer",serverPath(s)+"/settings/reinstall",confirmation="Uruchomi ponownie skrypt instalacji i zatrzyma serwer. Może nadpisać pliki. Wykonaj wcześniej kopię.")},modifier=Modifier.fillMaxWidth()){Text("Przeinstaluj serwer")}
 }
}
