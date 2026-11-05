<?php
namespace Kripusek\CS2MatchControl\Services;
use App\Models\Server; use App\Models\User;
use App\Repositories\Daemon\DaemonFileRepository; use App\Repositories\Daemon\DaemonServerRepository;
use App\Services\Servers\StartupModificationService;
use Illuminate\Contracts\Filesystem\FileNotFoundException; use Illuminate\Support\Facades\Cache;
use Illuminate\Validation\ValidationException;
class MatchSettingsService {
 public const MATCHZY='game/csgo/cfg/MatchZy/config.cfg';
 public const MODES=[
  'match'=>['Mecz MatchZy',0,1,1], 'practice'=>['Trening MatchZy',0,1,2],
  'competitive'=>['Turniejowy Valve',0,1,0], 'casual'=>['Uproszczony',0,0,0],
  'wingman'=>['Skrzydłowy (Wingman)',0,2,0], 'deathmatch'=>['Deathmatch',1,2,0],
  'armsrace'=>['Wyścig zbrojeń',1,0,0], 'custom'=>['Własny tryb mapy',3,0,0],
 ];
 public function __construct(private readonly DaemonFileRepository $files,private readonly StartupModificationService $startup,private readonly DaemonServerRepository $daemon) {}
 public function nativeMaps(Server $server): array {
  $out=[];
  foreach($this->files->setServer($server)->getDirectory('game/csgo/maps') as $file) {
   $name=(string)($file['name']??''); if(empty($file['is_file'] ?? $file['file'] ?? false) || !str_ends_with($name,'.vpk'))continue;
   $map=substr($name,0,-4);if(preg_match('/^[a-zA-Z0-9_-]{1,64}$/D',$map) && !str_contains($map,'_vanity'))$out[$map]=$map;
  }
  ksort($out);return $out;
 }
 public function hasMatchZy(Server $server): bool {
  try{$this->files->setServer($server)->getContent(self::MATCHZY,262144);return true;}catch(FileNotFoundException){return false;}
 }
 public function read(Server $server): array {
  // Load variables lazily: their join requires this concrete server ID.
  $s=$server->fresh();$env=[];
  foreach($s->variables as $v)$env[$v->env_variable]=(string)($v->server_value??$v->default_value);
  $auto=0;
  try{$cfg=$this->files->setServer($server)->getContent(self::MATCHZY,262144);$auto=1;if(preg_match('/^\h*matchzy_autostart_mode\h+"?(\d+)/m',$cfg,$m))$auto=(int)$m[1];}catch(FileNotFoundException){}
  $preset=null;
  foreach(self::MODES as $key=>[$label,$type,$mode,$a])if((int)($env['GAME_TYPE']??0)===$type && (int)($env['GAME_MODE']??1)===$mode && $auto===$a){$preset=$key;break;}
  $id='';if(preg_match('/^\+host_workshop_map ([1-9][0-9]{0,19})$/D',trim($env['CS2_WORKSHOP_STARTUP']??''),$m))$id=$m[1];
  return ['source'=>$id!==''?'workshop':'native','nativeMap'=>$env['SRCDS_MAP']??'de_nuke','workshopMap'=>$id,'preset'=>$preset??'competitive'];
 }
 public function validate(Server $server,array $data): array {
  if(!in_array($data['source']??'', ['native','workshop'],true))throw ValidationException::withMessages(['formData.source'=>'Wybierz Źródło mapy.']);
  if(!isset(self::MODES[$data['preset']??'']))throw ValidationException::withMessages(['formData.preset'=>'Wybierz tryb z listy.']);
  $map=(string)($data['nativeMap']??'');$maps=MapCompatibility::nativeMaps($this->nativeMaps($server),$data['preset']);
  if(!isset($maps[$map]))throw ValidationException::withMessages(['formData.nativeMap'=>$data['preset']==='wingman'?'Wybierz zainstalowaną mapę obsługującą Wingman.':'Wybierz mapę dostępną na serwerze.']);
  if($data['source']==='workshop') {
   $item=app(WorkshopService::class)->requireMap($server,(string)($data['workshopMap']??''));
   if(!MapCompatibility::workshopCompatible($item,$data['preset']))throw ValidationException::withMessages(['formData.workshopMap'=>'Wybierz mapę Workshop oznaczoną jako Wingman.']);
   $data['workshopMap']=$item['id'];
  }
  else $data['workshopMap']='';
  if(self::MODES[$data['preset']][3]>0 && !$this->hasMatchZy($server))throw ValidationException::withMessages(['formData.preset'=>'Na tym serwerze nie ma MatchZy.']);
  return ['source'=>$data['source'],'nativeMap'=>$map,'workshopMap'=>$data['workshopMap'],'preset'=>$data['preset']];
 }
 public function save(Server $server,array $input): array {
  $data=$this->validate($server,$input);
  return Cache::lock('cs2-settings-'.$server->uuid,25)->block(5,function()use($server,$data){
   // Eager-loading this relation would lose the server-specific values.
   $s=$server->fresh();$environment=[];
   foreach($s->variables as $v)$environment[$v->env_variable]=(string)($v->server_value??$v->default_value);
   if(!array_key_exists('CS2_WORKSHOP_STARTUP',$environment) || !str_contains($s->startup,'{{CS2_WORKSHOP_STARTUP}}'))throw new \RuntimeException('Brakuje integracji map startowych Workshop w konfiguracji tego serwera.');
   $original=$environment;[$label,$type,$mode,$auto]=self::MODES[$data['preset']];
   $environment['SRCDS_MAP']=$data['nativeMap'];$environment['GAME_TYPE']=(string)$type;$environment['GAME_MODE']=(string)$mode;
   $environment['CS2_WORKSHOP_STARTUP']=$data['source']==='workshop'?'+host_workshop_map '.$data['workshopMap']:'';
   $oldCfg=null;
   try{$oldCfg=$this->files->setServer($server)->getContent(self::MATCHZY,262144);}catch(FileNotFoundException){}
   try {
    $fresh=$this->startup->setUserLevel(User::USER_LEVEL_USER)->handle($s,['environment'=>$environment]);
    if($oldCfg!==null) {
     $line='matchzy_autostart_mode '.$auto;$pattern='/^(\h*matchzy_autostart_mode\h+)("?[0-2]"?)/m';
     if(preg_match($pattern,$oldCfg,$existing)) {
      $newCfg=trim($existing[2],'"')===(string)$auto?$oldCfg:preg_replace_callback($pattern,static fn($m)=>$m[1].$auto,$oldCfg);
     } else $newCfg=rtrim($oldCfg)."\n".$line."\n";
     if($this->files->setServer($server)->putContent(self::MATCHZY,$newCfg)->failed())throw new \RuntimeException('Nie udało się zapisać ustawień MatchZy.');
    }
    $this->daemon->setServer($fresh)->sync();
   }catch(\Throwable $e){
    try{$restored=$this->startup->setUserLevel(User::USER_LEVEL_USER)->handle($s->fresh(),['environment'=>$original]);if($oldCfg!==null)$this->files->setServer($server)->putContent(self::MATCHZY,$oldCfg);$this->daemon->setServer($restored)->sync();}catch(\Throwable $rollback){report($rollback);}
    throw $e;
   }
   return $data;
  });
 }
 public function commands(Server $server,array $input): array {
  $d=$this->validate($server,$input);[$label,$type,$mode,$auto]=self::MODES[$d['preset']];$commands=[];
  if($this->hasMatchZy($server))$commands=['matchzy_autostart_mode '.$auto,'css_endmatch'];
  $commands[]='game_type '.$type;$commands[]='game_mode '.$mode;
  $commands[]=$d['source']==='workshop'?'host_workshop_map '.$d['workshopMap']:'changelevel '.$d['nativeMap'];
  return $commands;
 }
}
