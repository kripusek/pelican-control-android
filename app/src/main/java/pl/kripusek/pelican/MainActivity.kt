package pl.kripusek.pelican

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.Locale

val Ink=Color(0xFF0C1220)
val Lilac=Color(0xFFB4A7FF)
val Mint=Color(0xFF72DCC0)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false};setContent{PelicanTheme{NativeApp()}}}
}
@Composable fun PelicanTheme(content: @Composable () -> Unit){
 MaterialTheme(colorScheme=darkColorScheme(primary=Lilac,secondary=Mint,background=Ink,surface=Color(0xFF161E30),surfaceContainer=Color(0xFF1C263A),onPrimary=Ink),content=content)
}
@Composable fun Hero(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF242447))){Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF2B2851),Color(0xFF1B2C40)))).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text(title,fontSize=29.sp,fontWeight=FontWeight.Bold,lineHeight=35.sp);Text(subtitle,color=Color(0xFFD0CEE5));content()
 }}
}
@Composable fun ServerCard(s:ServerInfo,click:()->Unit){
 Card(Modifier.fillMaxWidth().clickable(onClick=click)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
  Icon(Icons.Outlined.Dns,null,tint=Mint,modifier=Modifier.size(32.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){
   Text(s.name,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(s.node,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
   if(s.address.isNotBlank())Text(s.address,fontFamily=FontFamily.Monospace,fontSize=12.sp,color=Lilac)
  };Icon(Icons.Outlined.ChevronRight,"Szczegóły serwera",tint=Lilac)
 }}
}
@Composable fun Shortcut(title:String,subtitle:String,icon:ImageVector,click:()->Unit){
 Card(Modifier.fillMaxWidth().clickable(onClick=click)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Lilac,modifier=Modifier.size(28.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text(subtitle,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Outlined.ChevronRight,null)}}
}
@Composable fun ErrorCard(text:String){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF462633))){Text(text,Modifier.padding(16.dp),color=Color(0xFFFFCEDB))}}
@Composable fun Metric(name:String,value:String){Row(Modifier.fillMaxWidth()){Text(name,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.SemiBold)}}
fun bytes(n:Long)=if(n>=1073741824L)String.format(Locale.US,"%.2f GB",n/1073741824.0)else String.format(Locale.US,"%.1f MB",n/1048576.0)
fun duration(ms:Long):String{val seconds=ms/1000;return "%02d:%02d:%02d".format(seconds/3600,(seconds/60)%60,seconds%60)}
@Composable fun ConsoleScreen(server:ServerInfo,api:PelicanApi,onBack:()->Unit,canSend:Boolean=true){
 val scope=rememberCoroutineScope();val lines=remember{mutableStateListOf<String>()};var state by remember{mutableStateOf("Łączenie…")};var revision by remember{mutableIntStateOf(0)};var command by remember{mutableStateOf("")};var sending by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
 val listState=androidx.compose.foundation.lazy.rememberLazyListState()
 DisposableEffect(server.uuid,api,revision){
  val connection=api.console(server,onLine={raw->scope.launch{
   val cleaned=raw.replace(Regex("\\x1B\\[[0-?]*[ -/]*[@-~]"),"")
   cleaned.lines().forEach{lines.add(it.take(2000))};while(lines.size>400)lines.removeAt(0)
  }},onState={message->scope.launch{state=message}})
  onDispose{connection.close()}
 }
 LaunchedEffect(lines.size){if(lines.isNotEmpty())listState.animateScrollToItem(lines.lastIndex)}
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp).imePadding()){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("← Wróć")};Text(state,Modifier.weight(1f),fontSize=12.sp,maxLines=2);IconButton(onClick={revision++}){Icon(Icons.Outlined.Refresh,"Połącz ponownie")}}
  if(error.isNotBlank())ErrorCard(error)
  LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth().background(Color(0xFF090D18)),contentPadding=PaddingValues(12.dp)){
   if(lines.isEmpty())item{Text("Oczekiwanie na logi serwera…",color=Color(0xFF8E99B8),fontFamily=FontFamily.Monospace,fontSize=12.sp)}
   items(lines.size){i->SelectionContainer{Text(lines[i],fontFamily=FontFamily.Monospace,fontSize=11.sp,lineHeight=16.sp,color=Color(0xFFD0DAEB))}}
  }
  if(canSend)Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
   OutlinedTextField(command,{command=it},label={Text("Komenda serwera")},singleLine=true,modifier=Modifier.weight(1f));IconButton(enabled=command.isNotBlank()&&!sending,onClick={
    val text=command;scope.launch{sending=true;error="";try{api.command(server,text);command=""}catch(e:Exception){error=e.message?:"Nie udało się wysłać komendy."}finally{sending=false}}
   }){Icon(Icons.Outlined.Send,"Wyślij komendę",tint=Lilac)}
  }
 }
}
