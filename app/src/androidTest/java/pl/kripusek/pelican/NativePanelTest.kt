package pl.kripusek.pelican

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import java.io.File
import android.graphics.Bitmap

class NativePanelTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun waitText(text:String){ui.waitUntil(30000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun back(){ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
 private fun screenshot(name:String){val i=InstrumentationRegistry.getInstrumentation();val out=File(i.targetContext.filesDir,name);i.uiAutomation.takeScreenshot().let{b->out.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun realNativePanels(){
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext;val fixture=File(ctx.filesDir,"qa.json");Assume.assumeTrue(fixture.exists())
  val j=JSONObject(fixture.readText());val store=CredentialStore(ctx);val old=store.load();val config=PanelConfig(j.getString("url"),j.getString("token"));val api=PelicanApi(config)
  val server=runBlocking{api.servers().single{it.uuid==j.getString("server_uuid")}}
  try{
   store.save(config.copy(cs2Id=server.shortId));ui.activityRule.scenario.recreate()
   waitText(server.name);screenshot("native-dashboard.png")
   ui.onNodeWithText("Serwery",useUnmergedTree=true).performClick();waitText(server.name);ui.onNodeWithText(server.name).performClick()
   waitText("Pliki i edytor");ui.onNodeWithText("Pliki i edytor").performScrollTo().performClick();waitText("Nowy katalog");screenshot("native-files.png")
   back();back();ui.onNodeWithText("CS2",useUnmergedTree=true).performClick();waitText("Ustawienia meczu");ui.onNodeWithText("Ustawienia meczu").performClick();waitText("Zapisz na następne uruchomienie");screenshot("native-match.png")
   back();ui.onNodeWithText("CS2AC").performScrollTo().performClick();waitText("Ochrona CS2AC");screenshot("native-ac.png")
   ui.onNodeWithText("Admin",useUnmergedTree=true).performClick();waitText("Administracja");ui.waitUntil(30000){ui.onAllNodesWithText("Skonfiguruj dostęp administratora").fetchSemanticsNodes().isEmpty()};screenshot("native-admin.png")
   ui.onNodeWithText("Węzły",useUnmergedTree=true).performClick();waitText("Węzły Wings");ui.onNodeWithText("Użytkownicy",useUnmergedTree=true).performClick();waitText("Użytkownicy Pelicana")
   ui.onNodeWithText("Więcej",useUnmergedTree=true).performClick();ui.onNodeWithText("Dodatki Pelicana").performClick();waitText(" Dodaj");ui.onNodeWithText(" Dodaj").performClick();ui.onAllNodesWithText("Importuj dodatek z URL").onFirst().assertExists()
  }catch(e:Throwable){screenshot("native-error.png");File(ctx.filesDir,"native-error.txt").writeText(ui.onRoot(useUnmergedTree=true).printToString());throw e}finally{store.save(old);fixture.delete();ui.activityRule.scenario.recreate()}
 }
}
