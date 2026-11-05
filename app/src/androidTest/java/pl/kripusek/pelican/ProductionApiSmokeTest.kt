package pl.kripusek.pelican
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class ProductionApiSmokeTest {
 @Test fun realReadOnlyApiAndConsole()=runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val fixture=File(c.filesDir,"qa.json");assumeTrue("QA fixture supplied only for temporary emulator testing",fixture.exists())
  val j=JSONObject(fixture.readText());val api=PelicanApi(PanelConfig(j.getString("url"),j.getString("token")))
  val servers=api.servers();assertTrue(servers.isNotEmpty())
  val s=servers.single{it.uuid==j.getString("server_uuid")};assertTrue(api.resources(s).state.isNotBlank())
  val p=serverPath(s)
  val mobile=api.json("$p/mobile-cs2");assertTrue(mobile.getJSONArray("maps").length()>0)
  listOf("ac","fow","matchzy").forEach{assertTrue(mobile.getJSONObject("modules").getJSONArray(it).length()>0)}
  val snapshot=mobile.toString();assertFalse(snapshot.contains("webhook"));assertFalse(snapshot.contains("punishment_command"))
  listOf("servers","users","nodes","roles","eggs","plugins","mounts","database-hosts").forEach{resource->assertTrue(api.json("/$resource?per_page=1",admin=true).has("data"))}
  listOf("files/list?directory=%2F","startup","backups","schedules","network/allocations","users","databases","activity").forEach{resource->assertTrue(api.json("$p/$resource").has("data"))}
  // Exercise real writes only in newly created QA resources, never a running match.
  val label="native_qa_"+System.currentTimeMillis()
  val qaFile="/$label.txt"
  try {
   api.raw("$p/files/write?file=${enc(qaFile)}","POST","native file editor check",contentType="text/plain")
   assertEquals("native file editor check",api.raw("$p/files/contents?file=${enc(qaFile)}"))
  }finally{api.json("$p/files/delete","POST",JSONObject().put("root","/").put("files",org.json.JSONArray().put(label+".txt")))}
  val role=api.json("/roles","POST",JSONObject().put("name",label),true).getJSONObject("attributes").getInt("id")
  try{api.json("/roles/$role","PATCH",JSONObject().put("name",label+"_edit"),true);assertEquals(label+"_edit",api.json("/roles/$role",admin=true).getJSONObject("attributes").getString("name"))}finally{api.json("/roles/$role","DELETE",admin=true)}
  val scheduleBody=JSONObject().put("name",label).put("minute","0").put("hour","0").put("day_of_month","*").put("month","*").put("day_of_week","*").put("is_active",false).put("only_when_online",true)
  val schedule=api.json("$p/schedules","POST",scheduleBody).getJSONObject("attributes").getInt("id")
  try{val edited=JSONObject(scheduleBody.toString()).put("name",label+"_edit");api.json("$p/schedules/$schedule","POST",edited);assertFalse(api.json("$p/schedules/$schedule").getJSONObject("attributes").getBoolean("is_active"))}finally{api.json("$p/schedules/$schedule","DELETE")}
  val auth=CountDownLatch(1);val logs=CountDownLatch(1)
  val ws=api.console(s,{if(it.isNotBlank())logs.countDown()},{if(it.startsWith("Połączono"))auth.countDown()})
  try{assertTrue("WSS authentication",auth.await(30,TimeUnit.SECONDS));assertTrue("Console log reception",logs.await(15,TimeUnit.SECONDS));File(c.filesDir,"qa-result.json").writeText("""{"realApi":true,"websocketAuthenticated":true,"consoleLogsReceived":true,"nativeCs2":true,"adminLists":8,"clientLists":8,"fileRoundtrip":true,"roleCrud":true,"inactiveScheduleCrud":true}""")}
  finally{ws.close();fixture.delete()}
 }
}
