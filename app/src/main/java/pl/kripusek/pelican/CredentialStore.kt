package pl.kripusek.pelican

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PanelConfig(val url:String="https://panel.example.com",val token:String="",val cs2Id:String="",val adminToken:String="")
class CredentialStore(context:Context) {
 private val prefs=context.getSharedPreferences("connection",Context.MODE_PRIVATE)
 private fun key():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (ks.getKey("pelican.credentials",null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
   init(KeyGenParameterSpec.Builder("pelican.credentials",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun save(c:PanelConfig) {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
  val plain=JSONObject().put("url",c.url).put("token",c.token).put("cs2",c.cs2Id).put("adminToken",c.adminToken).toString().toByteArray(Charsets.UTF_8)
  val encrypted=cipher.doFinal(plain)
  check(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
 }
 fun load():PanelConfig {
  val data=prefs.getString("data",null)?:return PanelConfig()
  val iv=prefs.getString("iv",null)?:error("Nie można odczytać zapisanych danych. Zapisz połączenie ponownie.")
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))}
  val json=JSONObject(String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8))
  return PanelConfig(json.getString("url"),json.optString("token"),json.optString("cs2",""),json.optString("adminToken"))
 }
}
