<?php
namespace Kripusek\CS2MatchControl\Http;

use App\Enums\SubuserPermission as P;
use App\Enums\ContainerStatus;
use App\Models\Server;
use App\Facades\Activity;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Kripusek\CS2MatchControl\Services\MatchSettingsService as M;
use Kripusek\CS2MatchControl\Services\WorkshopService as W;
use Kripusek\CS2ACControl\Services\CS2ACConfigService as A;
use Kripusek\CS2FOWControl\Services\CS2FOWConfigService as F;
use Kripusek\MatchZyControl\Services\MatchZyConfigService as Z;

class MobileCs2Controller {
 private const ZKEYS=['knife'=>'matchzy_knife_enabled_default','whitelist'=>'matchzy_whitelist_enabled_default','readyRequired'=>'matchzy_minimum_ready_required','demoRecording'=>'matchzy_demo_recording_enabled','playout'=>'matchzy_playout_enabled_default','techPause'=>'matchzy_enable_tech_pause','tacticalPause'=>'matchzy_use_pause_command_for_tactical_pause','damageReport'=>'matchzy_enable_damage_report','stopCommand'=>'matchzy_stop_command_available','autostartMode'=>'matchzy_autostart_mode'];
 private function guard(Request $r,Server $s,array $permissions):void {
  $hint=strtolower($s->egg->name.' '.$s->image.' '.$s->startup);
  abort_unless(str_contains($hint,'cs2')||str_contains($hint,'counter-strike 2')||str_contains($hint,'counter strike 2'),404);
  foreach($permissions as $p)abort_unless($r->user()->can($p,$s),403);
 }
 private function field(string $key,string $label,string $type,mixed $value,array $extra=[]):array{return compact('key','label','type','value')+$extra;}
 private function fields(Server $s,string $module):array {
  $f=[];
  if($module==='ac') {
   $c=app(A::class)->read($s);
   foreach(['enabled'=>'Ochrona CS2AC','chat_announcements'=>'Powiadomienia na czacie','center_announcements'=>'Powiadomienia na ekranie'] as $k=>$v)$f[]=$this->field($k,$v,'bool',($c['cs2ac_'.$k]??'1')==='1');
   $f[]=$this->field('language','Język','select',$c['cs2ac_language']??'pl',['options'=>['pl'=>'Polski','en'=>'English','de'=>'Deutsch','uk'=>'Ukrainian','ru'=>'Russian']]);
   $p=$c['cs2ac_punishment_command']??'';$k=$c['cs2ac_kick_command']??'';$native='kickid {userid} CS2AC: {detection}';
   $f[]=$this->field('response','Reakcja na wykrycie','select',$p===''&&$k===''?'report':($p===$native&&$k===$native?'kick':'custom'),['options'=>['report'=>'Raport','kick'=>'Wyrzuć gracza','custom'=>'Zachowaj własne komendy']]);
   $f[]=$this->field('whitelist','Whitelist SteamID64','multiline',$c['cs2ac_whitelist']??'');
   foreach(A::MODULES as $key=>$label)$f[]=$this->field($key,strtoupper(str_replace('_',' ',$key)),'bool',($c['cs2ac_'.$key.'_enabled']??'1')==='1');
  }elseif($module==='fow') {
   $c=app(F::class)->read($s);
   foreach(['enable'=>'Ochrona CS2FOW','smoke_occlusion'=>'Ukrywanie za dymem','filter_teammates'=>'Filtrowanie członków drużyny'] as $k=>$label)$f[]=$this->field($k,$label,'bool',($c['cs2fow_'.$k]??($k==='filter_teammates'?'0':'1'))==='1');
   foreach(F::NUMBERS as $key=>[$min,$max,$default,$label])$f[]=$this->field($key,str_replace('_',' ',$key),'number',0+($c['cs2fow_'.$key]??$default),compact('min','max'));
  }elseif($module==='matchzy') {
   $service=app(Z::class);$c=$service->readConvars($s,array_values(self::ZKEYS));$state=$service->readState($s);
   foreach(['team1'=>'Drużyna 1','team2'=>'Drużyna 2'] as $k=>$label)$f[]=$this->field($k,$label,'text',$state[$k]??$label);
   foreach(self::ZKEYS as $key=>$cv) {
    $raw=$c[$cv]??($key==='readyRequired'?'10':($key==='autostartMode'?'0':'false'));
    $labels=['knife'=>'Runda nożowa','whitelist'=>'Whitelist','readyRequired'=>'Liczba gotowych graczy','demoRecording'=>'Automatyczne dema','playout'=>'Wszystkie rundy','techPause'=>'Pauza techniczna','tacticalPause'=>'Pauza taktyczna','damageReport'=>'Raport obrażeń','stopCommand'=>'Komenda .stop','autostartMode'=>'Tryb po starcie'];
    $f[]=$this->field($key,$labels[$key],in_array($key,['readyRequired','autostartMode'])?'number':'bool',in_array($key,['readyRequired','autostartMode'])?(int)$raw:in_array(strtolower($raw),['1','true','yes']),['min'=>0,'max'=>$key==='autostartMode'?2:32]);
   }
  } else abort(404);
  return $f;
 }
 public function show(Request $r,Server $server):array {
  $this->guard($r,$server,[]);$p=[];foreach(P::cases() as $v)if($r->user()->can($v,$server))$p[]=$v->value;
  $out=['permissions'=>$p,'modules'=>[]];
  if($r->user()->can(P::FileReadContent,$server)) {
   foreach(['ac','fow','matchzy'] as $name)try{$out['modules'][$name]=$this->fields($server,$name);}catch(\Throwable $e){$out['moduleErrors'][$name]='Konfiguracja niedostępna.';}
  }
  if($r->user()->can(P::StartupRead,$server))$out['settings']=app(M::class)->read($server);
  if($r->user()->can(P::FileRead,$server)) {
   $out['maps']=array_values(app(M::class)->nativeMaps($server));
   if($r->user()->can(P::FileReadContent,$server))$out['workshop']=array_values(app(W::class)->library($server));
   $out['installed']=app(W::class)->installedIds($server);
  }
  $out['presets']=array_map(fn($v)=>$v[0],M::MODES);
  return $out;
 }
 public function settings(Request $r,Server $server):array {
  $this->guard($r,$server,[P::StartupRead,P::StartupUpdate,P::FileRead,P::FileReadContent,P::FileUpdate,P::ControlConsole]);
  $v=$r->validate(['source'=>'required|in:native,workshop','nativeMap'=>'required|string|max:64','workshopMap'=>'nullable|string|max:20','preset'=>'required|string|max:32','apply'=>'sometimes|boolean']);
  if($r->boolean('apply')){$cmd=app(M::class)->commands($server,$v);$this->send($server,$cmd);$message='Wysłano zmianę trybu i mapy. Sprawdź konsolę.';}
  else{app(M::class)->save($server,$v);$message='Zapisano ustawienia startowe. Zadziałają przy następnym uruchomieniu.';}
  return $this->done($r,$server,'settings',$message);
 }
 public function workshop(Request $r,Server $server):array {
  $this->guard($r,$server,[P::FileRead,P::FileReadContent,P::FileCreate,P::FileUpdate]);
  $v=$r->validate(['operation'=>'required|in:add,remove,load','input'=>'required|string|max:2048']);$w=app(W::class);
  if($v['operation']==='add')$w->add($server,$v['input']);
  elseif($v['operation']==='remove')$w->remove($server,$v['input']);
  else{$this->guard($r,$server,[P::ControlConsole]);$this->send($server,$w->loadCommands($server,$v['input']));}
  return $this->done($r,$server,'workshop:'.$v['operation'],$v['operation']==='load'?'Wysłano pobieranie i zmianę mapy. Postęp w konsoli.':'Zaktualizowano bibliotekę Workshop.');
 }
 public function module(Request $r,Server $server,string $module):array {
  $this->guard($r,$server,[P::FileReadContent,P::FileUpdate,P::ControlConsole]);
  abort_unless(in_array($module,['ac','fow','matchzy']),404);
  $data=$r->validate(['values'=>'required|array'])['values'];
  $fields=$this->fields($server,$module);$values=[];$rules=[];
  foreach($fields as $f){$k=$f['key'];$values[$k]=$data[$k]??$f['value'];$rules[$k]=match($f['type']){'bool'=>['required','boolean'],'number'=>['required','numeric','min:'.$f['min'],'max:'.$f['max']],'select'=>['required',\Illuminate\Validation\Rule::in(array_keys($f['options']))],default=>['nullable','string','max:8192']};}
  $values=validator($values,$rules)->validate();
  Cache::lock('cs2-mobile-'.$server->uuid.'-'.$module,20)->block(5,function()use($server,$module,$values){
   if($module==='ac')app(A::class)->save($server,$values);
   elseif($module==='fow')app(F::class)->save($server,$values);
   else {
    foreach(['team1','team2'] as $k)abort_unless(preg_match('/^[\p{L}\p{N} _.-]{1,32}$/uD',$values[$k]??''),422,'Nazwa drużyny: 1–32 litery, cyfry, spacje, kropki i myślniki.');
    foreach(['readyRequired','autostartMode'] as $k)abort_unless((float)$values[$k]===(float)(int)$values[$k],422);
    $cv=[];foreach(self::ZKEYS as $k=>$key)$cv[$key]=$values[$k];
    $z=app(Z::class);$z->writeConvars($server,$cv);$state=$z->readState($server);$z->writeState($server,array_merge($state,array_intersect_key($values,array_flip(['team1','team2']))));
   }
  });
  $message='Zapisano konfigurację.';
  if($server->retrieveStatus()===ContainerStatus::Running) {
   if($module==='matchzy'){$commands=['css_team1 '.$values['team1'],'css_team2 '.$values['team2']];foreach(self::ZKEYS as $k=>$cv)$commands[]=$cv.' '.(is_bool($values[$k])?($values[$k]?'true':'false'):$values[$k]);}
   else $commands=[$module==='ac'?'cs2ac_reload':'cs2fow_reload'];
   $this->send($server,$commands);$message.=' Wysłano zastosowanie na działającym serwerze.';
  } else $message.=' Zadziała po uruchomieniu serwera.';
  return $this->done($r,$server,'module:'.$module,$message);
 }
 public function action(Request $r,Server $server):array {
  $this->guard($r,$server,[P::ControlConsole]);
  $v=$r->validate(['action'=>'required|in:ac-status,ac-check,fow-status,fow-check,match-start,match-end,match-pause,match-unpause,practice,status']);
  $commands=['ac-status'=>'cs2ac_status','ac-check'=>'cs2ac_check_config','fow-status'=>'cs2fow_status','fow-check'=>'cs2fow_check_config','match-start'=>'css_forcestart','match-end'=>'css_endmatch','match-pause'=>'css_pause','match-unpause'=>'css_unpause','practice'=>'css_prac','status'=>'status'];
  $this->send($server,[$commands[$v['action']]]);return $this->done($r,$server,$v['action'],'Wysłano komendę. Odpowiedź jest w konsoli.');
 }
 private function send(Server $s,array $commands):void {
  abort_unless($s->retrieveStatus()===ContainerStatus::Running,409,'Serwer musi być uruchomiony.');
  $response=$s->send($commands);abort_if($response->getStatusCode()>=300,502,'Wings nie przyjął komendy.');
 }
 private function done(Request $r,Server $s,string $action,string $message):array {
  Activity::event('server:mobile.'.$action)->subject($s)->log();return ['message'=>$message];
 }
}
