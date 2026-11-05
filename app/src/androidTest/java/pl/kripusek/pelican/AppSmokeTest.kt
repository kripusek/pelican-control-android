package pl.kripusek.pelican
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
class AppSmokeTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Test fun navigationAndCs2Tabs(){
  ui.onNodeWithText("Centrum dowodzenia").assertExists()
  ui.onNodeWithText("CS2",useUnmergedTree=true).performClick()
  ui.onNodeWithText("Wybierz serwer CS2").assertExists()
  ui.onNodeWithText("Admin",useUnmergedTree=true).performClick()
  ui.onNodeWithText("Administracja").assertExists()
  ui.onNodeWithText("Więcej",useUnmergedTree=true).performClick()
  ui.onNodeWithText("Dodatki Pelicana").assertExists()
  ui.onNodeWithText("Użytkownik",useUnmergedTree=true).performClick()
  ui.onNodeWithText("Konto",useUnmergedTree=true).performClick()
  ui.onNodeWithText("Sprawdź i zapisz połączenie").assertExists()
 }
 @Test fun keystoreRoundtrip(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val store=CredentialStore(c);val old=store.load()
  try{val data=PanelConfig(token="SYNTHETIC_TEST_TOKEN");store.save(data);assertEquals(data,store.load());val raw=c.getSharedPreferences("connection",0).getString("data","");assertFalse(raw.orEmpty().contains("SYNTHETIC_TEST_TOKEN"))}finally{store.save(old)}
 }
}
