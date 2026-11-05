package pl.kripusek.pelican

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MatchMapRulesTest {
 private fun catalog()=JSONObject().put("maps",JSONArray(listOf("de_dust2","de_inferno","de_mirage","de_nuke","de_debris"))).put("mapsByPreset",JSONObject().put("wingman",JSONArray(listOf("de_nuke","de_inferno","de_debris","de_missing"))))

 @Test fun wingmanShowsOnlyInstalledSupportedMaps(){
  assertEquals(listOf("de_inferno","de_nuke","de_debris"),MatchMapRules.nativeMaps(catalog(),"wingman"))
 }
 @Test fun changingFromCompetitiveDropsIncompatibleSelection(){
  val input=JSONObject().put("preset","wingman").put("source","native").put("nativeMap","de_dust2")
  val result=MatchMapRules.normalize(catalog(),input)
  assertEquals("de_inferno",result.getString("nativeMap"));assertEquals("de_dust2",input.getString("nativeMap"))
 }
 @Test fun switchingBackRestoresFullMapList(){
  assertEquals(5,MatchMapRules.nativeMaps(catalog(),"competitive").size)
  assertEquals(5,MatchMapRules.nativeMaps(catalog(),"casual").size)
 }
 @Test fun olderBackendStillFiltersWingman(){
  val data=catalog().apply{remove("mapsByPreset")}
  assertFalse("de_mirage" in MatchMapRules.nativeMaps(data,"wingman"))
  assertTrue("de_nuke" in MatchMapRules.nativeMaps(data,"wingman"))
 }
 @Test fun workshopRequiresExplicitWingmanTag(){
  val data=catalog().put("workshop",JSONArray().put(JSONObject().put("id","1").put("tags",JSONArray().put("Wingman"))).put(JSONObject().put("id","2").put("tags",JSONArray().put("Competitive"))).put(JSONObject().put("id","3")))
  assertEquals(listOf("1"),MatchMapRules.workshopMaps(data,"wingman").map{it.getString("id")})
  assertEquals(3,MatchMapRules.workshopMaps(data,"competitive").size)
  val selected=JSONObject().put("preset","wingman").put("nativeMap","de_nuke").put("workshopMap","2")
  assertEquals("1",MatchMapRules.normalize(data,selected).getString("workshopMap"))
 }
}
