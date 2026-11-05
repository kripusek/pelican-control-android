package pl.kripusek.pelican
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class ApiJsonTest {
 @Test fun rejectsCredentialAndPathUrls(){
  listOf("http://example.org","https://user:pass@example.org","https://example.org/login","https://example.org?token=abc").forEach{assertTrue(runCatching{normalizePanel(it)}.isFailure)}
  assertEquals("https://panel.example.com",normalizePanel("https://panel.example.com/"))
 }
 @Test fun parsesServerWithoutRetainingInvocation(){
  val servers=ApiJson.servers(JSONObject("""{"data":[{"attributes":{"uuid":"11111111-2222-4333-8444-555555555555","identifier":"11111111","name":"CS2","node":"Home","invocation":"private-startup-token","limits":{"memory":2048,"cpu":100},"relationships":{"allocations":{"data":[{"attributes":{"ip":"203.0.113.10","alias":"cs2.example.com","port":27015,"is_default":true}}]}}}}]}"""))
  assertEquals("cs2.example.com:27015",servers.single().address)
  assertFalse(servers.toString().contains("private-startup-token"))
 }
 @Test fun readsResources(){
  val r=ApiJson.resources(JSONObject("""{"attributes":{"current_state":"running","resources":{"cpu_absolute":2.4,"memory_bytes":1000,"disk_bytes":2000,"uptime":3000}}}"""))
  assertEquals("running",r.state);assertEquals(1000L,r.memory);assertEquals(2.4,r.cpu,0.001)
 }
}
