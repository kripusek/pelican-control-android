package pl.kripusek.pelican

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext

data class Operation(val label:String,val path:String,val method:String="POST",val fields:List<Field> =emptyList(),val body:JSONObject=JSONObject(),val confirmation:String="",val admin:Boolean=false)
@Composable fun OperationForm(op:Operation,api:PelicanApi,onBack:()->Unit,onDone:(String)->Unit){
 var values by remember(op){mutableStateOf(formValues(op.fields))};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
 var fields by remember(op){mutableStateOf(op.fields)};var schemaReady by remember(op){mutableStateOf(op.fields.none{it.type=="rolePermissions"})}
 val scope=rememberCoroutineScope();val context=LocalContext.current
 LaunchedEffect(op){if(!schemaReady){try{val names=api.json("/mobile-control/permissions",admin=true).getJSONArray("permissions").strings();fields=op.fields.map{if(it.type=="rolePermissions")it.copy(type="permissions",options=names.associateWith{it})else it};schemaReady=true}catch(e:Exception){error=e.message.orEmpty()}}}
 fun submit(){scope.launch{busy=true;error="";try{
  var body=JSONObject(op.body.toString());val edited=serializeForm(fields,values);edited.keys().forEach{k->body.put(k,edited.get(k))}
  var path=op.path
  if(path.endsWith("/ID")){path=path.removeSuffix("ID")+body.getLong("resource_id");body.remove("resource_id")}
  if(path.endsWith("/files/rename")){body.put("files",JSONArray().put(JSONObject().put("from",body.getString("from")).put("to",body.getString("name"))));body.remove("from");body.remove("name")}
  if(path=="/eggs/import")body=body.getJSONObject("egg")
  if(op.method=="DOWNLOAD"){
   val download=api.json(path,admin=op.admin).attrs();nativeDownload(context,download.getString("url"),op.body.optString("filename","backup.tar.gz"));onDone("Rozpoczęto pobieranie do folderu Pobrane.")
  }else{val result=api.json(path,op.method,body,op.admin);onDone(result.optString("message").ifBlank{"Operacja zakończona."})}
 }catch(e:Exception){error=e.message?:"Operacja nie powiodła się."}finally{busy=false}}}
 if(confirm)Confirm(op.label,op.confirmation,{confirm=false},{confirm=false;submit()})
 Page(op.label,back=onBack){
  if(op.confirmation.isNotBlank())Info(op.confirmation)
  if(schemaReady)FormFields(fields,values){values=it}else if(error.isBlank())LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())ErrorCard(error);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Button(enabled=!busy&&schemaReady,onClick={if(op.confirmation.isNotBlank())confirm=true else submit()},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Wykonywanie…" else op.label)}
 }
}
data class ResourceSpec(val title:String,val path:String,val subtitle:String="",val admin:Boolean=false,val create:(()->Operation)?=null,val label:(JSONObject)->String={it.optString("name").ifBlank{it.optString("email").ifBlank{it.optString("uuid").ifBlank{it.optString("id")}}}},val detail:(JSONObject)->String={""},val actions:(JSONObject)->List<Operation> = {emptyList()},val children:(JSONObject)->List<ResourceSpec> = {emptyList()})
@Composable fun ResourceList(api:PelicanApi,spec:ResourceSpec,back:(()->Unit)?=null){
 var rows by remember(spec.path){mutableStateOf<List<JSONObject>>(emptyList())};var error by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)}
 var operation by remember(spec.path){mutableStateOf<Operation?>(null)};var selected by remember(spec.path){mutableStateOf<JSONObject?>(null)};var page by remember(spec.path){mutableIntStateOf(1)};var total by remember{mutableIntStateOf(1)}
 var child by remember(spec.path){mutableStateOf<ResourceSpec?>(null)}
 LaunchedEffect(spec.path,revision,page,api){busy=true;error="";try{val r=api.json(spec.path+(if('?' in spec.path)"&" else "?")+"page=$page&per_page=30",admin=spec.admin);rows=r.objects().map{it.attrs()};total=r.optJSONObject("meta")?.optJSONObject("pagination")?.optInt("total_pages",1)?:1}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}
 if(operation!=null){OperationForm(operation!!,api,{operation=null},{result=it;operation=null;selected=null;revision++});return}
 if(child!=null){ResourceList(api,child!!,{child=null});return}
 if(selected!=null){val row=selected!!;Page(spec.label(row),spec.title,back={selected=null}){
  Info(spec.detail(row));if(result.isNotBlank())ResultBanner(result)
  spec.actions(row).forEach{op->OutlinedButton(onClick={operation=op},modifier=Modifier.fillMaxWidth()){Text(op.label)}}
  spec.children(row).forEach{c->Shortcut(c.title,"",Icons.Outlined.Folder){child=c}}
  SectionLabel("Informacje")
  row.keys().asSequence().filter{it !in setOf("relationships","token","password","secret","daemon_token","daemon_token_id","configuration","invocation","environment")}.forEach{k->val v=row.opt(k);if(v!=JSONObject.NULL&&v !is JSONObject&&v !is JSONArray&&v.toString().length<600)Metric(human(k),v.toString())}
 };return}
 Page(spec.title,spec.subtitle,back,actions={IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Outlined.Refresh,"Odśwież")}}){
  spec.create?.let{OutlinedButton(onClick={operation=it()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(" Dodaj")}}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result)
  if(rows.isEmpty()&&!busy&&error.isBlank())EmptyState("Lista jest pusta","Nowe elementy pojawią się tutaj po utworzeniu.")
  rows.forEach{row->Shortcut(spec.label(row),spec.detail(row).take(150),Icons.Outlined.ChevronRight){selected=row}}
  if(total>1)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={page--},enabled=page>1&&!busy){Text("← Poprzednia")};Text("$page / $total");TextButton(onClick={page++},enabled=page<total&&!busy){Text("Następna →")}}
 }
}
fun human(key:String)=mapOf("id" to "ID","uuid" to "UUID","name" to "Nazwa","username" to "Login","email" to "E-mail","memory" to "Pamięć (MB)","disk" to "Dysk (MB)","cpu" to "CPU (%)","created_at" to "Utworzono","updated_at" to "Zmieniono","status" to "Stan","description" to "Opis","is_successful" to "Ukończona","bytes" to "Rozmiar (B)","fqdn" to "Adres węzła","is_active" to "Aktywny","last_run_at" to "Ostatnie wykonanie","next_run_at" to "Następne wykonanie")[key]?:key.replace('_',' ').replaceFirstChar{it.uppercase()}
val allPermissions=listOf("websocket.connect","control.console","control.start","control.stop","control.restart","file.read","file.read-content","file.create","file.update","file.delete","file.archive","file.sftp","backup.read","backup.create","backup.delete","backup.download","backup.restore","schedule.read","schedule.create","schedule.update","schedule.delete","user.read","user.create","user.update","user.delete","database.read","database.create","database.update","database.delete","database.view-password","allocation.read","allocation.create","allocation.update","allocation.delete","startup.read","startup.update","activity.read","settings.rename","settings.description")

@Composable fun ServerHub(server:ServerInfo,api:PelicanApi,onBack:()->Unit,onFavorite:()->Unit){
 var screen by remember(server){mutableStateOf("overview")};var permissions by remember(server,api){mutableStateOf<Set<String>>(emptySet())};var permissionError by remember{mutableStateOf("")}
 LaunchedEffect(server,api){try{val r=api.json(serverPath(server));permissions=r.optJSONObject("meta")?.optJSONArray("user_permissions")?.strings()?.toSet()?:emptySet()}catch(e:Exception){permissionError=e.message.orEmpty()}}
 fun can(p:String)=p in permissions||"*" in permissions
 val back={screen="overview";Unit};val p=serverPath(server)
 when(screen){
  "console"->Column{androidx.activity.compose.BackHandler(onBack=back);ConsoleScreen(server,api,onBack=back,canSend=can("control.console"))}
  "files"->FilesScreen(server,api,back,permissions)
  "startup"->StartupScreen(server,api,back,can("startup.update"))
  "cs2"->Cs2Screen(server,api,back)
  "backups"->ResourceList(api,ResourceSpec("Kopie zapasowe","$p/backups",create=if(can("backup.create")){{Operation("Utwórz kopię","$p/backups",fields=listOf(Field("name","Nazwa"),Field("ignored","Pomijane ścieżki","multiline"),Field("is_locked","Zablokuj przed usunięciem","bool",false)))}}else null,detail={bytes(it.optLong("bytes"))+" · "+if(it.optBoolean("is_successful"))"Ukończona" else "W trakcie / nieudana"},actions={r->buildList{
   val id=r.getString("uuid");if(can("backup.restore"))add(Operation("Przywróć kopię","$p/backups/$id/restore",fields=listOf(Field("truncate","Usuń obecne pliki przed przywróceniem","bool",false)),confirmation="Przywrócenie nadpisze pliki serwera. Zatrzymaj serwer i upewnij się, że wybrano właściwą kopię."))
   if(can("backup.download"))add(Operation("Pobierz kopię","$p/backups/$id/download","DOWNLOAD",body=JSONObject().put("filename",r.optString("name","backup")+".tar.gz")))
   if(can("backup.delete")){add(Operation("Zmień blokadę kopii","$p/backups/$id/lock"));add(Operation("Usuń kopię","$p/backups/$id","DELETE",confirmation="Trwale usunie wybraną kopię zapasową."))}
   if(can("backup.create"))add(Operation("Zmień nazwę","$p/backups/$id/rename","PUT",listOf(Field("name","Nazwa",value=r.optString("name"),required=true))))
  }}),back)
  "schedules"->SchedulesScreen(server,api,back,permissions)
  "network"->ResourceList(api,ResourceSpec("Sieć i porty","$p/network/allocations",create=if(can("allocation.create")){{Operation("Przydziel dodatkowy port","$p/network/allocations")}}else null,label={it.optString("ip_alias").ifBlank{it.optString("ip")}+":"+it.optInt("port")},detail={if(it.optBoolean("is_default"))"Port główny" else it.optString("notes")},actions={r->buildList{val id=r.optInt("id");if(can("allocation.update")){add(Operation("Notatka portu","$p/network/allocations/$id",fields=listOf(Field("notes","Notatka",value=r.optString("notes")))));add(Operation("Ustaw jako główny","$p/network/allocations/$id/primary",confirmation="Zmieni główny adres serwera."))};if(can("allocation.delete"))add(Operation("Zwolnij port","$p/network/allocations/$id","DELETE",confirmation="Usunie dodatkowy przydział portu."))}}),back)
  "users"->ResourceList(api,ResourceSpec("Dostęp do serwera","$p/users",create=if(can("user.create")){{Operation("Dodaj użytkownika","$p/users",fields=listOf(Field("email","E-mail",required=true),Field("permissions","Uprawnienia","permissions",JSONArray(),allPermissions.associateWith{it})))}}else null,label={it.optString("email")},detail={it.optString("username")},actions={r->buildList{val id=r.getString("uuid");if(can("user.update"))add(Operation("Zmień uprawnienia","$p/users/$id",fields=listOf(Field("permissions","Uprawnienia","permissions",r.optJSONArray("permissions")?:JSONArray(),allPermissions.associateWith{it}))));if(can("user.delete"))add(Operation("Odbierz dostęp","$p/users/$id","DELETE",confirmation="Użytkownik utraci dostęp do tego serwera."))}}),back)
  "databases"->ResourceList(api,ResourceSpec("Bazy danych","$p/databases",create=if(can("database.create")){{Operation("Utwórz bazę","$p/databases",fields=listOf(Field("database","Nazwa bazy",required=true),Field("remote","Dozwolone połączenia z",value="%",required=true)))}}else null,label={it.optString("name")},detail={it.optString("username")},actions={r->buildList{val id=r.getString("id");if(can("database.update"))add(Operation("Wygeneruj nowe hasło","$p/databases/$id/rotate-password",confirmation="Obecne połączenia z bazą przestaną działać do czasu zmiany hasła."));if(can("database.delete"))add(Operation("Usuń bazę","$p/databases/$id","DELETE",confirmation="Trwale usunie bazę i wszystkie jej dane."))}}),back)
  "activity"->ResourceList(api,ResourceSpec("Aktywność","$p/activity",label={it.optString("event")},detail={it.optString("created_at")}),back)
  "settings"->ServerSettingsScreen(server,api,back,permissions)
  else->ServerOverview(server,api,onBack,onFavorite,permissions,permissionError,onScreen={screen=it})
 }
}

@Composable fun ServerOverview(s:ServerInfo,api:PelicanApi,back:()->Unit,favorite:()->Unit,permissions:Set<String>,permissionError:String,onScreen:(String)->Unit){
 var res by remember{mutableStateOf<Resources?>(null)};var error by remember{mutableStateOf("")};var action by remember{mutableStateOf<Operation?>(null)};val scope=rememberCoroutineScope()
 fun can(p:String)=p in permissions||"*" in permissions
 LaunchedEffect(s,api){while(kotlinx.coroutines.currentCoroutineContext().isActive){runCatching{api.resources(s)}.onSuccess{res=it;error=""}.onFailure{error=it.message.orEmpty()};kotlinx.coroutines.delay(5000)}}
 if(action!=null){OperationForm(action!!,api,{action=null},{action=null});return}
 Page(s.name,s.node,back){
  Hero(when(res?.state){"running"->"Serwer działa";"offline"->"Serwer wyłączony";"starting"->"Uruchamianie…";"stopping"->"Zatrzymywanie…";else->"Sprawdzanie…"},s.address){res?.let{r->Metric("CPU",String.format(java.util.Locale.US,"%.1f %%",r.cpu));Metric("RAM",bytes(r.memory));Metric("Dysk",bytes(r.disk));Metric("Uptime",duration(r.uptime))}}
  if(error.isNotBlank())ErrorCard(error);if(permissionError.isNotBlank())ErrorCard(permissionError)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("start" to "Start","restart" to "Restart","stop" to "Stop").forEach{(signal,label)->OutlinedButton(enabled=can("control.$signal"),onClick={action=Operation(label,serverPath(s)+"/power",body=JSONObject().put("signal",signal),confirmation=if(signal=="start")"" else "Przerwie połączenia graczy i aktywne zadania serwera.")},modifier=Modifier.weight(1f)){Text(label)}}}
  SectionLabel("Zarządzanie")
  listOf(Triple("console","Konsola na żywo","websocket.connect"),Triple("files","Pliki i edytor","file.read"),Triple("backups","Kopie zapasowe","backup.read"),Triple("schedules","Harmonogramy","schedule.read"),Triple("network","Sieć i porty","allocation.read"),Triple("startup","Zmienne startowe","startup.read"),Triple("users","Dostęp użytkowników","user.read"),Triple("databases","Bazy danych","database.read"),Triple("activity","Historia aktywności","activity.read"),Triple("settings","Ustawienia serwera","settings.rename")).filter{can(it.third)}.forEach{(key,label,_)->Shortcut(label,"",when(key){"console"->Icons.Outlined.Terminal;"files"->Icons.Outlined.Folder;"backups"->Icons.Outlined.Backup;else->Icons.Outlined.Settings}){onScreen(key)}}
  if(can("control.console"))Shortcut("Panel CS2","MatchZy, Workshop i ochrona",Icons.Outlined.SportsEsports){onScreen("cs2")}
  OutlinedButton(onClick=favorite,modifier=Modifier.fillMaxWidth()){Text("Przypisz jako serwer CS2")}
 }
}
