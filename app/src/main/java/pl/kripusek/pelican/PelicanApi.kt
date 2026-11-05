package pl.kripusek.pelican

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ServerInfo(val uuid:String,val shortId:String,val name:String,val node:String,val address:String,val memoryLimit:Long,val cpuLimit:Int)
data class Resources(val state:String,val cpu:Double,val memory:Long,val disk:Long,val uptime:Long)
object ApiJson {
 fun servers(json:JSONObject):List<ServerInfo> {
  val data=json.getJSONArray("data")
  return (0 until data.length()).map { index->
   val a=data.getJSONObject(index).getJSONObject("attributes")
   val allocations=a.optJSONObject("relationships")?.optJSONObject("allocations")?.optJSONArray("data")
   var address=""
   if(allocations!=null) for(i in 0 until allocations.length()) {
    val p=allocations.getJSONObject(i).getJSONObject("attributes")
    if(address.isEmpty()||p.optBoolean("is_default")) address=(p.optString("alias").takeUnless{it.isBlank()||it=="null"}?:p.optString("ip"))+":"+p.optInt("port")
   }
   val limits=a.optJSONObject("limits")
   ServerInfo(a.getString("uuid"),a.getString("identifier"),a.getString("name"),a.optString("node"),address,limits?.optLong("memory")?:0,limits?.optInt("cpu")?:0)
  }
 }
 fun resources(json:JSONObject):Resources {
  val a=json.getJSONObject("attributes");val r=a.getJSONObject("resources")
  return Resources(a.getString("current_state"),r.optDouble("cpu_absolute"),r.optLong("memory_bytes"),r.optLong("disk_bytes"),r.optLong("uptime"))
 }
}
fun normalizePanel(input:String):String {
 val url=input.trim().trimEnd('/').toHttpUrl()
 require(url.scheme=="https"&&url.username.isEmpty()&&url.password.isEmpty()&&url.encodedPath=="/"&&url.query==null&&url.fragment==null){"Podaj adres HTTPS panelu, bez ścieżki i danych logowania."}
 return url.toString().trimEnd('/')
}
class PelicanApi(private val config:PanelConfig) {
 private val base=normalizePanel(config.url)
 internal val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
 private suspend fun request(path:String,body:JSONObject?=null):JSONObject=json(path,if(body==null)"GET" else "POST",body)
 suspend fun json(path:String,method:String="GET",body:JSONObject?=null,admin:Boolean=false):JSONObject {
  val text=raw(path,method,body?.toString(),admin)
  if(text.isBlank())return JSONObject()
  return try{JSONObject(text)}catch(_:Exception){throw IOException("Panel nie zwrócił danych API.")}
 }
 suspend fun raw(path:String,method:String="GET",body:String?=null,admin:Boolean=false,contentType:String="application/json"):String=withContext(Dispatchers.IO) {
  val token=if(admin)config.adminToken.ifBlank{config.token}else config.token
  require(token.isNotBlank()){"Połącz konto kluczem API w zakładce Konto."}
  require(admin||!token.startsWith("papp_")){"To klucz API administracyjnego. Wklej go w pole Klucz administracyjny. W polu Klucz API klienta użyj klucza pacc_ utworzonego w Profil → API Keys."}
  require(path.startsWith("/")||path.startsWith("?")){"Nieprawidłowa ścieżka API."}
  val builder=Request.Builder().url("$base/api/${if(admin)"application" else "client"}$path").header("Authorization","Bearer $token").header("Accept","application/json")
  builder.method(method,if(method in listOf("GET","HEAD"))null else (body?:"").toRequestBody(contentType.toMediaType()))
  client.newCall(builder.build()).execute().use { response->
   if(!response.isSuccessful) {
    val detail=runCatching{val errors=JSONObject(response.body?.string().orEmpty()).optJSONArray("errors");(0 until (errors?.length()?:0)).take(5).joinToString("\n"){errors!!.getJSONObject(it).optString("detail").take(300)}}.getOrDefault("")
    throw IOException(when(response.code){401->"Klucz API jest nieprawidłowy lub wygasł.";403->"Konto lub klucz API nie ma uprawnień do tej operacji.";429->"Za dużo zapytań. Spróbuj za chwilę.";else->detail.ifBlank{"Błąd HTTP ${response.code}."}})
   }
   if(response.code==204)return@withContext ""
   val text=response.body?.string().orEmpty()
   text
  }
 }
 suspend fun servers():List<ServerInfo> {
  val result=mutableListOf<ServerInfo>();var page=1
  do {
   val response=request("/?page=$page&per_page=100");result+=ApiJson.servers(response)
   val total=response.optJSONObject("meta")?.optJSONObject("pagination")?.optInt("total_pages",1)?:1
   if(page>=total)break
   page++;require(page<=20){"Lista jest zbyt długa. Skorzystaj z pełnego panelu."}
  }while(true)
  return result
 }
 private fun id(s:ServerInfo):String {require(s.uuid.matches(Regex("[a-fA-F0-9-]{36}")));return s.uuid}
 suspend fun resources(s:ServerInfo)=ApiJson.resources(request("/servers/${id(s)}/resources"))
 suspend fun power(s:ServerInfo,signal:String) {require(signal in listOf("start","stop","restart"));request("/servers/${id(s)}/power",JSONObject().put("signal",signal))}
 suspend fun command(s:ServerInfo,command:String) {require(command.isNotBlank()&&command.length<=1024&&!command.contains('\n')&&!command.contains('\r')){"Wpisz pojedynczą komendę, maksymalnie 1024 znaki."};request("/servers/${id(s)}/command",JSONObject().put("command",command))}
 private suspend fun socketInfo(s:ServerInfo)=request("/servers/${id(s)}/websocket").getJSONObject("data")
 fun console(s:ServerInfo,onLine:(String)->Unit,onState:(String)->Unit):ConsoleConnection=ConsoleConnection(s,onLine,onState)
 inner class ConsoleConnection(private val server:ServerInfo,private val line:(String)->Unit,private val state:(String)->Unit) {
  private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  @Volatile private var socket:WebSocket?=null
  @Volatile private var closed=false
  private var generation=0
  private var failures=0
  init{connect()}
  private fun connect(delayMs:Long=0){
   val run=++generation
   scope.launch {
    delay(delayMs);if(closed)return@launch
    state("Łączenie…")
    try {
     val info=socketInfo(server);val url=info.getString("socket")
     require(url.startsWith("wss://")){"Węzeł nie udostępnia bezpiecznego WSS. Użyj pełnego panelu."}
     val endpoint=url.replaceFirst("wss://","https://").toHttpUrl();require(endpoint.username.isEmpty()&&endpoint.password.isEmpty())
     val jwt=info.getString("token")
     val origin=base.toHttpUrl().let{it.scheme+"://"+it.host+(if(it.port==443)"" else ":${it.port}")}
     socket=client.newWebSocket(Request.Builder().url(url).header("Origin",origin).build(),object:WebSocketListener(){
      override fun onOpen(ws:WebSocket,response:Response){if(closed||run!=generation){ws.close(1000,null);return};ws.send(JSONObject().put("event","auth").put("args",JSONArray().put(jwt)).toString())}
      override fun onMessage(ws:WebSocket,text:String){
       if(closed||run!=generation)return
       try{
        val message=JSONObject(text);val event=message.optString("event");val args=message.optJSONArray("args")
        when(event){
         "auth success"->{failures=0;state("Połączono");ws.send("{\"event\":\"send logs\",\"args\":[]}")}
         "console output","install output","daemon message"->{if(args!=null)for(i in 0 until args.length())line(args.optString(i))}
         "status"->{state("Połączono · "+(args?.optString(0)?:""))}
         "token expiring","token expired"->{generation++;ws.close(1000,"Refresh");connect()}
         "auth error","jwt error"->{state("Uwierzytelnianie konsoli nie powiodło się.");ws.close(1000,null)}
        }
       }catch(_:Exception){state("Nieprawidłowa odpowiedź konsoli.")}
      }
      override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){if(!closed&&run==generation)retry()}
      override fun onClosed(ws:WebSocket,code:Int,reason:String){if(!closed&&run==generation)retry()}
     })
    }catch(e:Exception){if(!closed&&run==generation)state(e.message?:"Nie udało się połączyć z konsolą.")}
   }
  }
  private fun retry(){if(failures++<3)connect((failures*2000L))else state("Konsola rozłączona. Wybierz Połącz ponownie.")}
  fun close(){closed=true;generation++;socket?.close(1000,"Screen closed");scope.cancel()}
 }
}
