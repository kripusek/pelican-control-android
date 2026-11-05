package pl.kripusek.pelican
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class NativeFormsTest {
 @Test fun nestedLimitsRemainSeparateFromServerDetails(){
  val f=listOf(Field("name","Nazwa",value="CS2"),Field("limits.memory","RAM","integer",4096),Field("feature_limits.backups","Kopie","integer",3))
  val body=serializeForm(f,formValues(f))
  assertEquals(4096,body.getJSONObject("limits").getInt("memory"));assertEquals(3,body.getJSONObject("feature_limits").getInt("backups"));assertEquals("CS2",body.getString("name"));assertFalse(body.has("limits.memory"))
 }
 @Test fun invalidResourceLimitsFailBeforeSendingRequest(){
  val f=listOf(Field("limits.memory","RAM","number"))
  listOf("NaN","Infinity","one").forEach{assertTrue(runCatching{serializeForm(f,JSONObject().put("limits.memory",it))}.isFailure)}
 }
}
