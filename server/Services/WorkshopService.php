<?php
namespace Kripusek\CS2MatchControl\Services;
use App\Models\Server; use App\Repositories\Daemon\DaemonFileRepository;
use Illuminate\Support\Facades\Cache; use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException; use Illuminate\Contracts\Filesystem\FileNotFoundException;
class WorkshopService {
 public const PATH='game/csgo/cfg/pelican-workshop-library.json';
 public function __construct(private readonly DaemonFileRepository $files) {}
 public function id(string $input): string {
  $input=trim($input);
  if(preg_match('/^[1-9][0-9]{0,19}$/D',$input)) return $input;
  $url=parse_url($input);
  if(is_array($url) && ($url['scheme']??'')==='https' && in_array(strtolower($url['host']??''),['steamcommunity.com','www.steamcommunity.com'],true)
   && !isset($url['user']) && !isset($url['pass']) && !isset($url['port']) && in_array(rtrim($url['path']??'','/'),['/sharedfiles/filedetails','/workshop/filedetails'],true)) {
   parse_str($url['query']??'',$q); if(isset($q['id']) && is_string($q['id']) && preg_match('/^[1-9][0-9]{0,19}$/D',$q['id'])) return $q['id'];
  }
  throw ValidationException::withMessages(['formData.workshopInput'=>'Wklej ID mapy lub link https://steamcommunity.com/sharedfiles/filedetails/?id=…']);
 }
 public function library(Server $server): array {
  try { $raw=$this->files->setServer($server)->getContent(self::PATH,262144); }
  catch(FileNotFoundException) { return []; }
  $data=json_decode($raw,true,32,JSON_THROW_ON_ERROR);
  if(!is_array($data)) throw new \RuntimeException('Nieprawidłowy plik biblioteki Workshop.');
  $result=[];
  foreach($data as $item) if(is_array($item) && isset($item['id'],$item['title']) && preg_match('/^[1-9][0-9]{0,19}$/D',(string)$item['id'])) $result[(string)$item['id']]=$item;
  return $result;
 }
 private function write(Server $server,array $items): void {
  $r=$this->files->setServer($server)->putContent(self::PATH,json_encode(array_values($items),JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR)."\n");
  if($r->failed()) throw new \RuntimeException('Nie udało się zapisać biblioteki przez Wings.');
 }
 public function details(string $id): array {
  $id=$this->id($id);
  $data=Http::asForm()->connectTimeout(5)->timeout(15)->post('https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/',[
   'itemcount'=>1,'publishedfileids[0]'=>$id])->throw()->json('response.publishedfiledetails.0');
  if(!is_array($data) || (int)($data['result']??0)!==1 || (string)($data['publishedfileid']??'')!==$id || (int)($data['consumer_app_id']??0)!==730 || (int)($data['file_type']??0)===2 || (int)($data['file_size']??0)<=0 || !empty($data['banned']))
   throw ValidationException::withMessages(['formData.workshopInput'=>'Steam nie potwierdził publicznej mapy CS2. Kolekcje, prywatne i usunięte materiały nie są obsługiwane.']);
  $tags=[];foreach($data['tags']??[] as $tag)if(is_array($tag)&&is_string($tag['tag']??null))$tags[]=mb_substr($tag['tag'],0,100);
  return ['id'=>$id,'title'=>mb_substr((string)($data['title']??$id),0,200),'size'=>(int)$data['file_size'],'tags'=>array_values(array_unique($tags)),'added_at'=>now()->toIso8601String()];
 }
 public function add(Server $server,string $input): void {
  $item=$this->details($this->id($input));
  Cache::lock('cs2-workshop-'.$server->uuid,15)->block(5,function()use($server,$item){
   $items=$this->library($server); if(count($items)>=100 && !isset($items[$item['id']])) throw new \RuntimeException('Biblioteka mieści maksymalnie 100 map.');
   $items[$item['id']]=$item; $this->write($server,$items);
  });
 }
 public function remove(Server $server,string $id): void {
  $id=$this->id($id); Cache::lock('cs2-workshop-'.$server->uuid,15)->block(5,function()use($server,$id){$items=$this->library($server);unset($items[$id]);$this->write($server,$items);});
 }
 public function requireMap(Server $server,string $id): array {
  $id=$this->id($id); $items=$this->library($server);
  if(!isset($items[$id])) throw new \RuntimeException('Najpierw dodaj tę mapę do biblioteki Workshop.');
  return $items[$id];
 }
 public function loadCommands(Server $server,string $id): array {
  $item=$this->requireMap($server,$id); $commands=[];
  if(app(MatchSettingsService::class)->hasMatchZy($server)) $commands[]='css_endmatch';
  $commands[]='host_workshop_map '.$item['id']; return $commands;
 }
 public function installedIds(Server $server): array {
  $found=[];
  foreach(['game/bin/linuxsteamrt64/steamapps/workshop/content/730','steamapps/workshop/content/730','game/csgo/maps/workshop'] as $path) {
   try { $entries=$this->files->setServer($server)->getDirectory($path); }
   catch(FileNotFoundException) { continue; }
   catch(\Illuminate\Http\Client\RequestException $e) {if($e->response->status()!==404)throw $e;continue;}
   foreach($entries as $entry) {
    $id=(string)($entry['name']??''); if(!preg_match('/^[1-9][0-9]{0,19}$/D',$id) || !empty($entry['is_file'] ?? $entry['file'] ?? false)) continue;
    foreach($this->files->setServer($server)->getDirectory($path.'/'.$id) as $file) if(!empty($file['is_file'] ?? $file['file'] ?? false) && str_ends_with(strtolower($file['name']??''),'.vpk') && (int)($file['size']??0)>0) $found[$id]=true;
   }
  }
  return array_keys($found);
 }
}
