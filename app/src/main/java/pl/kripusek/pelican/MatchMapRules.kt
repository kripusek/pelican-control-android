package pl.kripusek.pelican

import org.json.JSONObject

object MatchMapRules {
 private val legacyWingmanMaps=setOf("de_debris","de_eldorado","de_poseidon","de_overpass","de_vertigo","de_nuke","de_inferno")

 fun nativeMaps(data:JSONObject,preset:String):List<String> {
  val installed=data.optJSONArray("maps")?.strings()?:emptyList()
  if(preset!="wingman")return installed
  val supported=data.optJSONObject("mapsByPreset")?.optJSONArray("wingman")?.strings()?.toSet()?:legacyWingmanMaps
  return installed.filter{it in supported}
 }

 fun workshopMaps(data:JSONObject,preset:String):List<JSONObject> = data.objects("workshop").filter{item->
  preset!="wingman"||item.optJSONArray("tags")?.strings()?.any{it.trim().lowercase() in setOf("wingman","2v2")}==true
 }

 fun normalize(data:JSONObject,input:JSONObject):JSONObject {
  val out=JSONObject(input.toString());val preset=out.optString("preset","competitive")
  val native=nativeMaps(data,preset);val workshop=workshopMaps(data,preset).map{it.getString("id")}
  if(out.optString("nativeMap") !in native)out.put("nativeMap",native.firstOrNull().orEmpty())
  if(out.optString("workshopMap") !in workshop)out.put("workshopMap",workshop.firstOrNull().orEmpty())
  return out
 }
}
