package pl.kripusek.pelican

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.objects(key:String="data"):List<JSONObject> = optJSONArray(key)?.let{a->(0 until a.length()).map{a.getJSONObject(it)}}?:emptyList()
fun JSONObject.attrs():JSONObject=optJSONObject("attributes")?:this
fun JSONArray.strings():List<String> = (0 until length()).map{getString(it)}
fun enc(s:String)=java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20")
fun serverPath(s:ServerInfo)="/servers/${s.uuid}"

@Composable fun Page(title:String,subtitle:String="",back:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit){
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
   if(back!=null){IconButton(onClick=back){Icon(Icons.Outlined.ArrowBack,"Wróć")};BackHandler(onBack=back)}
   Column(Modifier.weight(1f)){Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,fontSize=12.sp,color=Mint)}
   actions()
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){content();Spacer(Modifier.height(20.dp))}
 }
}
@Composable fun Info(text:String){Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}
@Composable fun SectionLabel(text:String){Text(text.uppercase(),fontSize=12.sp,color=Mint,letterSpacing=1.sp,fontWeight=FontWeight.SemiBold)}
@Composable fun ResultBanner(message:String){Card(colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFF183A35))){Text(message,Modifier.padding(16.dp))}}
@Composable fun EmptyState(title:String,detail:String){Card{Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.Bold);Info(detail)}}}
@Composable fun Confirm(title:String,body:String,onDismiss:()->Unit,onConfirm:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=onConfirm){Text("Potwierdź")}},dismissButton={TextButton(onClick=onDismiss){Text("Anuluj")}})}

data class Field(val key:String,val label:String,val type:String="text",val value:Any?="",val options:Map<String,String> =emptyMap(),val required:Boolean=false)
fun jsonFields(a:JSONArray):List<Field> = (0 until a.length()).map{a.getJSONObject(it).let{f->val opts=f.optJSONObject("options");Field(f.getString("key"),f.getString("label"),f.getString("type"),f.opt("value"),opts?.keys()?.asSequence()?.associateWith{opts.getString(it)}?:emptyMap())}}
fun formValues(fields:List<Field>)=JSONObject().apply{fields.forEach{put(it.key,it.value?:JSONObject.NULL)}}
@Composable fun FormFields(fields:List<Field>,values:JSONObject,onChange:(JSONObject)->Unit){
 fields.forEach{f->
  val value=values.opt(f.key).takeUnless{it==JSONObject.NULL}?.toString().orEmpty()
  fun update(v:Any){onChange(JSONObject(values.toString()).put(f.key,v))}
  when(f.type){
   "permissions"->{SectionLabel(f.label);val selected=values.optJSONArray(f.key)?.strings()?.toSet()?:emptySet();f.options.forEach{(key,label)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(key in selected,{checked->update(JSONArray(if(checked)selected+key else selected-key))});Text(label)}}}
   "bool"->Card{Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(f.label,Modifier.weight(1f));Switch(checked=values.optBoolean(f.key),onCheckedChange={update(it)})}}
   "select"->Picker(f.label,value,f.options){update(it)}
   else->OutlinedTextField(value,{update(it)},label={Text(f.label+(if(f.required)" *" else ""))},modifier=Modifier.fillMaxWidth(),singleLine=f.type!in listOf("multiline","json"),minLines=if(f.type in listOf("multiline","json"))3 else 1,visualTransformation=if(f.type=="password")PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=when(f.type){"number","integer"->androidx.compose.ui.text.input.KeyboardType.Decimal;"password"->androidx.compose.ui.text.input.KeyboardType.Password;else->androidx.compose.ui.text.input.KeyboardType.Text}))
  }
 }
}
fun serializeForm(fields:List<Field>,values:JSONObject):JSONObject=JSONObject().apply{
 fields.forEach{f->val raw=values.opt(f.key);val text=raw?.toString().orEmpty();if(f.required)require(text.isNotBlank()){ "Uzupełnij: ${f.label}" }
  if(raw!=null&&raw!=JSONObject.NULL){val value=when(f.type){"bool"->values.optBoolean(f.key);"integer"->text.toLongOrNull()?:throw IllegalArgumentException("${f.label}: wpisz liczbę całkowitą.");"number"->text.toDoubleOrNull()?.takeIf{it.isFinite()}?:throw IllegalArgumentException("${f.label}: wpisz liczbę.");"permissions"->raw;"json"->if(text.trim().startsWith("["))JSONArray(text)else JSONObject(text);else->text};var parent=this;val parts=f.key.split('.');parts.dropLast(1).forEach{k->val child=parent.optJSONObject(k)?:JSONObject().also{parent.put(k,it)};parent=child};parent.put(parts.last(),value)}
 }
}
@Composable fun Picker(label:String,value:String,options:Map<String,String>,onChange:(String)->Unit){
 var expanded by remember{mutableStateOf(false)}
 Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().testTag("picker:$label")){Column(Modifier.weight(1f)){Text(label,fontSize=12.sp,color=Mint);Text(options[value]?:value.ifBlank{"Wybierz"})};Icon(Icons.Outlined.ExpandMore,null)}
  DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{(k,v)->DropdownMenuItem(text={Text(v)},onClick={expanded=false;onChange(k)})}}
 }
}
@Composable fun NativeApp(){
 val ctx=LocalContext.current;val store=remember{CredentialStore(ctx)}
 var config by remember{mutableStateOf(runCatching{store.load()}.getOrDefault(PanelConfig()))}
 val api=remember(config){PelicanApi(config)}
 var admin by rememberSaveable{mutableStateOf(false)};var tab by rememberSaveable{mutableIntStateOf(0)}
 var selected by remember{mutableStateOf<ServerInfo?>(null)}
 var servers by remember{mutableStateOf<List<ServerInfo>>(emptyList())};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 fun refresh(){val requested=config;scope.launch{loading=true;error="";try{val fetched=api.servers();if(config==requested)servers=fetched}catch(e:Exception){if(e is CancellationException)throw e;if(config==requested)error=e.message.orEmpty()}finally{if(config==requested)loading=false}}}
 LaunchedEffect(config){servers=emptyList();selected=null;if(config.token.isNotBlank())refresh()}
 val labels=if(admin)listOf("Pulpit","Serwery","Węzły","Użytkownicy","Więcej")else listOf("Pulpit","Serwery","CS2","Konto")
 val icons=if(admin)listOf(Icons.Outlined.Dashboard,Icons.Outlined.Dns,Icons.Outlined.Hub,Icons.Outlined.People,Icons.Outlined.Apps)else listOf(Icons.Outlined.Dashboard,Icons.Outlined.Dns,Icons.Outlined.SportsEsports,Icons.Outlined.AccountCircle)
 Scaffold(containerColor=Ink,bottomBar={
  NavigationBar {
   labels.forEachIndexed { i,label ->
    NavigationBarItem(selected=tab==i,onClick={tab=i;selected=null},icon={Icon(icons[i],label)},label={Text(label,fontSize=11.sp)})
   }
  }
 }){padding->
  Column(Modifier.fillMaxSize().padding(padding)){
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("PELICAN",Modifier.weight(1f),color=Mint,fontWeight=FontWeight.Bold,letterSpacing=3.sp);FilterChip(selected=!admin,onClick={admin=false;tab=0;selected=null},label={Text("Użytkownik")});Spacer(Modifier.width(6.dp));FilterChip(selected=admin,onClick={admin=true;tab=0;selected=null},label={Text("Admin")})}
   if(selected!=null){ServerHub(selected!!,api,onBack={selected=null},onFavorite={val c=config.copy(cs2Id=selected!!.shortId);store.save(c);config=c;selected=null;tab=2;admin=false});return@Column}
   if(admin){when(tab){
    0->AdminOverview(api,onSection={tab=it},onAccount={admin=false;tab=3})
    1->AdminResourceScreen(api,AdminResources.servers)
    2->AdminResourceScreen(api,AdminResources.nodes)
    3->AdminResourceScreen(api,AdminResources.users)
    else->AdminMore(api,onAccount={admin=false;tab=3})
   }}else when(tab){
    0->Page("Centrum dowodzenia","Twój Pelican · natywna aplikacja",actions={IconButton(onClick={refresh()}){Icon(Icons.Outlined.Refresh,"Odśwież")}}){
     Hero("Wszystko pod kontrolą.","Serwery, mecze i administracja w jednym panelu."){Text("${servers.size} serwery na koncie",color=Mint,fontWeight=FontWeight.SemiBold)}
     if(config.token.isBlank()){EmptyState("Połącz swoje konto","Dodaj klucz API klienta Pelicana. Ekrany działają bez przeglądarki.");Button(onClick={tab=3},modifier=Modifier.fillMaxWidth()){Text("Połącz z Pelicanem")}}
     if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
     if(error.isNotBlank())ErrorCard(error)
     Shortcut("Twoje serwery","Konsola, pliki, kopie i harmonogramy",Icons.Outlined.Dns){tab=1}
     Shortcut("Counter-Strike 2","Tryby gry, mapy Workshop i ochrona",Icons.Outlined.SportsEsports){tab=2}
     SectionLabel("Szybki dostęp")
     servers.take(4).forEach{s->ServerCard(s){selected=s}}
    }
    1->Page("Twoje serwery","Dostęp zgodny z uprawnieniami konta",actions={IconButton(onClick={refresh()},enabled=!loading){Icon(Icons.Outlined.Refresh,"Odśwież")}}){
     if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())ErrorCard(error)
     if(servers.isEmpty()&&!loading)EmptyState("Brak serwerów",if(config.token.isBlank())"Połącz konto w zakładce Konto." else "Odśwież listę lub sprawdź uprawnienia konta.")
     servers.forEach{s->ServerCard(s){selected=s}}
    }
    2->{val cs=servers.firstOrNull{it.shortId==config.cs2Id};if(cs==null)Page("Counter-Strike 2"){EmptyState("Wybierz serwer CS2","Połącz konto i przypisz serwer CS2 w jego szczegółach.");Button(onClick={tab=1}){Text("Lista serwerów")}}else Cs2Screen(cs,api)}
    else->AccountScreen(config,api,onSave={c->store.save(c);config=c;error=""},onDisconnect={val c=PanelConfig(url=config.url,cs2Id=config.cs2Id);store.save(c);config=c})
   }
  }
 }
}

@Composable fun AccountScreen(config:PanelConfig,api:PelicanApi,onSave:(PanelConfig)->Unit,onDisconnect:()->Unit){
 var url by remember(config){mutableStateOf(config.url)};var token by remember(config){mutableStateOf(config.token)};var adminToken by remember(config){mutableStateOf(config.adminToken)}
 var account by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var success by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 LaunchedEffect(api){account=null;if(config.token.isNotBlank())runCatching{api.json("/account").attrs()}.onSuccess{account=it}.onFailure{error=it.message.orEmpty()}}
 Page("Konto i połączenie","Pelican Control 2.1.0"){
  account?.let{Hero(it.optString("username"),it.optString("email")){Text(if(it.optBoolean("root_admin"))"Administrator Pelicana" else "Konto użytkownika",color=Mint)}}
  OutlinedTextField(url,{url=it},label={Text("Adres panelu HTTPS")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(token,{token=it},label={Text("Klucz API klienta")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  Info("W Pelicanie: Konto → Klucze API. Klucz klienta zaczyna się od pacc_. Jest przechowywany zaszyfrowany na telefonie.")
  SectionLabel("Administracja")
  OutlinedTextField(adminToken,{adminToken=it},label={Text("Klucz administracyjny · opcjonalny")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  Info("Konto głównego administratora może używać klucza klienta również w trybie Admin. Dla pozostałych administratorów dodaj klucz aplikacyjny z odpowiednimi uprawnieniami. Sam przełącznik Admin nie nadaje uprawnień.")
  if(error.isNotBlank())ErrorCard(error);if(success.isNotBlank())ResultBanner(success);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Button(enabled=!busy,onClick={scope.launch{busy=true;error="";success="";try{val c=PanelConfig(normalizePanel(url),token.trim(),config.cs2Id,adminToken.trim());require(c.token.isNotBlank()||c.adminToken.isNotBlank()){"Dodaj klucz klienta lub klucz administracyjny."};val check=PelicanApi(c);if(c.token.isNotBlank())check.json("/account");if(c.adminToken.isNotBlank())check.json("/servers?per_page=1",admin=true);onSave(c);success=if(c.token.isBlank())"Połączono z API administratora. Przełącz na Admin. Do panelu użytkownika dodaj klucz pacc_." else "Połączono z kontem Pelicana."}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("Sprawdź i zapisz połączenie")}
  if(config.token.isNotBlank())OutlinedButton(onClick=onDisconnect,modifier=Modifier.fillMaxWidth()){Text("Wyloguj z aplikacji")}
  HorizontalDivider();Info("Wszystkie ekrany są natywne. Zmiany sprawdza API Pelicana. Dane konta, konsola i pliki pozostają na Twoim serwerze.")
 }
}
