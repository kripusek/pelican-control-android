<?php
namespace Kripusek\CS2MatchControl\Http;
use App\Models\ApiKey;
use App\Models\Role;
use App\Facades\Activity;
use App\Traits\EnvironmentWriterTrait;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Validation\Rule;
use Spatie\Permission\Models\Permission;

class MobileAdminController {
 use EnvironmentWriterTrait;
 // Supplementary administration endpoints accept the same ACLs as Pelican.
 private function guard(Request $r,string $resource,bool $write=false):void {
  $u=$r->user();$token=$u->currentAccessToken();
  if($u->isRootAdmin()&&$token instanceof ApiKey&&$token->key_type===ApiKey::TYPE_ACCOUNT)return;
  abort_unless($token instanceof ApiKey&&$token->key_type===ApiKey::TYPE_APPLICATION&&$token->getPermission($resource)>=($write?3:1),403);
  abort_unless($u->can(($write?'update ':'view ').$resource),403);
 }
 public function permissions(Request $r):array {
  $this->guard($r,'role');
  return ['permissions'=>$this->permissionNames()];
 }
 private function permissionNames():array {
  $names=Permission::where('guard_name',Role::DEFAULT_GUARD_NAME)->pluck('name')->all();
  foreach(Role::getPermissionList() as $model=>$prefixes)foreach($prefixes as $prefix)$names[]=$prefix.' '.$model;
  $names=array_values(array_unique($names));sort($names);return $names;
 }
 public function role(Request $r,Role $role):array {
  $this->guard($r,'role',true);abort_if($role->isRootAdmin(),403,'Nie można zmieniać roli Root Admin.');
  $d=$r->validate(['permissions'=>'present|array','permissions.*'=>['string',Rule::in($this->permissionNames())]]);
  $models=array_map(fn($name)=>Permission::findOrCreate($name,Role::DEFAULT_GUARD_NAME),$d['permissions']);
  $role->syncPermissions($models);Activity::event('role:mobile.permissions')->subject($role)->log();return ['message'=>'Zapisano uprawnienia roli.'];
 }
 private function schema():array {
  return [
   ['key'=>'APP_NAME','label'=>'Nazwa panelu','type'=>'text','value'=>config('app.name')],
   ['key'=>'APP_LOGO','label'=>'Ścieżka lub URL logo','type'=>'text','value'=>env('APP_LOGO','')],
   ['key'=>'APP_FAVICON','label'=>'Ścieżka lub URL favicon','type'=>'text','value'=>env('APP_FAVICON','/pelican.ico')],
   ['key'=>'APP_TIMEZONE','label'=>'Strefa czasowa panelu','type'=>'select','value'=>config('app.timezone'),'options'=>array_combine(\DateTimeZone::listIdentifiers(),\DateTimeZone::listIdentifiers())],
   ['key'=>'FILAMENT_UPLOADABLE_AVATARS','label'=>'Wysyłanie własnych awatarów','type'=>'bool','value'=>(bool)config('panel.filament.uploadable-avatars')],
   ['key'=>'PANEL_USE_BINARY_PREFIX','label'=>'Jednostki binarne (MiB/GiB)','type'=>'bool','value'=>(bool)config('panel.use_binary_prefix')],
  ];
 }
 public function settings(Request $r):array {$this->guard($r,'settings');return ['fields'=>$this->schema()];}
 public function saveSettings(Request $r):array {
  $this->guard($r,'settings',true);
  $d=$r->validate(['APP_NAME'=>'required|string|min:1|max:100','APP_LOGO'=>'nullable|string|max:2048','APP_FAVICON'=>'required|string|max:2048','APP_TIMEZONE'=>['required',Rule::in(\DateTimeZone::listIdentifiers())],'FILAMENT_UPLOADABLE_AVATARS'=>'required|boolean','PANEL_USE_BINARY_PREFIX'=>'required|boolean']);
  foreach(['APP_NAME','APP_LOGO','APP_FAVICON'] as $key)abort_if(preg_match('/[\x00-\x1F\x7F]/',(string)($d[$key]??'')),422);
  Cache::lock('pelican-mobile-global-settings',15)->block(5,function()use($d){$this->writeToEnvironment($d);});
  Activity::event('settings:mobile.update')->log();return ['message'=>'Zapisano ustawienia panelu.'];
 }
}
