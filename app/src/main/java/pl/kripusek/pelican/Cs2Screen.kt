package pl.kripusek.pelican

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable fun Cs2Screen(s:ServerInfo,api:PelicanApi,back:(()->Unit)?=null){
 val scope=rememberCoroutineScope();val path=serverPath(s)+"/mobile-cs2"
 var data by remember(s,api){mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)};var section by remember{mutableStateOf("home")};var op by remember{mutableStateOf<Operation?>(null)}
 val permissions=data?.optJSONArray("permissions")?.strings()?.toSet()?:emptySet();fun can(p:String)=p in permissions||"*" in permissions
 val canSave=can("control.console")&&can("file.read-content")&&can("file.update")
 LaunchedEffect(s,api,revision){busy=true;error="";try{data=api.json(path)}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}
 if(op!=null){OperationForm(op!!,api,{op=null},{result=it;op=null;revision++});return}
 if(section=="console"){androidx.activity.compose.BackHandler{section="home"};ConsoleScreen(s,api,{section="home"},canSend=can("control.console"));return}
 if(section in listOf("ac","fow","matchzy")){
  val name=when(section){"ac"->"CS2AC";"fow"->"CS2FOW";else->"MatchZy"};val fields=data?.optJSONObject("modules")?.optJSONArray(section)?.let{jsonFields(it)}?:emptyList();var values by remember(section,data){mutableStateOf(formValues(fields))}
  Page(name,"Konfiguracja na Twoim serwerze",back={section="home"}){
   if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result)
   if(fields.isEmpty())EmptyState("Konfiguracja niedostępna","Sprawdź instalację dodatku i uprawnienia do plików.")
   else{
    FormFields(fields,values){values=it}
    if(canSave)Button(enabled=!busy,onClick={scope.launch{busy=true;error="";try{val v=serializeForm(fields,values);result=api.json("$path/module/$section","POST",JSONObject().put("values",v)).getString("message");revision++}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("Zapisz i zastosuj")}
   }
   if(can("control.console")){
    SectionLabel("Sterowanie")
    val actions=if(section=="matchzy")listOf("match-start" to "Wymuś start meczu","match-pause" to "Pauza","match-unpause" to "Wznów","match-end" to "Zakończ mecz","practice" to "Włącz trening")else listOf("$section-status" to "Pobierz status","$section-check" to "Sprawdź konfigurację")
    actions.forEach{(key,label)->OutlinedButton(onClick={op=Operation(label,"$path/action",body=JSONObject().put("action",key),confirmation=if(key.startsWith("match")||key=="practice")"Zmieni bieżący stan meczu na serwerze." else "")},modifier=Modifier.fillMaxWidth()){Text(label)}}
    TextButton(onClick={section="console"}){Text("Otwórz konsolę z wynikami")}
   }
  };return
 }
 if(section=="settings"){
  val catalog=data?:JSONObject()
  var values by remember(data){mutableStateOf(MatchMapRules.normalize(catalog,catalog.optJSONObject("settings")?:JSONObject().put("source","native").put("preset","competitive")))}
  val preset=values.optString("preset","competitive")
  val native=MatchMapRules.nativeMaps(catalog,preset);val workshop=MatchMapRules.workshopMaps(catalog,preset)
  val settingsFields=listOf(
   Field("preset","Tryb gry","select",options=catalog.optJSONObject("presets")?.let{p->p.keys().asSequence().associateWith{p.getString(it)}}?:emptyMap()),
   Field("source","Źródło mapy","select",options=mapOf("native" to "Mapa Valve","workshop" to "Steam Workshop")),
   Field("nativeMap","Mapa Valve","select",options=native.associateWith{it}),
   Field("workshopMap","Mapa Workshop","select",options=workshop.associate{it.getString("id") to it.getString("title")})
  )
  val selectionValid=values.optString("nativeMap") in native && (values.optString("source")=="native"||workshop.any{it.getString("id")==values.optString("workshopMap")})
  Page("Ustawienia meczu","Mapa, tryb i ustawienia po uruchomieniu",back={section="home"}){
   if(result.isNotBlank())ResultBanner(result);if(error.isNotBlank())ErrorCard(error)
   FormFields(settingsFields.filter{it.key!="workshopMap"||values.optString("source")=="workshop"},values){values=MatchMapRules.normalize(catalog,it)}
   if(preset=="wingman")Info("Dostępne są tylko mapy Wingman. Mapy Workshop muszą mieć oznaczenie Wingman w Steam.")
   if(!selectionValid&&!busy)Info("Brak zgodnej mapy dla wybranego źródła. Wybierz inne źródło lub dodaj mapę do biblioteki.")
   Info("Zapis dotyczy kolejnego uruchomienia. Zastosowanie teraz przełączy mapę i zakończy bieżący mecz.")
   if(canSave&&can("startup.update")){
    Button(enabled=selectionValid&&!busy,onClick={op=Operation("Zapisz ustawienia startowe","$path/settings",body=JSONObject(values.toString()).put("apply",false))},modifier=Modifier.fillMaxWidth()){Text("Zapisz na następne uruchomienie")}
    OutlinedButton(enabled=selectionValid&&!busy,onClick={op=Operation("Zastosuj teraz","$path/settings",body=JSONObject(values.toString()).put("apply",true),confirmation="Zakończy bieżący mecz, zmieni tryb i przełączy mapę. Gracze zostaną przeniesieni na wybraną mapę.")},modifier=Modifier.fillMaxWidth()){Text("Zastosuj teraz i zmień mapę")}
   }
  };return
 }
 if(section=="workshop"){
  val installed=data?.optJSONArray("installed")?.strings()?.toSet()?:emptySet();val rows=data?.objects("workshop")?:emptyList()
  Page("Mapy Workshop","Biblioteka map CS2",back={section="home"}){
   if(result.isNotBlank())ResultBanner(result);if(error.isNotBlank())ErrorCard(error)
   if(canSave&&can("file.create"))Button(onClick={op=Operation("Dodaj mapę do biblioteki","$path/workshop",fields=listOf(Field("input","ID mapy lub link Steam",required=true)),body=JSONObject().put("operation","add"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(" Dodaj z Workshop")}
   Info("Pobieranie mapy w CS2 odbywa się podczas jej uruchamiania. Przycisk pobierania zmieni również bieżącą mapę.")
   if(rows.isEmpty())EmptyState("Biblioteka jest pusta","Dodaj publiczną mapę CS2 po jej ID lub linku Steam Workshop.")
   rows.forEach{r->Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(r.getString("title"));Info("${r.getString("id")} · ${bytes(r.optLong("size"))}");Text(if(r.getString("id") in installed)"Pobrana na serwerze" else "W bibliotece · do pobrania",color=Mint)
    if(canSave){OutlinedButton(onClick={op=Operation("Pobierz i uruchom mapę","$path/workshop",body=JSONObject().put("operation","load").put("input",r.getString("id")),confirmation="Zakończy mecz i przełączy serwer na tę mapę. Pobieranie może potrwać kilka minut.")},modifier=Modifier.fillMaxWidth()){Text("Pobierz i uruchom")}
     TextButton(onClick={op=Operation("Usuń z biblioteki","$path/workshop",body=JSONObject().put("operation","remove").put("input",r.getString("id")),confirmation="Usunie wpis z biblioteki. Pobrane pliki pozostaną na serwerze.")}){Text("Usuń z biblioteki")}}
   }}}
  };return
 }
 Page("Counter-Strike 2",s.name,back,actions={IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Outlined.Refresh,"Odśwież CS2")}}){
  Hero("Twój serwer. Twój mecz.","${data?.optJSONObject("settings")?.optString("nativeMap")?:s.address}"){Text("MatchZy · Workshop · CS2AC · CS2FOW",color=Mint)}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())ErrorCard(error);if(result.isNotBlank())ResultBanner(result)
  listOf(Triple("settings","Ustawienia meczu","Mapa, tryb i konfiguracja startowa"),Triple("workshop","Steam Workshop","Biblioteka i pobieranie custom map"),Triple("matchzy","MatchZy","Drużyny, gotowość, pauzy i start"),Triple("ac","CS2AC","Detektory i reakcja na wykrycie"),Triple("fow","CS2FOW","Widoczność, dym i ochrona")).forEach{(key,label,detail)->Shortcut(label,detail,when(key){"settings"->Icons.Outlined.Tune;"workshop"->Icons.Outlined.CloudDownload;"matchzy"->Icons.Outlined.Groups;else->Icons.Outlined.Shield}){section=key}}
  if(can("websocket.connect"))Shortcut("Konsola CS2","Status i odpowiedzi dodatków",Icons.Outlined.Terminal){section="console"}
 }
}
